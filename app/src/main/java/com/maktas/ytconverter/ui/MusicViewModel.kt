package com.maktas.ytconverter.ui

import android.app.Application
import android.net.Uri
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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
        state = LibraryUiState.Loading
        viewModelScope.launch {
            hiddenUris = settings.hiddenSongs.first()
            allSongs = MusicLibrary.scan(getApplication(), hiddenUris)
            applyView()
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.delete(Uri.parse(song.uri), null, null)
                }
            }
            refresh()
        }
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
