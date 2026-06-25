package com.maktas.ytconverter.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.maktas.ytconverter.download.UpdateChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Audio container/codec the downloader uses. M4A = stream copy (lossless/instant). */
enum class AudioFormat { M4A, MP3 }

/** App theme preference. */
enum class AppTheme { SYSTEM, LIGHT, DARK }

/** Max video quality when downloading MP4. */
enum class VideoQuality { BEST, P1080, P720 }

/** User-facing per-download format choice. */
enum class DownloadFormat { M4A, MP3, MP4 }

/** All persisted user settings. The URL and Search sections each keep their own format. */
data class Settings(
    val urlFormat: DownloadFormat = DownloadFormat.M4A,
    val searchFormat: DownloadFormat = DownloadFormat.M4A,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val theme: AppTheme = AppTheme.SYSTEM,
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val videoQuality: VideoQuality = VideoQuality.BEST,
)

// One DataStore per process, keyed on the (application) Context.
private val Context.dataStore by preferencesDataStore(name = "settings")

/** Reads/writes [Settings] via Preferences DataStore. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val URL_FORMAT = stringPreferencesKey("url_format")
        val SEARCH_FORMAT = stringPreferencesKey("search_format")
        val EMBED_THUMBNAIL = booleanPreferencesKey("embed_thumbnail")
        val EMBED_METADATA = booleanPreferencesKey("embed_metadata")
        val THEME = stringPreferencesKey("theme")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = Settings(
        urlFormat = enumOr(this[Keys.URL_FORMAT], DownloadFormat.M4A),
        searchFormat = enumOr(this[Keys.SEARCH_FORMAT], DownloadFormat.M4A),
        embedThumbnail = this[Keys.EMBED_THUMBNAIL] ?: true,
        embedMetadata = this[Keys.EMBED_METADATA] ?: true,
        theme = enumOr(this[Keys.THEME], AppTheme.SYSTEM),
        updateChannel = enumOr(this[Keys.UPDATE_CHANNEL], UpdateChannel.STABLE),
        videoQuality = enumOr(this[Keys.VIDEO_QUALITY], VideoQuality.BEST),
    )

    suspend fun setUrlFormat(value: DownloadFormat) = put(Keys.URL_FORMAT, value.name)
    suspend fun setSearchFormat(value: DownloadFormat) = put(Keys.SEARCH_FORMAT, value.name)
    suspend fun setEmbedThumbnail(value: Boolean) =
        context.dataStore.edit { it[Keys.EMBED_THUMBNAIL] = value }
    suspend fun setEmbedMetadata(value: Boolean) =
        context.dataStore.edit { it[Keys.EMBED_METADATA] = value }
    suspend fun setTheme(value: AppTheme) = put(Keys.THEME, value.name)
    suspend fun setUpdateChannel(value: UpdateChannel) = put(Keys.UPDATE_CHANNEL, value.name)
    suspend fun setVideoQuality(value: VideoQuality) = put(Keys.VIDEO_QUALITY, value.name)

    suspend fun lastUpdateCheck(): Long = context.dataStore.data.first()[Keys.LAST_UPDATE_CHECK] ?: 0L
    suspend fun setLastUpdateCheck(value: Long) =
        context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK] = value }

    private suspend fun put(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
