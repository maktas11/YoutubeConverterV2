package com.maktas.ytconverter.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.LruCache
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MicExternalOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maktas.ytconverter.data.CoverArtRepository
import com.maktas.ytconverter.data.SortMode
import com.maktas.ytconverter.data.playlist.PlaylistWithCount
import com.maktas.ytconverter.music.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MusicScreen(
    modifier: Modifier = Modifier,
    onSearchCoverForSong: (Song) -> Unit = {},
    onEditSong: (Song) -> Unit = {},
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

    // Scoped storage delete consent: MusicViewModel sets pendingDeleteIntentSender when
    // contentResolver.delete() needs the user to approve via the system dialog.
    val deletePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> vm.onDeletePermissionResult(result.resultCode == Activity.RESULT_OK) }

    LaunchedEffect(vm.pendingDeleteIntentSender) {
        vm.pendingDeleteIntentSender?.let { sender ->
            deletePermissionLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    // Same again for editing a song this app doesn't own — the rename has already applied
    // in-app, so declining here just leaves MediaStore untouched.
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> vm.onWritePermissionResult(result.resultCode == Activity.RESULT_OK) }

    LaunchedEffect(vm.pendingWriteIntentSender) {
        vm.pendingWriteIntentSender?.let { sender ->
            writePermissionLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    LaunchedEffect(Unit) {
        vm.errorEvents.collect { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
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
            SongsTab(
                vm, playback, playlistVm, onSearchCoverForSong,
                onGrant = { launcher.launch(permission) },
                onEditSong = onEditSong,
            )
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
    onGrant: () -> Unit,
    onEditSong: (Song) -> Unit,
) {
    when (val s = vm.state) {
        LibraryUiState.NeedsPermission -> PermissionPrompt(onGrant)
        LibraryUiState.Loading -> LoadingState()
        is LibraryUiState.Loaded -> LoadedLibrary(vm, s.songs, playback, playlistVm, onSearchCoverForSong, onEditSong)
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

/**
 * Prompts once for "All files access" so deleting a song doesn't need a per-file
 * scoped-storage consent dialog every time. Rechecked on resume since granting it
 * happens in a separate system Settings screen with no direct callback.
 */
@Composable
private fun StorageAccessBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Environment.isExternalStorageManager()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = Environment.isExternalStorageManager()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (granted) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Enable full storage access to delete songs without a confirmation popup every time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = {
            val intent = runCatching {
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
            }.getOrElse { Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION) }
            context.startActivity(intent)
        }) { Text("Enable") }
    }
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
    onEditSong: (Song) -> Unit,
) {
    StorageAccessBanner()
    OutlinedTextField(
        value = vm.query,
        onValueChange = vm::onQueryChange,
        label = { Text("Search songs") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentSongIndex = songs.indexOfFirst {
        it.title == playback.nowPlaying?.title && it.artist == playback.nowPlaying?.artist
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${songs.size} song${if (songs.size == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { scope.launch { listState.animateScrollToItem(currentSongIndex) } },
            enabled = currentSongIndex >= 0,
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = "Go to current song")
        }
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
        var editingDetails by remember { mutableStateOf<Song?>(null) }

        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { playback.play(songs, index) },
                        onMoreOptions = { actionSong = song },
                    )
                }
            }

            if (vm.sortMode == SortMode.TITLE) {
                // First index for each letter — only meaningful since songs are A-Z sorted here.
                val letterIndices = remember(songs) {
                    val map = mutableMapOf<Char, Int>()
                    songs.forEachIndexed { index, song ->
                        val letter = song.title.trim().firstOrNull()?.uppercaseChar()
                        if (letter != null && letter in 'A'..'Z' && letter !in map) map[letter] = index
                    }
                    map
                }
                AlphabetSidebar(
                    availableLetters = letterIndices.keys,
                    onLetterSelected = { letter ->
                        val target = letterIndices[letter]
                            ?: letterIndices.keys.minByOrNull { kotlin.math.abs(it - letter) }?.let { letterIndices[it] }
                        target?.let { scope.launch { listState.scrollToItem(it) } }
                    },
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

        editingDetails?.let { song ->
            EditSongDetailsDialog(
                song = song,
                onSave = { title, artist ->
                    vm.editSong(song, title, artist)
                    playback.updateSongMetadata(song.uri, title.trim(), artist.trim())
                    editingDetails = null
                },
                onDismiss = { editingDetails = null },
            )
        }

        actionSong?.let { song ->
            SongActionDialog(
                song = song,
                onEditDetails = { editingDetails = song; actionSong = null },
                onAddToPlaylist = { addingToPlaylist = song; actionSong = null },
                onChangeCover = { onSearchCoverForSong(song); actionSong = null },
                onEdit = { onEditSong(song); actionSong = null },
                onDelete = { vm.deleteSong(song); actionSong = null },
                onHide = { vm.hideSong(song); actionSong = null },
                onDismiss = { actionSong = null }
            )
        }
    }
}

/** Fast-scroll index shown alongside the song list in A-Z sort mode. Tapping a letter jumps
 *  to the first song starting with it; letters with no songs still work, jumping to the
 *  nearest letter that does (shown dimmed to indicate there's no exact match). */
@Composable
private fun AlphabetSidebar(
    availableLetters: Set<Char>,
    onLetterSelected: (Char) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        ('A'..'Z').forEach { letter ->
            Text(
                letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (letter in availableLetters) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.clickable { onLetterSelected(letter) },
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
    onEditDetails: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onChangeCover: () -> Unit,
    onEdit: () -> Unit,
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
                    text = { Text("Edit details") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = onEditDetails
                )
                HorizontalDivider()
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
                    text = { Text("Edit audio (trim, speed, pitch)") },
                    leadingIcon = { Icon(Icons.Filled.ContentCut, contentDescription = null) },
                    onClick = onEdit
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

/** Renames a song. Artist is optional; a blank title is refused since the list has nothing
 *  left to show for the song. */
@Composable
private fun EditSongDetailsDialog(
    song: Song,
    onSave: (title: String, artist: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit details") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    singleLine = true,
                    label = { Text("Artist") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, artist) },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
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

/** Artwork resolution used by list rows — matches the old hardcoded thumbnail size. */
const val ART_PX_LIST = 256

/** Artwork resolution for full-width displays (Now Playing). Album art is commonly
 *  embedded at 1000x1000 or larger, so this keeps a big cover sharp instead of
 *  upscaling a thumbnail across the whole screen. */
const val ART_PX_FULL = 1080

/**
 * Shows custom cover art (if set via CoverArtRepository), then the artwork embedded in
 * the file itself, then MediaStore's generated thumbnail, then a generic music note icon.
 *
 * [targetSizePx] is the shortest edge this will be drawn at. The embedded picture is
 * decoded downsampled to it, so list rows stay cheap while Now Playing gets full detail.
 */
@Composable
fun AudioArtwork(
    uri: String?,
    title: String? = null,
    modifier: Modifier = Modifier,
    targetSizePx: Int = ART_PX_LIST,
) {
    val context = LocalContext.current
    val version by CoverArtRepository.version.collectAsState()

    val art by produceState<ImageBitmap?>(initialValue = null, uri, title, version, targetSizePx) {
        val cacheKey = "$title|$uri|$targetSizePx"
        ArtworkCache.get(cacheKey, version)?.let {
            value = it
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            // 1. Custom cover keyed by title
            if (title != null) {
                CoverArtRepository.getFile(context, title)?.let { f ->
                    BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                        ?.let { return@withContext it }
                }
            }
            val parsed = uri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                ?: return@withContext null
            // 2. Artwork embedded in the file. This is the original the tagger wrote, and
            //    the source MediaStore derives its thumbnail cache from — reading it here
            //    skips that lossy downscale.
            embeddedArtwork(context, parsed, targetSizePx)?.asImageBitmap()
                ?.let { return@withContext it }
            // 3. MediaStore's generated thumbnail, for files with no embedded picture.
            runCatching {
                context.contentResolver
                    .loadThumbnail(parsed, Size(targetSizePx, targetSizePx), null)
                    .asImageBitmap()
            }.getOrNull()
        }
        value?.let { ArtworkCache.put(cacheKey, it, version) }
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

/**
 * In-memory artwork cache. Reading embedded art costs more than MediaStore's thumbnail
 * cache did, so without this a long list would re-decode every row on every scroll pass.
 * Entries are dropped wholesale whenever a cover is saved (the [CoverArtRepository]
 * version changes), since that's the only way artwork changes under us.
 */
private object ArtworkCache {
    // 1/8th of the heap, the conventional bitmap-cache budget, measured in KB.
    private val cache = object : LruCache<String, ImageBitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt().coerceAtLeast(4096)
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width.toLong() * value.height * 4 / 1024).toInt().coerceAtLeast(1)
    }
    private var cachedVersion = 0L

    @Synchronized
    fun get(key: String, version: Long): ImageBitmap? {
        if (version != cachedVersion) return null
        return cache.get(key)
    }

    @Synchronized
    fun put(key: String, bitmap: ImageBitmap, version: Long) {
        if (version != cachedVersion) {
            cache.evictAll()
            cachedVersion = version
        }
        cache.put(key, bitmap)
    }
}

/** Reads the cover art embedded in an audio file, downsampled to [targetSizePx].
 *  Returns null when the file has no embedded picture or can't be read. */
private fun embeddedArtwork(context: Context, uri: Uri, targetSizePx: Int): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.embeddedPicture?.let { bytes -> decodeSampled(bytes, targetSizePx) }
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/** Decodes [bytes] no smaller than [targetSizePx] on its shortest edge. Only ever
 *  halves the source, so the result is never upscaled or blurred. */
private fun decodeSampled(bytes: ByteArray, targetSizePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    var shortest = minOf(bounds.outWidth, bounds.outHeight)
    while (shortest / 2 >= targetSizePx) {
        sample *= 2
        shortest /= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun formatMs(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
