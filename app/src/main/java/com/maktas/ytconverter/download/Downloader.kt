package com.maktas.ytconverter.download

import android.content.Context
import com.maktas.ytconverter.data.AudioFormat
import com.maktas.ytconverter.data.VideoQuality
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thin wrapper around the on-device yt-dlp engine.
 *
 * yt-dlp can only write to real file paths, not content:// URIs, so every
 * download stages into a private temp dir and is then published to the public
 * Download/ folder via [MediaStoreSaver].
 */
object Downloader {

    data class Saved(val displayName: String)

    /** Audio download. M4A = stream copy (lossless/instant); MP3 = FFmpeg re-encode. */
    suspend fun downloadAudio(
        context: Context,
        url: String,
        processId: String,
        format: AudioFormat,
        embedThumbnail: Boolean,
        embedMetadata: Boolean,
        onProgress: (percent: Float, etaSeconds: Long) -> Unit,
    ): Result<Saved> = runDownload(context, url, processId, onProgress) {
        when (format) {
            AudioFormat.M4A -> addOption("-f", "ba[ext=m4a]/ba")
            AudioFormat.MP3 -> {
                addOption("-f", "ba/b")
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
            }
        }
        applyEmbeds(embedThumbnail, embedMetadata)
    }

    /** Video download: best video+audio merged into an MP4 (needs FFmpeg). */
    suspend fun downloadVideo(
        context: Context,
        url: String,
        processId: String,
        quality: VideoQuality,
        embedThumbnail: Boolean,
        embedMetadata: Boolean,
        onProgress: (percent: Float, etaSeconds: Long) -> Unit,
    ): Result<Saved> = runDownload(context, url, processId, onProgress) {
        addOption("-f", videoFormat(quality))
        addOption("--merge-output-format", "mp4")
        applyEmbeds(embedThumbnail, embedMetadata)
    }

    /** Kills an in-progress download started with [processId]. */
    fun cancel(processId: String): Boolean =
        YoutubeDL.getInstance().destroyProcessById(processId)

    // --- internals ---

    private suspend fun runDownload(
        context: Context,
        url: String,
        processId: String,
        onProgress: (Float, Long) -> Unit,
        configure: YoutubeDLRequest.() -> Unit,
    ): Result<Saved> = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "downloads/$processId").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val request = YoutubeDLRequest(url).apply {
                configure()
                // A pasted watch URL can carry a playlist id; only grab the video.
                addOption("--no-playlist")
                addOption("-o", "${workDir.absolutePath}/%(title)s.%(ext)s")
            }

            YoutubeDL.getInstance().execute(request, processId) { percent, eta, _ ->
                onProgress(percent, eta)
            }

            val file = workDir.listFiles()
                ?.filterNot { it.isDirectory || it.name.endsWith(".part") }
                ?.maxByOrNull { it.length() }
                ?: return@withContext Result.failure(
                    IllegalStateException("Download finished but produced no file")
                )

            Result.success(Saved(MediaStoreSaver.saveToDownloads(context, file)))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            workDir.deleteRecursively()
        }
    }

    // Cover art (full 16:9, not cropped) + metadata, when enabled.
    private fun YoutubeDLRequest.applyEmbeds(thumbnail: Boolean, metadata: Boolean) {
        if (thumbnail) addOption("--embed-thumbnail")
        if (metadata) addOption("--embed-metadata")
    }

    private fun videoFormat(quality: VideoQuality): String = when (quality) {
        VideoQuality.BEST ->
            "bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b"
        VideoQuality.P1080 ->
            "bv*[height<=1080][ext=mp4]+ba[ext=m4a]/bv*[height<=1080]+ba/b[height<=1080]/b"
        VideoQuality.P720 ->
            "bv*[height<=720][ext=mp4]+ba[ext=m4a]/bv*[height<=720]+ba/b[height<=720]/b"
    }
}
