package com.maktas.ytconverter.data.playlist

import android.content.Context
import com.maktas.ytconverter.music.Song
import kotlinx.coroutines.flow.Flow

/** Higher-level playlist operations on top of [PlaylistDao]. */
class PlaylistRepository(context: Context) {

    private val dao = AppDatabase.get(context).playlistDao()

    fun playlists(): Flow<List<PlaylistWithCount>> = dao.playlistsWithCounts()

    fun songs(playlistId: Long): Flow<List<PlaylistSong>> = dao.songs(playlistId)

    suspend fun create(name: String): Long = dao.insertPlaylist(Playlist(name = name))

    suspend fun rename(id: Long, name: String) = dao.renamePlaylist(id, name)

    suspend fun delete(id: Long) = dao.deletePlaylist(id)

    suspend fun addSong(playlistId: Long, song: Song) {
        val nextPosition = dao.maxPosition(playlistId) + 1
        dao.insertSong(
            PlaylistSong(
                playlistId = playlistId,
                uri = song.uri,
                title = song.title,
                artist = song.artist,
                durationMs = song.durationMs,
                position = nextPosition,
            )
        )
    }

    suspend fun addSongs(playlistId: Long, songs: List<Song>) {
        var nextPosition = dao.maxPosition(playlistId) + 1
        dao.insertSongs(songs.map { song ->
            PlaylistSong(
                playlistId = playlistId,
                uri = song.uri,
                title = song.title,
                artist = song.artist,
                durationMs = song.durationMs,
                position = nextPosition++,
            )
        })
    }

    suspend fun removeSong(playlistSongId: Long) = dao.deleteSong(playlistSongId)

    /** Persists a new order; positions are reassigned to match the list order. */
    suspend fun reorder(songs: List<PlaylistSong>) {
        dao.updateSongs(songs.mapIndexed { index, song -> song.copy(position = index) })
    }
}
