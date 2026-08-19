package com.maktas.ytconverter.music

/** A playable audio track from the device's MediaStore. */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    /** content:// URI string for playback and artwork. */
    val uri: String,
    /** Seconds since epoch when the file was added — used for "newest first". */
    val dateAdded: Long,
    /** True when the user renamed this song, so its title/artist came from a saved edit
     *  rather than the file's own metadata. */
    val edited: Boolean = false,
)
