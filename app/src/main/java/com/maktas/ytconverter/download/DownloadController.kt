package com.maktas.ytconverter.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Observable state for the current (single) download. */
sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data class Running(val percent: Float, val etaSeconds: Long, val title: String?) : DownloadUiState
    data class Success(val displayName: String) : DownloadUiState
    data class Error(val message: String) : DownloadUiState
}

/**
 * Single source of truth for the active download, shared between the foreground
 * [DownloadService] (writer) and the UI (reader). v1 runs one download at a time.
 */
object DownloadController {

    private val _state = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    @Volatile private var processId: String? = null
    @Volatile private var cancelled = false
    @Volatile private var title: String? = null

    val isRunning: Boolean get() = _state.value is DownloadUiState.Running

    /** Whether the most recent run was cancelled (read right after it finishes). */
    val wasCancelled: Boolean get() = cancelled

    fun onStart(id: String, title: String?) {
        processId = id
        cancelled = false
        this.title = title
        _state.value = DownloadUiState.Running(percent = 0f, etaSeconds = -1, title = title)
    }

    fun onProgress(percent: Float, etaSeconds: Long) {
        if (!cancelled) _state.value = DownloadUiState.Running(percent, etaSeconds, title)
    }

    fun onFinished(result: Result<Downloader.Saved>) {
        _state.value = when {
            cancelled -> DownloadUiState.Idle
            result.isSuccess -> DownloadUiState.Success(result.getOrThrow().displayName)
            else -> DownloadUiState.Error(ErrorMapper.friendly(result.exceptionOrNull()?.message))
        }
        processId = null
    }

    /** Cancels the running download — from the in-app button or the notification. */
    fun requestCancel() {
        cancelled = true
        processId?.let { Downloader.cancel(it) }
    }

    /** Dismiss a finished (success/error) status. No-op while a download is running. */
    fun clear() {
        if (_state.value !is DownloadUiState.Running) _state.value = DownloadUiState.Idle
    }
}
