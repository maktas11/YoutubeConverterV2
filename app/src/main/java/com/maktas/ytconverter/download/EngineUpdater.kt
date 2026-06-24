package com.maktas.ytconverter.download

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Update channels we expose in the UI (maps to the library's channels). */
enum class UpdateChannel { STABLE, NIGHTLY }

/**
 * Updates the on-device yt-dlp binary by downloading the latest release from
 * GitHub. YouTube breaks extractors often, so the bundled binary goes stale;
 * NIGHTLY is usually the freshest and most likely to fix extraction.
 *
 * (This is Phase 7 functionality, pulled forward because a stale binary blocks
 * all downloads. It will move into the Settings screen later.)
 */
object EngineUpdater {

    suspend fun update(context: Context, channel: UpdateChannel): Result<String> =
        withContext(Dispatchers.IO) {
            val libChannel = when (channel) {
                UpdateChannel.STABLE -> YoutubeDL.UpdateChannel.STABLE
                UpdateChannel.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
            }
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(context, libChannel)
                val message = when (status) {
                    YoutubeDL.UpdateStatus.DONE -> "yt-dlp updated (${channel.name.lowercase()})"
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Already up to date"
                    null -> "Update finished"
                }
                Result.success(message)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
