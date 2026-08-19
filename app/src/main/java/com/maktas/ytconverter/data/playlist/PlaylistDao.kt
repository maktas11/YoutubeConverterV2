package com.maktas.ytconverter.data.playlist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query(
        "SELECT p.*, " +
            "(SELECT COUNT(*) FROM playlist_songs s WHERE s.playlistId = p.id) AS songCount " +
            "FROM playlists p ORDER BY p.createdAt DESC"
    )
    fun playlistsWithCounts(): Flow<List<PlaylistWithCount>>

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun songs(playlistId: Long): Flow<List<PlaylistSong>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert
    suspend fun insertSong(song: PlaylistSong)

    @Insert
    suspend fun insertSongs(songs: List<PlaylistSong>)

    @Query("DELETE FROM playlist_songs WHERE id = :songId")
    suspend fun deleteSong(songId: Long)

    /** Playlist rows keep their own copy of the title/artist, so a rename has to reach
     *  every playlist the song is in — otherwise it shows two names in two places. */
    @Query("UPDATE playlist_songs SET title = :title, artist = :artist WHERE uri = :uri")
    suspend fun updateSongDetails(uri: String, title: String, artist: String)

    @Update
    suspend fun updateSongs(songs: List<PlaylistSong>)
}
