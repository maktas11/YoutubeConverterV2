package com.maktas.ytconverter.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maktas.ytconverter.data.SettingsRepository
import com.maktas.ytconverter.download.DownloadController
import com.maktas.ytconverter.download.DownloadUiState
import com.maktas.ytconverter.music.MusicLibrary
import com.maktas.ytconverter.music.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortMode { NEWEST, TITLE }

/** State of the music library tab. */
sealed interface LibraryUiState {
    data object NeedsPermission : LibraryUiState
    data object Loading : LibraryUiState
    data class Loaded(val songs: List<Song>) : LibraryUiState
}

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)
    private var hiddenUris: Set<String> = emptySet()

    var query by mutableStateOf("")
        private set

    var sortMode by mutableStateOf(SortMode.NEWEST)
        private set

    var state: LibraryUiState by mutableStateOf(LibraryUiState.NeedsPermission)
        private set

    private var allSongs: List<Song> = emptyList()

    // Set when contentResolver.delete() needs user consent (scoped storage, API 29+) —
    // the UI launches this via StartIntentSenderForResult and reports back the outcome.
    var pendingDeleteIntentSender: IntentSender? by mutableStateOf(null)
        private set

    private val _errorEvents = Channel<String>(Channel.BUFFERED)
    val errorEvents: Flow<String> = _errorEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            DownloadController.state
                .drop(1)
                .collect { downloadState ->
                    if (downloadState is DownloadUiState.Success && state is LibraryUiState.Loaded) {
                        refresh()
                    }
                }
        }
    }

    fun onQueryChange(value: String) {
        query = value
        applyView()
    }

    fun setSort(mode: SortMode) {
        sortMode = mode
        applyView()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) refresh() else state = LibraryUiState.NeedsPermission
    }

    fun refresh() {
        // Only show the Loading screen on the very first load — once we already have a
        // list on screen, refreshing in place (delete/hide/download-complete) keeps the
        // same LazyColumn mounted so its scroll position isn't lost.
        if (state !is LibraryUiState.Loaded) state = LibraryUiState.Loading
        viewModelScope.launch {
            hiddenUris = settings.hiddenSongs.first()
            allSongs = MusicLibrary.scan(getApplication(), hiddenUris)
            applyView()
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.delete(Uri.parse(song.uri), null, null)
                }
            }
            result.fold(
                onSuccess = { rowsDeleted ->
                    if (rowsDeleted > 0) refresh()
                    else _errorEvents.send("Couldn't delete \"${song.title}\" — it may already be gone.")
                },
                onFailure = { e ->
                    // Scoped storage: deleting media this app doesn't own needs user consent.
                    // Approving the resulting system dialog performs the delete itself.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        pendingDeleteIntentSender = e.userAction.actionIntent.intentSender
                    } else {
                        _errorEvents.send("Couldn't delete \"${song.title}\": ${e.message ?: "unknown error"}")
                    }
                },
            )
        }
    }

    /** Called by the UI after the system delete-consent dialog (from [pendingDeleteIntentSender]) closes. */
    fun onDeletePermissionResult(approved: Boolean) {
        pendingDeleteIntentSender = null
        if (approved) refresh()
    }

    fun hideSong(song: Song) {
        viewModelScope.launch {
            settings.hideUnhideSong(song.uri, hide = true)
            refresh()
        }
    }

    private fun applyView() {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allSongs
        } else {
            allSongs.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        }
        val sorted = when (sortMode) {
            SortMode.NEWEST -> filtered.sortedByDescending { it.dateAdded }
            SortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
        }
        state = LibraryUiState.Loaded(sorted)
    }
}
