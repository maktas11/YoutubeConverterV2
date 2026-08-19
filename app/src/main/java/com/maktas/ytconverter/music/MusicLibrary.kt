package com.maktas.ytconverter.music

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.maktas.ytconverter.data.SongEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Scans the device for music via MediaStore (filtered to real songs, not ringtones/alarms). */
object MusicLibrary {

    // Matches a trailing copy-number like " (1)" / " (12)" (1–3 digits, so years like
    // "(2015)" are left alone).
    private val COPY_SUFFIX = Regex("""\s*\(\d{1,3}\)\s*$""")

    private fun cleanTitle(raw: String): String {
        val trimmed = raw.trim()
        val cleaned = trimmed.replace(COPY_SUFFIX, "").trim()
        return cleaned.ifEmpty { trimmed }
    }

    suspend fun scan(
        context: Context,
        hiddenUris: Set<String> = emptySet(),
        edits: Map<String, SongEdit> = emptyMap(),
    ): List<Song> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        // IS_MUSIC excludes ringtones / alarms / notifications / podcasts-as-system, etc.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val songs = mutableListOf<Song>()
        // Skip duplicate songs — same title + artist + length (re-downloads, M4A + MP3 of
        // the same track, etc.). Scan is newest-first, so the newest copy is the one kept.
        val seen = HashSet<String>()
        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                if (uri in hiddenUris) continue

                val durationMs = cursor.getLong(durCol)
                // A name the user typed is taken literally: no copy-number cleanup (so
                // "Remix (2)" survives) and never dropped as a duplicate (so renaming a
                // song can't make it vanish from the list).
                val edit = edits[uri]
                // Filenames like "Song (1)", "Song (2)" leak into the title — strip that
                // trailing copy-number so re-downloads collapse to one clean entry.
                val title = edit?.title ?: cleanTitle(cursor.getString(titleCol) ?: "(unknown title)")
                val artist = edit?.artist
                    ?: cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ?: ""

                val key = "${title.lowercase()}|${artist.lowercase()}|${durationMs / 1000}"
                if (!seen.add(key) && edit == null) continue

                songs += Song(
                    id = id,
                    title = title,
                    artist = artist,
                    durationMs = durationMs,
                    uri = uri,
                    dateAdded = cursor.getLong(dateCol),
                    edited = edit != null,
                )
            }
        }
        songs
    }
}
