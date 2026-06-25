package com.maktas.ytconverter.download

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One YouTube search hit. */
data class SearchResult(
    val id: String,
    val title: String,
    val uploader: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val videoUrl: String,
)

/** Minimal video info shown in the confirm dialog (from a search hit or a pasted URL). */
data class VideoPreview(
    val title: String,
    val uploader: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val videoUrl: String,
)

/**
 * In-app search via yt-dlp's built-in `ytsearch` — no YouTube Data API, no key.
 * `--flat-playlist` keeps it fast (it lists entries without fully extracting each
 * video); `--dump-json` prints one JSON object per line for us to parse.
 */
object Searcher {

    private const val COUNT = 15

    suspend fun search(query: String): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest("ytsearch$COUNT:$query").apply {
                addOption("--flat-playlist")
                addOption("--dump-json")
                addOption("--no-warnings")
            }
            val response = YoutubeDL.getInstance().execute(request)
            val results = response.out
                .lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("{") }
                .mapNotNull(::parseEntry)
                .toList()
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Fetch one video's info (for the URL confirm dialog). Does a full extraction. */
    suspend fun fetchInfo(url: String): Result<VideoPreview> = withContext(Dispatchers.IO) {
        try {
            val info = YoutubeDL.getInstance().getInfo(url)
            val id = info.id
            Result.success(
                VideoPreview(
                    title = info.title ?: "(untitled)",
                    uploader = info.uploader ?: "",
                    durationSeconds = info.duration.toLong(),
                    thumbnailUrl = info.thumbnail
                        ?: id?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
                        ?: "",
                    videoUrl = info.webpageUrl ?: url,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseEntry(line: String): SearchResult? = runCatching {
        val o = JSONObject(line)
        val id = o.optString("id")
        if (id.isBlank()) return null
        SearchResult(
            id = id,
            title = o.optString("title").ifBlank { "(untitled)" },
            uploader = o.optString("channel").ifBlank { o.optString("uploader") },
            durationSeconds = o.optDouble("duration", 0.0).toLong(),
            // Built from the id so we don't depend on the JSON carrying thumbnails.
            thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=$id",
        )
    }.getOrNull()
}
