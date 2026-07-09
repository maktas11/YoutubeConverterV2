package com.maktas.ytconverter.data.playlist

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_songs",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("playlistId")],
)
data class PlaylistSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val position: Int,
)

/** A playlist plus its song count, for the playlists list. */
data class PlaylistWithCount(
    @Embedded val playlist: Playlist,
    val songCount: Int,
)
