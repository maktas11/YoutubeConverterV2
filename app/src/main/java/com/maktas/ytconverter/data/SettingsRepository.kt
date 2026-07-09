package com.maktas.ytconverter.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

/** How shuffle behaves. SMART = each song once before repeating; PURE_RANDOM = fully random. */
enum class ShuffleStyle { SMART, PURE_RANDOM }

/** All persisted user settings. The URL and Search sections each keep their own format. */
data class Settings(
    val urlFormat: DownloadFormat = DownloadFormat.M4A,
    val searchFormat: DownloadFormat = DownloadFormat.M4A,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val theme: AppTheme = AppTheme.SYSTEM,
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val videoQuality: VideoQuality = VideoQuality.BEST,
    val shuffleStyle: ShuffleStyle = ShuffleStyle.SMART,
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
        val SHUFFLE_STYLE = stringPreferencesKey("shuffle_style")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val HIDDEN_SONGS = stringSetPreferencesKey("hidden_songs")
        // Playback state restore
        val PB_QUEUE_JSON = stringPreferencesKey("pb_queue_json")
        val PB_QUEUE_INDEX = intPreferencesKey("pb_queue_index")
        val PB_POSITION_MS = longPreferencesKey("pb_position_ms")
        val PB_SHUFFLE_ENABLED = booleanPreferencesKey("pb_shuffle_enabled")
        val PB_PURE_RANDOM = booleanPreferencesKey("pb_pure_random")
        val PB_REPEAT_MODE = intPreferencesKey("pb_repeat_mode")
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
        shuffleStyle = enumOr(this[Keys.SHUFFLE_STYLE], ShuffleStyle.SMART),
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
    suspend fun setShuffleStyle(value: ShuffleStyle) = put(Keys.SHUFFLE_STYLE, value.name)

    suspend fun lastUpdateCheck(): Long = context.dataStore.data.first()[Keys.LAST_UPDATE_CHECK] ?: 0L
    suspend fun setLastUpdateCheck(value: Long) =
        context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK] = value }

    val hiddenSongs: Flow<Set<String>> = context.dataStore.data.map { it[Keys.HIDDEN_SONGS] ?: emptySet() }

    suspend fun hideUnhideSong(uri: String, hide: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HIDDEN_SONGS] ?: emptySet()
            prefs[Keys.HIDDEN_SONGS] = if (hide) current + uri else current - uri
        }
    }

    suspend fun savePlaybackState(state: SavedPlaybackState) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PB_QUEUE_JSON] = state.queueJson
            prefs[Keys.PB_QUEUE_INDEX] = state.index
            prefs[Keys.PB_POSITION_MS] = state.positionMs
            prefs[Keys.PB_SHUFFLE_ENABLED] = state.shuffleEnabled
            prefs[Keys.PB_PURE_RANDOM] = state.pureRandom
            prefs[Keys.PB_REPEAT_MODE] = state.repeatMode
        }
    }

    suspend fun loadPlaybackState(): SavedPlaybackState? {
        val prefs = context.dataStore.data.first()
        val queueJson = prefs[Keys.PB_QUEUE_JSON]?.takeIf { it.isNotEmpty() } ?: return null
        return SavedPlaybackState(
            queueJson = queueJson,
            index = prefs[Keys.PB_QUEUE_INDEX] ?: 0,
            positionMs = prefs[Keys.PB_POSITION_MS] ?: 0L,
            shuffleEnabled = prefs[Keys.PB_SHUFFLE_ENABLED] ?: false,
            pureRandom = prefs[Keys.PB_PURE_RANDOM] ?: false,
            repeatMode = prefs[Keys.PB_REPEAT_MODE] ?: 0,
        )
    }

    private suspend fun put(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}

/** Snapshot of playback state persisted across process restarts. */
data class SavedPlaybackState(
    val queueJson: String,
    val index: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val pureRandom: Boolean,
    /** 0 = off, 1 = all, 2 = one */
    val repeatMode: Int,
)
