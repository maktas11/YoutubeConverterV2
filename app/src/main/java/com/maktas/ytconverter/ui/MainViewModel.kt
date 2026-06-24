package com.maktas.ytconverter.ui

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maktas.ytconverter.data.AppTheme
import com.maktas.ytconverter.data.AudioFormat
import com.maktas.ytconverter.data.Settings
import com.maktas.ytconverter.data.SettingsRepository
import com.maktas.ytconverter.data.VideoQuality
import com.maktas.ytconverter.download.DownloadController
import com.maktas.ytconverter.download.DownloadService
import com.maktas.ytconverter.download.DownloadUiState
import com.maktas.ytconverter.download.EngineUpdater
import com.maktas.ytconverter.download.UpdateChannel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state for the yt-dlp updater. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Running : UpdateUiState
    data class Done(val message: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)

    var url by mutableStateOf("")
        private set

    /** Download progress is published by the foreground service via DownloadController. */
    val state: StateFlow<DownloadUiState> = DownloadController.state

    /** Persisted user settings; Eagerly so the theme is correct from first frame. */
    val settings: StateFlow<Settings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    var updateState: UpdateUiState by mutableStateOf(UpdateUiState.Idle)
        private set

    fun onUrlChange(value: String) {
        url = value
    }

    fun startDownload(video: Boolean = false) {
        val target = url.trim()
        if (target.isEmpty() || DownloadController.isRunning) return
        val intent = Intent(getApplication(), DownloadService::class.java).apply {
            action = DownloadService.ACTION_DOWNLOAD
            putExtra(DownloadService.EXTRA_URL, target)
            putExtra(DownloadService.EXTRA_VIDEO, video)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun cancel() {
        DownloadController.requestCancel()
    }

    // --- Settings writers ---
    fun setAudioFormat(value: AudioFormat) = launchSetting { settingsRepo.setAudioFormat(value) }
    fun setEmbedThumbnail(value: Boolean) = launchSetting { settingsRepo.setEmbedThumbnail(value) }
    fun setEmbedMetadata(value: Boolean) = launchSetting { settingsRepo.setEmbedMetadata(value) }
    fun setTheme(value: AppTheme) = launchSetting { settingsRepo.setTheme(value) }
    fun setUpdateChannel(value: UpdateChannel) = launchSetting { settingsRepo.setUpdateChannel(value) }
    fun setVideoQuality(value: VideoQuality) = launchSetting { settingsRepo.setVideoQuality(value) }

    private inline fun launchSetting(crossinline block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun updateEngine() {
        if (updateState is UpdateUiState.Running) return
        updateState = UpdateUiState.Running
        viewModelScope.launch {
            val result = EngineUpdater.update(getApplication(), settings.value.updateChannel)
            updateState = result.fold(
                onSuccess = { UpdateUiState.Done(it) },
                onFailure = { UpdateUiState.Error(it.message ?: "Update failed") },
            )
        }
    }
}
