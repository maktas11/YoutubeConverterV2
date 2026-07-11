package com.maktas.ytconverter.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.maktas.ytconverter.music.Song
import kotlinx.coroutines.delay
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun AudioEditorScreen(song: Song, onDismiss: () -> Unit) {
    val vm: AudioEditorViewModel = viewModel()
    val context = LocalContext.current
    val duration = song.durationMs.coerceAtLeast(1L)

    LaunchedEffect(song.id) { vm.open(song) }

    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(song.id) {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(song.uri)))
        player.prepare()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceIn(0, duration)
            isPlaying = player.isPlaying
            // Skip over cut regions live during preview playback, so you can hear what
            // the trimmed result will actually sound like before exporting.
            if (isPlaying) {
                val activeCut = vm.cutRegions.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }
                if (activeCut != null) player.seekTo(activeCut.endMs)
            }
            delay(100)
        }
    }

    // Apply speed/pitch to the live preview immediately, so the sliders are audible
    // while editing instead of only taking effect after export.
    LaunchedEffect(vm.speed, vm.pitch) {
        player.playbackParameters = PlaybackParameters(vm.speed, vm.pitch)
    }

    // Which region/handle a drag on the waveform is currently manipulating, if any.
    var dragTarget by remember { mutableStateOf<DragTarget?>(null) }
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(horizontal = 16.dp),
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val handleSlopPx = with(density) { 20.dp.toPx() }

            fun timeForX(x: Float): Long = ((x / widthPx) * duration).toLong().coerceIn(0, duration)
            fun xForTime(t: Long): androidx.compose.ui.unit.Dp = maxWidth * (t.toFloat() / duration)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInputForEditor(
                        regionsProvider = { vm.cutRegions },
                        widthPx = widthPx,
                        handleSlopPx = handleSlopPx,
                        timeForX = ::timeForX,
                        onDragTargetChange = { dragTarget = it },
                        onSeek = { t -> player.seekTo(t) },
                        onUpdateRegion = { index, region -> vm.updateCutRegion(index, region) },
                        duration = duration,
                        dragTargetProvider = { dragTarget },
                    ),
            ) {
                if (vm.isLoadingWaveform) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    WaveformCanvas(waveform = vm.waveform, modifier = Modifier.fillMaxSize())
                }

                vm.cutRegions.forEachIndexed { index, region ->
                    val startX = xForTime(region.startMs)
                    val regionWidth = (xForTime(region.endMs) - startX).coerceAtLeast(4.dp)
                    Box(
                        modifier = Modifier
                            .offset(x = startX)
                            .width(regionWidth)
                            .fillMaxHeight()
                            .background(Color(0xFFE53935).copy(alpha = 0.35f)),
                    )
                    IconButton(
                        onClick = { vm.removeCutRegion(index) },
                        modifier = Modifier
                            .offset(x = startX)
                            .size(28.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove cut",
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color(0xFFE53935), shape = androidx.compose.foundation.shape.CircleShape),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .offset(x = xForTime(positionMs))
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${formatMs(positionMs)} / ${formatMs(duration)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
            }
            TextButton(onClick = {
                val end = (positionMs + 2_000L).coerceAtMost(duration)
                if (end > positionMs) vm.addCutRegion(CutRegion(positionMs, end))
            }) { Text("+ Add cut") }
        }

        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Speed: ${"%.2f".format(vm.speed)}x", style = MaterialTheme.typography.bodyMedium)
            Slider(value = vm.speed, onValueChange = vm::updateSpeed, valueRange = 0.5f..2f)

            val semitones = 12f * ln(vm.pitch) / ln(2f)
            Text(
                "Pitch: ${if (semitones >= 0) "+" else ""}${"%.1f".format(semitones)} semitones",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = semitones,
                onValueChange = { st -> vm.updatePitch(2f.pow(st / 12f)) },
                valueRange = -12f..12f,
            )
        }

        Spacer(Modifier.weight(1f))

        var showNameDialog by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Button(
                onClick = { showNameDialog = true },
                enabled = vm.hasChanges,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save as new") }
        }

        if (showNameDialog) {
            SaveAsDialog(
                initialName = "${song.title} (edited)",
                onConfirm = { name ->
                    showNameDialog = false
                    vm.export(name)
                },
                onDismiss = { showNameDialog = false },
            )
        }
    }

    when (val state = vm.exportState) {
        EditorExportState.Idle -> Unit
        EditorExportState.Exporting -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text("Exporting…", color = Color.White)
            }
        }

        EditorExportState.Done -> LaunchedEffect(state) {
            Toast.makeText(context, "Saved as a new song", Toast.LENGTH_SHORT).show()
            vm.dismissExportState()
            onDismiss()
        }

        is EditorExportState.Error -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = vm::dismissExportState) { Text("OK") }
            }
        }
    }
}

