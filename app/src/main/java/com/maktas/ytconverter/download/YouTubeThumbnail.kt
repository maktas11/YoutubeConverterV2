package com.maktas.ytconverter.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.maktas.ytconverter.data.CoverArtRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches YouTube's own best-available thumbnail for a video and saves it as the song's
 * cover via [CoverArtRepository] — the same override system "Change cover art" uses, so
 * downloads get a proper high-res cover automatically without touching the embedded file
 * (whose own thumbnail, embedded by yt-dlp during download, is often noticeably lower-res).
 */
object YouTubeThumbnail {

    // Largest first. A candidate size that doesn't actually exist for a given video still
    // returns HTTP 200 with a small placeholder image rather than a 404, so a plain
    // dimension check (comfortably below the smallest real size, well above the placeholder)
    // distinguishes a real thumbnail from that placeholder.
    private val SIZES = listOf("maxresdefault", "sddefault", "hqdefault", "mqdefault")
    private const val PLACEHOLDER_THRESHOLD_PX = 200

    suspend fun fetchAndSaveCover(context: Context, videoId: String, songTitle: String): Boolean =
        withContext(Dispatchers.IO) {
            for (size in SIZES) {
                val bitmap = download("https://i.ytimg.com/vi/$videoId/$size.jpg") ?: continue
                if (bitmap.width >= PLACEHOLDER_THRESHOLD_PX && bitmap.height >= PLACEHOLDER_THRESHOLD_PX) {
                    CoverArtRepository.save(context, songTitle, bitmap)
                    return@withContext true
                }
            }
            false
        }

    private fun download(url: String): Bitmap? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.connect()
        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            BitmapFactory.decodeStream(conn.inputStream)
        } else {
            null
        }
    }.getOrNull()
}
