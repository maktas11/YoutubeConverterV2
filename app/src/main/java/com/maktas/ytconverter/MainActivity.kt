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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maktas.ytconverter.data.AppTheme
import com.maktas.ytconverter.download.DownloadUiState
import com.maktas.ytconverter.ui.MainViewModel
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
                    Scaffold { innerPadding ->
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
        Spacer(Modifier.height(32.dp))

        // Only let the user download once the on-device engine has loaded.
        if (initState is App.InitState.Ready) {
            DownloadSection(vm)
        } else {
            EngineStatusCard(initState)
        }
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

@Composable
private fun DownloadSection(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val running = state is DownloadUiState.Running
    val canDownload = !running && vm.url.isNotBlank()

    OutlinedTextField(
        value = vm.url,
        onValueChange = vm::onUrlChange,
        label = { Text("YouTube URL") },
        placeholder = { Text("https://youtube.com/watch?v=…") },
        singleLine = true,
        enabled = !running,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { vm.startDownload(video = false) },
        enabled = canDownload,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Download audio")
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { vm.startDownload(video = true) },
        enabled = canDownload,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Download video (MP4)")
    }

    Spacer(Modifier.height(24.dp))

    when (val s = state) {
        DownloadUiState.Idle -> Unit
        is DownloadUiState.Running -> RunningCard(s, onCancel = vm::cancel)
        is DownloadUiState.Success -> ResultCard(
            title = "Saved to Download/",
            detail = s.displayName,
            isError = false
        )
        is DownloadUiState.Error -> ResultCard(
            title = "Download failed",
            detail = s.message,
            isError = true
        )
    }
}

@Composable
private fun RunningCard(state: DownloadUiState.Running, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // yt-dlp reports percent 0..100; show indeterminate until it ticks up.
            if (state.percent <= 0f) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { (state.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(12.dp))
            val eta = if (state.etaSeconds >= 0) " • ETA ${state.etaSeconds}s" else ""
            Text("Downloading… ${state.percent.toInt()}%$eta")
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun ResultCard(title: String, detail: String, isError: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
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

@Preview(showBackground = true)
@Composable
private fun ResultCardPreview() {
    YoutubeConverterTheme {
        ResultCard(title = "Saved to Download/", detail = "Some Song.m4a", isError = false)
    }
}