@Composable
private fun SaveAsDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as new song") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class DragTarget(val index: Int, val isStart: Boolean, val moveWhole: Boolean, val grabOffsetMs: Long)

private fun Modifier.pointerInputForEditor(
    regionsProvider: () -> List<CutRegion>,
    widthPx: Float,
    handleSlopPx: Float,
    timeForX: (Float) -> Long,
    onDragTargetChange: (DragTarget?) -> Unit,
    onSeek: (Long) -> Unit,
    onUpdateRegion: (Int, CutRegion) -> Unit,
    duration: Long,
    dragTargetProvider: () -> DragTarget?,
): Modifier = this.then(
    // Keyed on widthPx/duration only — NOT the region list, which changes on every drag
    // update. Keying on it would restart this pointerInput block mid-gesture and cancel
    // the drag; regionsProvider() reads the live list instead without needing a restart.
    Modifier.pointerInput(widthPx, duration) {
        detectDragGestures(
            onDragStart = { offset ->
                val t = timeForX(offset.x)
                val hit = findHandle(regionsProvider(), offset.x, widthPx, duration, handleSlopPx, t)
                onDragTargetChange(hit)
                if (hit == null) onSeek(t)
            },
            onDrag = { change, _ ->
                change.consume()
                val t = timeForX(change.position.x)
                val target = dragTargetProvider()
                if (target == null) {
                    onSeek(t)
                    return@detectDragGestures
                }
                val region = regionsProvider().getOrNull(target.index) ?: return@detectDragGestures
                val updated = when {
                    target.moveWhole -> {
                        val length = region.endMs - region.startMs
                        val newStart = (t - target.grabOffsetMs).coerceIn(0, duration - length)
                        CutRegion(newStart, newStart + length)
                    }
                    target.isStart -> CutRegion(t.coerceIn(0, region.endMs), region.endMs)
                    else -> CutRegion(region.startMs, t.coerceIn(region.startMs, duration))
                }
                onUpdateRegion(target.index, updated)
            },
            onDragEnd = { onDragTargetChange(null) },
            onDragCancel = { onDragTargetChange(null) },
        )
    }
)

// Finds the nearest region edge within [slopPx] of [xPx], else "inside this region" for a
// whole-region move, else null (empty space — a plain seek).
private fun findHandle(
    regions: List<CutRegion>,
    xPx: Float,
    widthPx: Float,
    duration: Long,
    slopPx: Float,
    tappedMs: Long,
): DragTarget? {
    fun xForTime(t: Long) = (t.toFloat() / duration) * widthPx

    regions.forEachIndexed { index, region ->
        val startX = xForTime(region.startMs)
        val endX = xForTime(region.endMs)
        if (kotlin.math.abs(xPx - startX) <= slopPx) return DragTarget(index, isStart = true, moveWhole = false, grabOffsetMs = 0)
        if (kotlin.math.abs(xPx - endX) <= slopPx) return DragTarget(index, isStart = false, moveWhole = false, grabOffsetMs = 0)
    }
    regions.forEachIndexed { index, region ->
        if (tappedMs in region.startMs..region.endMs) {
            return DragTarget(index, isStart = false, moveWhole = true, grabOffsetMs = tappedMs - region.startMs)
        }
    }
    return null
}

@Composable
private fun WaveformCanvas(waveform: FloatArray, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        if (waveform.isEmpty()) return@Canvas
        val barWidth = size.width / waveform.size
        val midY = size.height / 2f
        waveform.forEachIndexed { i, amplitude ->
            val barHeight = (amplitude * size.height).coerceAtLeast(2f)
            val x = i * barWidth
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x, midY - barHeight / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barHeight),
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
