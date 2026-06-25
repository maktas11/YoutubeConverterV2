package com.maktas.ytconverter

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maktas.ytconverter.data.SettingsRepository
import com.maktas.ytconverter.download.EngineUpdater
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Initializes the on-device yt-dlp engine once at startup (off the main thread,
 * since first-launch binary extraction can take seconds) and, once ready, does a
 * best-effort silent yt-dlp refresh at most once per day (build plan §7).
 */
class App : Application() {

    var initState: InitState by mutableStateOf(InitState.Initializing)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            initState = try {
                YoutubeDL.getInstance().init(this@App)
                FFmpeg.getInstance().init(this@App)
                Log.i(TAG, "youtubedl-android initialized")
                InitState.Ready
            } catch (e: Exception) {
                Log.e(TAG, "failed to initialize youtubedl-android", e)
                InitState.Failed(e.message ?: e.javaClass.simpleName)
            }
            if (initState is InitState.Ready) maybeAutoUpdate()
        }
    }

    /** Silent launch-time yt-dlp update, throttled to once per day. Never blocks the UI. */
    private suspend fun maybeAutoUpdate() {
        try {
            val repo = SettingsRepository(this)
            val now = System.currentTimeMillis()
            if (now - repo.lastUpdateCheck() < DAY_MS) return
            EngineUpdater.update(this, repo.settings.first().updateChannel) // result ignored
            repo.setLastUpdateCheck(now)
        } catch (e: Exception) {
            Log.w(TAG, "auto-update check failed", e)
        }
    }

    sealed interface InitState {
        data object Initializing : InitState
        data object Ready : InitState
        data class Failed(val message: String) : InitState
    }

    companion object {
        private const val TAG = "App"
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
