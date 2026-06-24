package com.maktas.ytconverter.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maktas.ytconverter.data.AppTheme
import com.maktas.ytconverter.data.AudioFormat
import com.maktas.ytconverter.data.VideoQuality
import com.maktas.ytconverter.download.UpdateChannel

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsState()
    val update = vm.updateState

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        SectionTitle("Audio format")
        RadioRow("M4A — lossless, instant", settings.audioFormat == AudioFormat.M4A) {
            vm.setAudioFormat(AudioFormat.M4A)
        }
        RadioRow("MP3 — re-encoded, max compatibility", settings.audioFormat == AudioFormat.MP3) {
            vm.setAudioFormat(AudioFormat.MP3)
        }

        SectionDivider()

        SectionTitle("Video (MP4) quality")
        RadioRow("Best quality", settings.videoQuality == VideoQuality.BEST) {
            vm.setVideoQuality(VideoQuality.BEST)
        }
        RadioRow("1080p", settings.videoQuality == VideoQuality.P1080) {
            vm.setVideoQuality(VideoQuality.P1080)
        }
        RadioRow("720p", settings.videoQuality == VideoQuality.P720) {
            vm.setVideoQuality(VideoQuality.P720)
        }

        SectionDivider()

        SectionTitle("Embeds")
        SwitchRow("Embed thumbnail (cover art)", settings.embedThumbnail, vm::setEmbedThumbnail)
        SwitchRow("Embed metadata (title, artist)", settings.embedMetadata, vm::setEmbedMetadata)

        SectionDivider()

        SectionTitle("Theme")
        RadioRow("Follow system", settings.theme == AppTheme.SYSTEM) { vm.setTheme(AppTheme.SYSTEM) }
        RadioRow("Light", settings.theme == AppTheme.LIGHT) { vm.setTheme(AppTheme.LIGHT) }
        RadioRow("Dark", settings.theme == AppTheme.DARK) { vm.setTheme(AppTheme.DARK) }

        SectionDivider()

        SectionTitle("yt-dlp engine")
        Text(
            "YouTube changes often; update if downloads start failing. " +
                "Nightly is usually the freshest.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        RadioRow("Stable", settings.updateChannel == UpdateChannel.STABLE) {
            vm.setUpdateChannel(UpdateChannel.STABLE)
        }
        RadioRow("Nightly", settings.updateChannel == UpdateChannel.NIGHTLY) {
            vm.setUpdateChannel(UpdateChannel.NIGHTLY)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = vm::updateEngine, enabled = update !is UpdateUiState.Running) {
            Text("Update yt-dlp")
        }
        Spacer(Modifier.height(8.dp))
        when (update) {
            UpdateUiState.Idle -> Unit
            UpdateUiState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Updating yt-dlp…", style = MaterialTheme.typography.bodySmall)
            }

            is UpdateUiState.Done -> Text(
                update.message,
                color = MaterialTheme.colorScheme.primary
            )

            is UpdateUiState.Error -> Text(
                update.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
