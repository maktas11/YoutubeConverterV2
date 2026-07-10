package com.maktas.ytconverter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MicExternalOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maktas.ytconverter.data.CoverArtRepository
import com.maktas.ytconverter.data.playlist.PlaylistWithCount
import com.maktas.ytconverter.music.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MusicScreen(
    modifier: Modifier = Modifier,
    onSearchCoverForSong: (Song) -> Unit = {},
) {
    val vm: MusicViewModel = viewModel()
    val playback: PlaybackViewModel = viewModel()
    val playlistVm: PlaylistViewModel = viewModel()
    val context = LocalContext.current
    val permission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.refresh() else vm.onPermissionResult(false)
    }

    var tab by rememberSaveable { mutableStateOf(0) }

    // If a playlist is open, show its detail (or song picker on top of it).
    if (playlistVm.openId != null) {
        if (playlistVm.showSongPicker) {
            BackHandler { playlistVm.closeSongPicker() }
            SongPickerScreen(
                vm = playlistVm,
                playlistId = playlistVm.openId!!,
                modifier = modifier
            )
        } else {
            BackHandler { playlistVm.close() }
            PlaylistDetailScreen(vm = playlistVm, playback = playback, modifier = modifier)
        }
        return
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Echo Music Player", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TabButton("Songs", tab == 0, Modifier.weight(1f)) { tab = 0 }
            TabButton("Playlists", tab == 1, Modifier.weight(1f)) { tab = 1 }
        }
        Spacer(Modifier.height(12.dp))

        if (tab == 0) {
            SongsTab(vm, playback, playlistVm, onSearchCoverForSong, onGrant = { launcher.launch(permission) })
        } else {
            PlaylistsPane(playlistVm)
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun SongsTab(
    vm: MusicViewModel,
    playback: PlaybackViewModel,
    playlistVm: PlaylistViewModel,
    onSearchCoverForSong: (Song) -> Unit,
    onGrant: () -> Unit
) {
    when (val s = vm.state) {
        LibraryUiState.NeedsPermission -> PermissionPrompt(onGrant)
        LibraryUiState.Loading -> LoadingState()
        is LibraryUiState.Loaded -> LoadedLibrary(vm, s.songs, playback, playlistVm, onSearchCoverForSong)
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    EmptyState(
        icon = Icons.Filled.LibraryMusic,
        message = "Allow access to your audio files to see your music library.",
        action = { Button(onClick = onGrant) { Text("Grant access") } }
    )
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Scanning your music…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadedLibrary(
    vm: MusicViewModel,
    songs: List<Song>,
    playback: PlaybackViewModel,
    playlistVm: PlaylistViewModel,
    onSearchCoverForSong: (Song) -> Unit,
) {
    OutlinedTextField(
        value = vm.query,
        onValueChange = vm::onQueryChange,
        label = { Text("Search songs") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${songs.size} song${if (songs.size == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = vm::refresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
        }
        TextButton(onClick = {
            vm.setSort(if (vm.sortMode == SortMode.NEWEST) SortMode.TITLE else SortMode.NEWEST)
        }) {
            Text(if (vm.sortMode == SortMode.NEWEST) "Sort: Newest" else "Sort: A–Z")
        }
    }
    Spacer(Modifier.height(8.dp))

    if (songs.isEmpty()) {
        EmptyState(
            icon = if (vm.query.isBlank()) Icons.Filled.MusicNote else Icons.Filled.MicExternalOff,
            message = if (vm.query.isBlank())
                "No music found yet.\nDownload some songs from the Converter tab."
            else
                "No songs match \"${vm.query}\"."
        )
    } else {
        val playlists by playlistVm.playlists.collectAsState()
        var addingToPlaylist by remember { mutableStateOf<Song?>(null) }
        var actionSong by remember { mutableStateOf<Song?>(null) }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { playback.play(songs, index) },
                    onMoreOptions = { actionSong = song },
                )
            }
        }

        addingToPlaylist?.let { song ->
            AddToPlaylistDialog(
                playlists = playlists,
                onPick = { playlistId ->
                    playlistVm.addSong(playlistId, song)
                    addingToPlaylist = null
                },
                onDismiss = { addingToPlaylist = null }
            )
        }

        actionSong?.let { song ->
            SongActionDialog(
                song = song,
                onAddToPlaylist = { addingToPlaylist = song; actionSong = null },
                onChangeCover = { onSearchCoverForSong(song); actionSong = null },
                onDelete = { vm.deleteSong(song); actionSong = null },
                onHide = { vm.hideSong(song); actionSong = null },
                onDismiss = { actionSong = null }
            )
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onMoreOptions: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMoreOptions,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AudioArtwork(song.uri, song.title, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                if (song.artist.isNotBlank()) append(song.artist)
                if (song.durationMs > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(formatMs(song.durationMs))
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onMoreOptions != null) {
            IconButton(onClick = onMoreOptions) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
            }
        }
    }
}

@Composable
private fun SongActionDialog(
    song: Song,
    onAddToPlaylist: () -> Unit,
    onChangeCover: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete from device?") },
            text = {
                Text(
                    "\"${song.title}\" will be permanently deleted from your phone. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column {
                DropdownMenuItem(
                    text = { Text("Change cover art") },
                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                    onClick = onChangeCover
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    leadingIcon = { Icon(Icons.Filled.AddCircleOutline, contentDescription = null) },
                    onClick = onAddToPlaylist
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Hide from library") },
                    leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                    onClick = onHide
                )
                DropdownMenuItem(
                    text = { Text("Delete from device", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = { showDeleteConfirm = true }
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun AddToPlaylistDialog(
    playlists: List<PlaylistWithCount>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            if (playlists.isEmpty()) {
                Text("No playlists yet. Create one in the Playlists tab.")
            } else {
                Column {
                    playlists.forEach { pl ->
                        Text(
                            pl.playlist.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = { onPick(pl.playlist.id) })
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PlaylistsPane(vm: PlaylistViewModel) {
    val playlists by vm.playlists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PlaylistWithCount?>(null) }

    Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("New playlist")
    }
    Spacer(Modifier.height(12.dp))

    if (playlists.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.QueueMusic,
            message = "No playlists yet.\nTap \"New playlist\" to create one."
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(playlists, key = { it.playlist.id }) { pl ->
                PlaylistRow(
                    pl,
                    onClick = { vm.open(pl) },
                    onRename = { renaming = pl },
                    onDelete = { vm.delete(pl.playlist.id) }
                )
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "New playlist",
            initial = "",
            onConfirm = { vm.create(it); showCreate = false },
            onDismiss = { showCreate = false }
        )
    }
    renaming?.let { pl ->
        NameDialog(
            title = "Rename playlist",
            initial = pl.playlist.name,
            onConfirm = { vm.rename(pl.playlist.id, it); renaming = null },
            onDismiss = { renaming = null }
        )
    }
}

@Composable
private fun PlaylistRow(
    pl: PlaylistWithCount,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                pl.playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${pl.songCount} song${if (pl.songCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") }
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    message: String,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/**
 * Shows custom cover art (if set via CoverArtRepository) or falls back to the
 * embedded MediaStore thumbnail, then to a generic music note icon.
 */
@Composable
fun AudioArtwork(uri: String?, title: String? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version by CoverArtRepository.version.collectAsState()

    val art by produceState<ImageBitmap?>(initialValue = null, uri, title, version) {
        value = withContext(Dispatchers.IO) {
            // 1. Custom cover keyed by title
            if (title != null) {
                CoverArtRepository.getFile(context, title)?.let { f ->
                    BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                        ?.let { return@withContext it }
                }
            }
            // 2. Embedded thumbnail from MediaStore
            runCatching {
                uri?.let {
                    context.contentResolver
                        .loadThumbnail(Uri.parse(it), Size(256, 256), null)
                        .asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = art
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
