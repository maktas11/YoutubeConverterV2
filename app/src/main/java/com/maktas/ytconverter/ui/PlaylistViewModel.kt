package com.maktas.ytconverter.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maktas.ytconverter.data.SettingsRepository
import com.maktas.ytconverter.data.playlist.PlaylistRepository
import com.maktas.ytconverter.data.playlist.PlaylistSong
import com.maktas.ytconverter.data.playlist.PlaylistWithCount
import com.maktas.ytconverter.music.MusicLibrary
import com.maktas.ytconverter.music.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PlaylistRepository(app)
    private val settings = SettingsRepository(app)

    /** Library scan with the user's renames applied, so a song shows the same name here
     *  as it does in the library list. */
    private suspend fun scanLibrary(): List<Song> =
        MusicLibrary.scan(getApplication(), edits = settings.songEdits.first())

    val playlists: StateFlow<List<PlaylistWithCount>> = repo.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- open playlist (detail) ---
    var openId: Long? by mutableStateOf(null)
        private set
    var openName: String by mutableStateOf("")
        private set
    var openSongs: List<PlaylistSong> by mutableStateOf(emptyList())
        private set

    /** URIs that currently exist in the library; a playlist song not in here is "missing". */
    var availableUris: Set<String> by mutableStateOf(emptySet())
        private set

    private var songsJob: Job? = null

    // --- song picker (for mass-add inside a playlist) ---
    var showSongPicker by mutableStateOf(false)
        private set
    var songPickerAll: List<Song> by mutableStateOf(emptyList())
        private set
    var songPickerQuery by mutableStateOf("")
        private set

    fun create(name: String) {
        val n = name.trim()
        if (n.isNotEmpty()) viewModelScope.launch { repo.create(n) }
    }

    fun rename(id: Long, name: String) {
        val n = name.trim()
        if (n.isNotEmpty()) viewModelScope.launch { repo.rename(id, n) }
    }

    fun delete(id: Long) {
        if (openId == id) close()
        viewModelScope.launch { repo.delete(id) }
    }

    fun open(playlist: PlaylistWithCount) {
        if (openId == playlist.playlist.id) return
        openId = playlist.playlist.id
        openName = playlist.playlist.name
        songsJob?.cancel()
        songsJob = viewModelScope.launch {
            repo.songs(playlist.playlist.id).collect { openSongs = it }
        }
        viewModelScope.launch {
            availableUris = scanLibrary().mapTo(HashSet()) { it.uri }
        }
    }

    fun close() {
        openId = null
        openName = ""
        songsJob?.cancel()
        openSongs = emptyList()
    }

    fun addSong(playlistId: Long, song: Song) {
        viewModelScope.launch { repo.addSong(playlistId, song) }
    }

    fun removeSong(playlistSongId: Long) {
        viewModelScope.launch { repo.removeSong(playlistSongId) }
    }

    fun reorder(songs: List<PlaylistSong>) {
        openSongs = songs
        viewModelScope.launch { repo.reorder(songs) }
    }

    // --- song picker ---

    fun openSongPicker() {
        songPickerQuery = ""
        showSongPicker = true
        viewModelScope.launch {
            songPickerAll = scanLibrary()
        }
    }

    fun closeSongPicker() {
        showSongPicker = false
        songPickerAll = emptyList()
    }

    fun onSongPickerQueryChange(q: String) { songPickerQuery = q }

    fun confirmAddSongs(playlistId: Long, songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch { repo.addSongs(playlistId, songs) }
        showSongPicker = false
        songPickerAll = emptyList()
    }

    /**
     * Finds a song in the library whose title matches [displayName] (stripped of extension)
     * and adds it to [playlistId]. Used by the "Add to playlist" button in the download status bar.
     */
    fun addSongByDisplayName(playlistId: Long, displayName: String) {
        viewModelScope.launch {
            val songs = scanLibrary()
            val title = displayName.substringBeforeLast('.')
            val song = songs.firstOrNull { it.title.equals(title, ignoreCase = true) }
                ?: songs.firstOrNull { it.title.lowercase().contains(title.lowercase()) }
            if (song != null) repo.addSong(playlistId, song)
        }
    }
}
