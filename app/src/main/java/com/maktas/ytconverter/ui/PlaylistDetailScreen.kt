package com.maktas.ytconverter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maktas.ytconverter.data.playlist.PlaylistSong
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistDetailScreen(
    vm: PlaylistViewModel,
    playback: PlaybackViewModel,
    modifier: Modifier = Modifier
) {
    val name = vm.openName
    val available = vm.availableUris

    // Keep a local copy for instant UI response during drag; persisted on every move.
    var songs by remember(vm.openSongs) { mutableStateOf(vm.openSongs) }

    val lazyState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyState) { from, to ->
        songs = songs.toMutableList().apply { add(to.index, removeAt(from.index)) }
        vm.reorder(songs)
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::close) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        val playable = songs.filter { it.uri in available }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${songs.size} song${if (songs.size == 1) "" else "s"}" +
                    if (playable.size < songs.size) " (${songs.size - playable.size} unavailable)" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::openSongPicker) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add songs")
                }
                val isThisActive = playback.activePlaylistId == vm.openId
                val showPause = isThisActive && playback.isPlaying
                Button(
                    onClick = {
                        if (showPause) playback.togglePlayPause()
                        else playback.playPlaylist(songs, available, vm.openId)
                    },
                    enabled = playable.isNotEmpty() || showPause
                ) {
                    Icon(
                        if (showPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (showPause) "Pause" else "Play all")
                }
            }
        }

        if (songs.isEmpty()) {
            Text(
                "No songs yet. Add some from the Songs tab.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(state = lazyState, modifier = Modifier.fillMaxSize()) {
                items(songs, key = { it.id }) { song ->
                    val missing = song.uri !in available
                    ReorderableItem(reorderState, key = song.id) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (missing) 0.4f else 1f)
                                .clickable(enabled = !missing) {
                                    playback.playPlaylist(songs, available, vm.openId, song.uri)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AudioArtwork(
                                if (missing) null else song.uri,
                                Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sub = buildString {
                                    if (song.artist.isNotBlank()) append(song.artist)
                                    if (missing) {
                                        if (isNotEmpty()) append(" · ")
                                        append("Unavailable")
                                    }
                                }
                                if (sub.isNotBlank()) {
                                    Text(
                                        sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (missing) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { vm.removeSong(song.id) }) {
                                Icon(
                                    Icons.Filled.RemoveCircleOutline,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                Icon(Icons.Filled.DragHandle, contentDescription = "Drag to reorder")
                            }
                        }
                    }
                }
            }
        }
    }
}
