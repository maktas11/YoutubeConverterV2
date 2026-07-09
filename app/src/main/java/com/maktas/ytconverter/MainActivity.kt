package com.maktas.ytconverter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import coil3.compose.AsyncImage
import com.maktas.ytconverter.data.AppTheme
import com.maktas.ytconverter.data.DownloadFormat
import com.maktas.ytconverter.download.DownloadUiState
import com.maktas.ytconverter.download.SearchResult
import com.maktas.ytconverter.ui.AddToPlaylistDialog
import com.maktas.ytconverter.ui.AudioArtwork
import com.maktas.ytconverter.ui.MainViewModel
import com.maktas.ytconverter.ui.MusicScreen
import com.maktas.ytconverter.ui.PendingDownload
import com.maktas.ytconverter.ui.PlaybackViewModel
import com.maktas.ytconverter.ui.PlaylistViewModel
import com.maktas.ytconverter.ui.RepeatMode
import com.maktas.ytconverter.ui.SearchUiState
import com.maktas.ytconverter.ui.SettingsScreen
import com.maktas.ytconverter.ui.theme.YoutubeConverterTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val mainVm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractSharedUrl(intent)?.let { mainVm.onSharedUrl(it) }
        val app = application as App
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            val darkTheme = when (settings.theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }
            YoutubeConverterTheme(darkTheme = darkTheme) {
                RequestNotificationPermission()
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var showNowPlaying by rememberSaveable { mutableStateOf(false) }
                var showQueue by rememberSaveable { mutableStateOf(false) }
                val playback: PlaybackViewModel = viewModel()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        showSettings -> {
                            BackHandler { showSettings = false }
                            Scaffold { innerPadding ->
                                SettingsScreen(
                                    vm = vm,
                                    onBack = { showSettings = false },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }

                        showNowPlaying -> {
                            BackHandler { showNowPlaying = false }
                            NowPlayingScreen(
                                playback = playback,
                                onClose = { showNowPlaying = false },
                                onShowQueue = { showQueue = true }
                            )
                        }

                        else -> MainScaffold(
                            app = app,
                            vm = vm,
                            playback = playback,
                            onOpenSettings = { showSettings = true },
                            onExpandPlayer = { showNowPlaying = true },
                            onShowQueue = { showQueue = true }
                        )
                    }
                    if (showQueue) {
                        QueueSheet(
                            playback = playback,
                            onDismiss = { showQueue = false }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractSharedUrl(intent)?.let { mainVm.onSharedUrl(it) }
    }

    private fun extractSharedUrl(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        // YouTube share text is usually just the URL, but may include a title before it.
        return text.trim().split("\\s+".toRegex())
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
    }
}

@Composable
private fun MainScaffold(
    app: App,
    vm: MainViewModel,
    playback: PlaybackViewModel,
    onOpenSettings: () -> Unit,
    onExpandPlayer: () -> Unit,
    onShowQueue: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val playlistVm: PlaylistViewModel = viewModel()
    // Jump to Converter tab when a YouTube URL is shared into the app.
    LaunchedEffect(vm.navigateToConverter) {
        if (vm.navigateToConverter) {
            pagerState.scrollToPage(1)
            vm.onNavigatedToConverter()
        }
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        bottomBar = {
            Column {
                if (pagerState.currentPage == 1) DownloadStatusBar(vm, playlistVm)
                MiniPlayer(playback, onExpand = onExpandPlayer, onShowQueue = onShowQueue)
                NavigationBar {
                    NavigationBarItem(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        icon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                        label = { Text("Music") }
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        label = { Text("Converter") }
                    )
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                MusicScreen(modifier = Modifier.padding(innerPadding))
            } else {
                ConverterScreen(
                    initState = app.initState,
                    vm = vm,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    playback: PlaybackViewModel,
    onExpand: () -> Unit,
    onShowQueue: () -> Unit,
) {
    val np = playback.nowPlaying ?: return
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioArtwork(np.artworkUri, Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    np.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (np.artist.isNotBlank()) {
                    Text(
                        np.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = playback::previous) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
            }
            IconButton(onClick = playback::togglePlayPause) {
                Icon(
                    if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause"
                )
            }
            IconButton(onClick = playback::next) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next")
            }
            IconButton(onClick = onShowQueue) {
                Icon(Icons.Filled.QueueMusic, contentDescription = "Queue")
            }
        }
    }
}

@Composable
private fun NowPlayingScreen(
    playback: PlaybackViewModel,
    onClose: () -> Unit,
    onShowQueue: () -> Unit,
) {
    val np = playback.nowPlaying
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close")
            }
            Text(
                "Now Playing",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onShowQueue) {
                Icon(Icons.Filled.QueueMusic, contentDescription = "Queue")
            }
        }

        if (np == null) {
            Spacer(Modifier.weight(1f))
            Text("Nothing playing", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            return@Column
        }

        Spacer(Modifier.height(24.dp))
        AudioArtwork(
            np.artworkUri,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.height(24.dp))
        Text(
            np.title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (np.artist.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                np.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(24.dp))
        SeekBar(playback)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle button with filled circle background when active (#2)
            Box(contentAlignment = Alignment.Center) {
                if (playback.shuffleEnabled) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    ) {}
                }
                IconButton(onClick = playback::toggleShuffle) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playback.shuffleEnabled) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = playback::previous) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
            }
            FilledIconButton(onClick = playback::togglePlayPause, modifier = Modifier.size(64.dp)) {
                Icon(
                    if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = playback::next) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next")
            }
            // Repeat button: filled circle when active, popup to pick mode (#4)
            Box(contentAlignment = Alignment.Center) {
                if (playback.repeatMode != RepeatMode.OFF) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    ) {}
                }
                var showRepeatMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showRepeatMenu = true }) {
                        Icon(
                            if (playback.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Repeat",
                            tint = if (playback.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = showRepeatMenu, onDismissRequest = { showRepeatMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Off") },
                            onClick = { playback.setRepeat(RepeatMode.OFF); showRepeatMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Repeat all") },
                            onClick = { playback.setRepeat(RepeatMode.ALL); showRepeatMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Repeat one") },
                            onClick = { playback.setRepeat(RepeatMode.ONE); showRepeatMenu = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeekBar(playback: PlaybackViewModel) {
    val duration = playback.durationMs().coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var position by remember { mutableLongStateOf(0L) }

    LaunchedEffect(playback.nowPlaying?.title) { position = 0L }

    LaunchedEffect(playback.nowPlaying, playback.isPlaying) {
        while (true) {
            if (!dragging) position = playback.positionMs()
            delay(500)
        }
    }

    val fraction = if (dragging) dragFraction else (position.toFloat() / duration).coerceIn(0f, 1f)
    Slider(
        value = fraction,
        onValueChange = { dragging = true; dragFraction = it },
        onValueChangeFinished = {
            playback.seekTo((dragFraction * duration).toLong())
            dragging = false
        }
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val shown = if (dragging) (dragFraction * duration).toLong() else position
        Text(formatDuration(shown / 1000), style = MaterialTheme.typography.bodySmall)
        Text(formatDuration(playback.durationMs() / 1000), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(playback: PlaybackViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        if (playback.queue.isEmpty()) {
            Text(
                "No queue",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Header row: title + reshuffle or pure-random message (#3)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Up next",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (playback.shuffleEnabled) {
                    if (playback.isPureRandom) {
                        Text(
                            "Pure random",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        TextButton(
                            onClick = playback::reshuffle,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Reshuffle")
                        }
                    }
                }
            }
            val listState = rememberLazyListState()
            val currentIndex = playback.currentIndex
            LaunchedEffect(Unit) {
                if (currentIndex >= 0) listState.scrollToItem(currentIndex)
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(playback.queue) { index, item ->
                    val isCurrent = index == playback.currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AudioArtwork(
                            item.artworkUri,
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.artist.isNotBlank()) {
                                Text(
                                    item.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (isCurrent) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = "Now playing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConverterScreen(
    initState: App.InitState,
    vm: MainViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
        Text("YT Converter", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Local YouTube → audio / video downloader",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        if (initState is App.InitState.Ready) {
            UrlSection(vm)
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))
            SearchSection(vm)
            Spacer(Modifier.height(24.dp))
        } else {
            EngineStatusCard(initState)
        }
    }

    vm.pending?.let { pending ->
        ConfirmDownloadDialog(
            pending = pending,
            onConfirm = vm::confirmDownload,
            onDismiss = vm::dismissPending
        )
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored on purpose */ }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun DownloadStatusBar(vm: MainViewModel, playlistVm: PlaylistViewModel) {
    val state by vm.state.collectAsState()
    if (state is DownloadUiState.Idle) return

    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when (val s = state) {
                DownloadUiState.Idle -> Unit
                is DownloadUiState.Running -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.title ?: "Downloading…",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = vm::cancel) { Text("Cancel") }
                    }
                    if (s.percent <= 0f) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { (s.percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val eta = if (s.etaSeconds >= 0) " • ETA ${s.etaSeconds}s" else ""
                    Text(
                        "${s.percent.toInt()}%$eta",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is DownloadUiState.Success -> {
                    var showAddDialog by remember { mutableStateOf(false) }
                    val playlists by playlistVm.playlists.collectAsState()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Saved ✓ — ${s.displayName}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = vm::dismissStatus) { Text("✕") }
                    }
                    val isAudio = !s.displayName.lowercase().endsWith(".mp4")
                    if (playlists.isNotEmpty() && isAudio) {
                        TextButton(
                            onClick = { showAddDialog = true },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                        ) { Text("Add to playlist") }
                    }
                    if (showAddDialog) {
                        AddToPlaylistDialog(
                            playlists = playlists,
                            onPick = { playlistId ->
                                playlistVm.addSongByDisplayName(playlistId, s.displayName)
                                showAddDialog = false
                            },
                            onDismiss = { showAddDialog = false }
                        )
                    }
                }

                is DownloadUiState.Error -> StatusRow(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                    onDismiss = vm::dismissStatus
                )
            }
        }
    }
}

@Composable
private fun StatusRow(text: String, color: Color, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onDismiss) { Text("✕") }
    }
}

@Composable
private fun UrlSection(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    Text("Paste a link", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.url,
        onValueChange = vm::onUrlChange,
        label = { Text("YouTube URL") },
        placeholder = { Text("https://youtube.com/watch?v=…") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { clipboard.getText()?.text?.let(vm::onUrlChange) }) {
                    Text("Paste")
                }
                if (vm.url.isNotEmpty()) {
                    IconButton(onClick = { vm.onUrlChange("") }) { Text("✕") }
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    FormatSelector(selected = settings.urlFormat, onSelect = vm::setUrlFormat)
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { keyboard?.hide(); vm.loadUrl() },
        enabled = !vm.urlLoading && vm.url.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (vm.urlLoading) "Finding…" else "Find video")
    }
    vm.urlError?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SearchSection(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val doSearch = { keyboard?.hide(); vm.search() }

    Text("Search", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    FormatSelector(selected = settings.searchFormat, onSelect = vm::setSearchFormat)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.query,
        onValueChange = vm::onQueryChange,
        label = { Text("Search YouTube") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { doSearch() }),
        trailingIcon = { TextButton(onClick = doSearch) { Text("Go") } },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))

    when (val s = vm.searchState) {
        SearchUiState.Idle -> Text(
            "Search YouTube, then tap a result to download it as ${settings.searchFormat.name}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SearchUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Searching…")
        }

        is SearchUiState.Error -> Text(
            s.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )

        is SearchUiState.Results -> {
            if (s.items.isEmpty()) {
                Text("No results", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    s.items.forEach { result ->
                        SearchResultRow(result = result, onClick = { vm.selectSearchResult(result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatSelector(selected: DownloadFormat, onSelect: (DownloadFormat) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DownloadFormat.entries.forEach { format ->
            if (format == selected) {
                Button(onClick = { onSelect(format) }, modifier = Modifier.weight(1f)) {
                    Text(format.name)
                }
            } else {
                OutlinedButton(onClick = { onSelect(format) }, modifier = Modifier.weight(1f)) {
                    Text(format.name)
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: SearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = result.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(120.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildString {
                if (result.uploader.isNotBlank()) append(result.uploader)
                if (result.durationSeconds > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(formatDuration(result.durationSeconds))
                }
            }
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun ConfirmDownloadDialog(
    pending: PendingDownload,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val v = pending.video
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download this video?") },
        text = {
            Column {
                AsyncImage(
                    model = v.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    v.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildString {
                    if (v.uploader.isNotBlank()) append(v.uploader)
                    if (v.durationSeconds > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(formatDuration(v.durationSeconds))
                    }
                }
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Download (${pending.format.name})") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EngineStatusCard(initState: App.InitState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (initState) {
                App.InitState.Initializing -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Starting the on-device yt-dlp engine…")
                }

                App.InitState.Ready -> {
                    Text("✓", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(16.dp))
                    Text("yt-dlp engine ready")
                }

                is App.InitState.Failed -> {
                    Column {
                        Text(
                            "Engine failed to start",
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            initState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
