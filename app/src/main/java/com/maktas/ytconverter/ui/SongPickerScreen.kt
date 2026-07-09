package com.maktas.ytconverter.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maktas.ytconverter.music.Song

@Composable
fun SongPickerScreen(
    vm: PlaylistViewModel,
    playlistId: Long,
    modifier: Modifier = Modifier,
) {
    val allSongs = vm.songPickerAll
    val query = vm.songPickerQuery
    val filtered = remember(allSongs, query) {
        if (query.isBlank()) allSongs
        else allSongs.filter {
            it.title.lowercase().contains(query.lowercase()) ||
                it.artist.lowercase().contains(query.lowercase())
        }
    }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            IconButton(onClick = vm::closeSongPicker) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel")
            }
            Text(
                "Add songs",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    val songs = allSongs.filter { it.id in selected }
                    vm.confirmAddSongs(playlistId, songs)
                },
                enabled = selected.isNotEmpty()
            ) {
                Text(if (selected.isEmpty()) "Add" else "Add (${selected.size})")
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = vm::onSongPickerQueryChange,
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        if (allSongs.isEmpty()) {
            Text(
                "Scanning library…",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { song ->
                    val isSelected = song.id in selected
                    SongPickerRow(
                        song = song,
                        isSelected = isSelected,
                        onToggle = {
                            selected = if (isSelected) selected - song.id else selected + song.id
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SongPickerRow(song: Song, isSelected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(4.dp))
        AudioArtwork(song.uri, Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)))
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
                if (song.durationMs > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(pickerFormatMs(song.durationMs))
                }
            }
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun pickerFormatMs(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
