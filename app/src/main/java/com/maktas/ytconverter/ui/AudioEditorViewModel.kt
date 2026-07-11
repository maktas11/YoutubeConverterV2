package com.maktas.ytconverter.ui

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.maktas.ytconverter.music.Song
import com.maktas.ytconverter.music.WaveformExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** A time range (ms, in the original track) to remove. */
data class CutRegion(val startMs: Long, val endMs: Long)

sealed interface EditorExportState {
    data object Idle : EditorExportState
    data object Exporting : EditorExportState
    data object Done : EditorExportState
    data class Error(val message: String) : EditorExportState
}

class AudioEditorViewModel(app: Application) : AndroidViewModel(app) {

    var song: Song? by mutableStateOf(null)
        private set

    var waveform: FloatArray by mutableStateOf(FloatArray(0))
        private set
    var isLoadingWaveform by mutableStateOf(false)
        private set

    var cutRegions: List<CutRegion> by mutableStateOf(emptyList())
        private set

    var speed by mutableStateOf(1f)
        private set
    var pitch by mutableStateOf(1f)
        private set

    var exportState: EditorExportState by mutableStateOf(EditorExportState.Idle)
        private set

    val hasChanges: Boolean
        get() = cutRegions.isNotEmpty() || speed != 1f || pitch != 1f

    fun open(target: Song) {
        song = target
        cutRegions = emptyList()
        speed = 1f
        pitch = 1f
        exportState = EditorExportState.Idle
        waveform = FloatArray(0)
        isLoadingWaveform = true
        viewModelScope.launch {
            waveform = WaveformExtractor.extract(getApplication(), Uri.parse(target.uri))
            isLoadingWaveform = false
        }
    }

    fun addCutRegion(region: CutRegion) {
        cutRegions = (cutRegions + region).sortedBy { it.startMs }
    }

    fun updateCutRegion(index: Int, region: CutRegion) {
        cutRegions = cutRegions.toMutableList().also { it[index] = region }.sortedBy { it.startMs }
    }

    fun removeCutRegion(index: Int) {
        cutRegions = cutRegions.toMutableList().also { it.removeAt(index) }
    }

    fun updateSpeed(value: Float) {
        speed = value
    }

    fun updatePitch(value: Float) {
        pitch = value
    }

    fun export(title: String) {
        val target = song ?: return
        exportState = EditorExportState.Exporting
        viewModelScope.launch {
            runCatching { runExport(target) }
                .fold(
                    onSuccess = { outputFile ->
                        val finalized = runCatching {
                            withContext(Dispatchers.IO) { insertEditedSong(target, outputFile, title) }
                        }
                        outputFile.delete()
                        finalized.fold(
                            onSuccess = { exportState = EditorExportState.Done },
                            onFailure = { e ->
                                exportState = EditorExportState.Error(e.message ?: "Couldn't save the edited file.")
                            },
                        )
                    },
                    onFailure = { e ->
                        exportState = EditorExportState.Error(e.message ?: "Export failed.")
                    },
                )
        }
    }

    fun dismissExportState() {
        exportState = EditorExportState.Idle
    }

    private suspend fun runExport(target: Song): File {
        val outputFile = File(getApplication<Application>().cacheDir, "edited_${System.currentTimeMillis()}.m4a")
        val sourceUri = Uri.parse(target.uri)
        val durationMs = target.durationMs

        // Kept ranges = everything NOT covered by a cut region.
        val cuts = cutRegions.sortedBy { it.startMs }
        val keptRanges = mutableListOf<Pair<Long, Long>>()
        var cursor = 0L
        for (cut in cuts) {
            if (cut.startMs > cursor) keptRanges += cursor to cut.startMs
            cursor = max(cursor, cut.endMs)
        }
        if (cursor < durationMs) keptRanges += cursor to durationMs
        if (keptRanges.isEmpty()) throw IllegalStateException("Nothing left after cuts.")

        val sonic = SonicAudioProcessor().apply {
            setSpeed(speed)
            setPitch(pitch)
        }
        val effects = Effects(listOf(sonic), emptyList())

        val editedItems = keptRanges.map { (start, end) ->
            val mediaItem = MediaItem.Builder()
                .setUri(sourceUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(start)
                        .setEndPositionMs(end)
                        .build()
                )
                .build()
            EditedMediaItem.Builder(mediaItem)
                .setEffects(effects)
                .build()
        }

        val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
            .addItems(editedItems)
            .build()
        val composition = Composition.Builder(listOf(sequence)).build()

        // Transformer must be built/driven from a Looper thread (main), unlike the rest
        // of this function which is plain computation.
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                val transformer = Transformer.Builder(getApplication<Application>())
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException,
                        ) {
                            if (cont.isActive) cont.resumeWithException(exception)
                        }
                    })
                    .build()
                transformer.start(composition, outputFile.absolutePath)
                cont.invokeOnCancellation { transformer.cancel() }
            }
        }
        return outputFile
    }

    // Inserts the edited file as a genuinely new song, with an explicit TITLE set
    // directly on the MediaStore row rather than relying on the exported file's own
    // embedded tag (which likely still carries the original's title, causing the
    // library's title+artist+duration duplicate-detection to silently hide it).
    private fun insertEditedSong(song: Song, editedFile: File, title: String): String {
        val safeTitle = title.ifBlank { "${song.title} (new)" }
        val safeFileName = safeTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        val resolver = getApplication<Application>().contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val pending = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeFileName.m4a")
            put(MediaStore.Audio.Media.TITLE, safeTitle)
            put(MediaStore.Audio.Media.ARTIST, song.artist)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, pending)
            ?: throw IllegalStateException("Couldn't create the new song entry.")

        resolver.openOutputStream(uri)?.use { out ->
            editedFile.inputStream().use { input -> input.copyTo(out) }
        } ?: throw IllegalStateException("Couldn't open the new song file for writing.")

        val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)

        return safeTitle
    }
}
