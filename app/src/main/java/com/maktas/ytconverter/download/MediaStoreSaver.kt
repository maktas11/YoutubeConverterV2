package com.maktas.ytconverter.download

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Publishes a finished file into the public Download/ folder via MediaStore.
 * Works under Scoped Storage (API 29+) with **no storage permission** because an
 * app may always insert its own files into MediaStore.Downloads.
 */
object MediaStoreSaver {

    /** Copies [file] into Download/ and returns the (possibly de-duplicated) name. */
    fun saveToDownloads(context: Context, file: File): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            mimeTypeFor(file.extension)?.let { put(MediaStore.Downloads.MIME_TYPE, it) }
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            // Mark in-progress so other apps don't read a half-written file.
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, pending)
            ?: error("MediaStore returned no URI for ${file.name}")

        resolver.openOutputStream(uri).use { out ->
            requireNotNull(out) { "Could not open output stream for ${file.name}" }
            file.inputStream().use { input -> input.copyTo(out) }
        }

        val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)

        // MediaStore may have de-duplicated the name (e.g. "title (1).m4a").
        return resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else file.name }
            ?: file.name
    }

    private fun mimeTypeFor(ext: String): String? = when (ext.lowercase()) {
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "opus" -> "audio/opus"
        "ogg" -> "audio/ogg"
        "webm" -> "audio/webm"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "mp4" -> "video/mp4"
        else -> null
    }
}
