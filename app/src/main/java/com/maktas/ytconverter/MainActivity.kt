package com.maktas.ytconverter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.maktas.ytconverter.data.AppTheme
import com.maktas.ytconverter.data.DownloadFormat
import com.maktas.ytconverter.download.DownloadUiState
import com.maktas.ytconverter.download.SearchResult
import com.maktas.ytconverter.ui.MainViewModel
import com.maktas.ytconverter.ui.PendingDownload
import com.maktas.ytconverter.ui.SearchUiState
import com.maktas.ytconverter.ui.SettingsScreen
import com.maktas.ytconverter.ui.theme.YoutubeConverterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        bottomBar = { if (!showSettings) DownloadStatusBar(vm) }
                    ) { innerPadding ->
                        if (showSettings) {
                            BackHandler { showSettings = false }
                            SettingsScreen(
                                vm = vm,
                                onBack = { showSettings = false },
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else {
                            HomeScreen(
                                initState = app.initState,
                                vm = vm,
                                onOpenSettings = { showSettings = true },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
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
    // Android 13+ gates notifications behind a runtime permission. Downloads work
    // regardless; this just lets the progress notification show.
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

/**
 * Sticky bottom bar showing the current download — visible from anywhere (URL or
 * search section) regardless of scroll position. Hidden when nothing is active.
 */
@Composable
private fun DownloadStatusBar(vm: MainViewModel) {
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
                .navigationBarsPadding()
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

                is DownloadUiState.Success -> StatusRow(
                    text = "Saved to Download/ — ${s.displayName}",
                    color = MaterialTheme.colorScheme.onSurface,
                    onDismiss = vm::dismissStatus
                )

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
        onClick = vm::loadUrl,
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
        keyboardActions = KeyboardActions(onSearch = { vm.search() }),
        trailingIcon = { TextButton(onClick = vm::search) { Text("Go") } },
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

