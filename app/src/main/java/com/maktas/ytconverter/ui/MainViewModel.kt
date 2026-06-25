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
import com.maktas.ytconverter.data.DownloadFormat
import com.maktas.ytconverter.data.Settings
import com.maktas.ytconverter.data.SettingsRepository
import com.maktas.ytconverter.data.VideoQuality
import com.maktas.ytconverter.download.DownloadController
import com.maktas.ytconverter.download.DownloadService
import com.maktas.ytconverter.download.DownloadUiState
import com.maktas.ytconverter.download.EngineUpdater
import com.maktas.ytconverter.download.ErrorMapper
import com.maktas.ytconverter.download.SearchResult
import com.maktas.ytconverter.download.Searcher
import com.maktas.ytconverter.download.UpdateChannel
import com.maktas.ytconverter.download.VideoPreview
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

/** UI state for in-app search. */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(val items: List<SearchResult>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

/** A video chosen (via search or URL) awaiting confirmation in the dialog. */
data class PendingDownload(val video: VideoPreview, val format: DownloadFormat)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)

    var url by mutableStateOf("")
        private set

    var query by mutableStateOf("")
        private set

    /** Download progress is published by the foreground service via DownloadController. */
    val state: StateFlow<DownloadUiState> = DownloadController.state

    var searchState: SearchUiState by mutableStateOf(SearchUiState.Idle)
        private set

    /** Persisted user settings; Eagerly so the theme is correct from first frame. */
    val settings: StateFlow<Settings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    var updateState: UpdateUiState by mutableStateOf(UpdateUiState.Idle)
        private set

    /** Set when a video is selected and awaiting confirmation (drives the dialog). */
    var pending: PendingDownload? by mutableStateOf(null)
        private set

    var urlLoading: Boolean by mutableStateOf(false)
        private set

    var urlError: String? by mutableStateOf(null)
        private set

    fun onUrlChange(value: String) {
        url = value
    }

    fun onQueryChange(value: String) {
        query = value
    }

    /** Tapping a search result opens the confirm dialog (no download yet). */
    fun selectSearchResult(result: SearchResult) {
        pending = PendingDownload(
            video = VideoPreview(
                title = result.title,
                uploader = result.uploader,
                durationSeconds = result.durationSeconds,
                thumbnailUrl = result.thumbnailUrl,
                videoUrl = result.videoUrl,
            ),
            format = settings.value.searchFormat,
        )
    }

    /** Fetches the pasted link's video info, then opens the confirm dialog. */
    fun loadUrl() {
        val target = url.trim()
        if (target.isEmpty() || urlLoading) return
        urlLoading = true
        urlError = null
        viewModelScope.launch {
            val result = Searcher.fetchInfo(target)
            urlLoading = false
            result.fold(
                onSuccess = { pending = PendingDownload(it, settings.value.urlFormat) },
                onFailure = { urlError = ErrorMapper.friendly(it.message) },
            )
        }
    }

    /** Confirms the pending selection and starts the download. */
    fun confirmDownload() {
        val p = pending ?: return
        pending = null
        startDownload(p.video.videoUrl, p.format, p.video.title)
    }

    fun dismissPending() {
        pending = null
    }

    private fun startDownload(target: String, format: DownloadFormat, title: String?) {
        if (target.isEmpty() || DownloadController.isRunning) return
        val intent = Intent(getApplication(), DownloadService::class.java).apply {
            action = DownloadService.ACTION_DOWNLOAD
            putExtra(DownloadService.EXTRA_URL, target)
            putExtra(DownloadService.EXTRA_FORMAT, format.name)
            title?.let { putExtra(DownloadService.EXTRA_TITLE, it) }
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun cancel() {
        DownloadController.requestCancel()
    }

    fun dismissStatus() {
        DownloadController.clear()
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty() || searchState is SearchUiState.Loading) return
        searchState = SearchUiState.Loading
        viewModelScope.launch {
            val result = Searcher.search(q)
            searchState = result.fold(
                onSuccess = { SearchUiState.Results(it) },
                onFailure = { SearchUiState.Error(ErrorMapper.friendly(it.message)) },
            )
        }
    }

    // --- Settings writers ---
    fun setUrlFormat(value: DownloadFormat) = launchSetting { settingsRepo.setUrlFormat(value) }
    fun setSearchFormat(value: DownloadFormat) = launchSetting { settingsRepo.setSearchFormat(value) }
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
