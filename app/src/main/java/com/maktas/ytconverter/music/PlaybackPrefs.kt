package com.maktas.ytconverter.music

/**
 * Process-wide playback preferences that the [PlaybackService] needs to read
 * synchronously (kept in sync from DataStore by the Application).
 */
object PlaybackPrefs {
    @Volatile
    var pureRandomShuffle: Boolean = false
}
