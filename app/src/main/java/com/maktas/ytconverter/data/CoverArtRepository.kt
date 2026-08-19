package com.maktas.ytconverter.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

object CoverArtRepository {
    // Bumped every time a cover is saved; AudioArtwork observes it to invalidate its cache.
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> get() = _version

    private fun dir(context: Context) =
        File(context.filesDir, "covers").also { it.mkdirs() }

    // Normalise a title to a safe filename key.
    private fun key(title: String) =
        title.trim().lowercase().replace(Regex("[^a-z0-9]"), "_").take(100)

    fun save(context: Context, title: String, bitmap: Bitmap) {
        File(dir(context), "${key(title)}.jpg")
            .outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        _version.value = System.currentTimeMillis()
    }

    /** Follows a saved cover to a renamed song — covers are keyed by title, so without
     *  this a rename silently drops back to the default artwork. */
    fun rename(context: Context, oldTitle: String, newTitle: String) {
        if (key(oldTitle) == key(newTitle)) return
        val old = File(dir(context), "${key(oldTitle)}.jpg")
        if (!old.exists()) return
        val new = File(dir(context), "${key(newTitle)}.jpg")
        new.delete()
        if (old.renameTo(new)) _version.value = System.currentTimeMillis()
    }

    fun getFile(context: Context, title: String): File? {
        val f = File(dir(context), "${key(title)}.jpg")
        return if (f.exists()) f else null
    }
}
