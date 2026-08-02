package com.maktas.ytconverter.download

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * Resolves a SAF tree URI (picked via ACTION_OPEN_DOCUMENT_TREE in Settings) to a save
 * location for a finished download, publishing it so it shows up in the library.
 */
object DownloadLocation {

    /** Copies [file] into the folder [treeUriString] points to and returns the saved name. */
    fun saveToCustomFolder(context: Context, treeUriString: String, file: File): String {
        val treeUri = Uri.parse(treeUriString)
        val realPath = resolveRealPath(treeUri)
        if (realPath != null) {
            realPath.mkdirs()
            val dest = uniqueDestination(realPath, file.name)
            file.copyTo(dest, overwrite = false)
            // Real path on primary storage — trigger an immediate scan so it shows up in
            // the library right away, same as the default Downloads-folder path does.
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
            return dest.name
        }

        // Not on primary storage (e.g. a removable SD card) — fall back to the slower but
        // universally-correct system document API. Uses android.provider.DocumentsContract
        // directly (framework API, no extra dependency) rather than the AndroidX DocumentFile
        // wrapper. May take longer to appear in the library since it relies on the system's
        // own background media scan instead of an explicit one.
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        val mime = mimeTypeFor(file.extension) ?: "application/octet-stream"
        val newDocUri = DocumentsContract.createDocument(context.contentResolver, parentDocUri, mime, file.name)
            ?: throw IllegalStateException("Couldn't create the file in the selected folder.")
        context.contentResolver.openOutputStream(newDocUri)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
        } ?: throw IllegalStateException("Couldn't write to the selected folder.")
        return file.name
    }

    /** A short display label for the folder, e.g. "Download/Music", for the Settings row. */
    fun displayLabel(treeUriString: String): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(treeUriString)) }.getOrNull()
            ?: return "Custom folder"
        val parts = docId.split(":", limit = 2)
        return if (parts.size == 2) parts[1].ifBlank { parts[0] } else docId
    }

    // "content://.../tree/primary:Download/Music" -> external storage root + "Download/Music".
    // Only primary (internal) storage resolves this way; other volumes return null.
    private fun resolveRealPath(treeUri: Uri): File? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val parts = docId.split(":", limit = 2)
        if (parts.size != 2 || !parts[0].equals("primary", ignoreCase = true)) return null
        return File(Environment.getExternalStorageDirectory(), parts[1])
    }

    private fun uniqueDestination(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n)$ext")
            n++
        }
        return candidate
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
