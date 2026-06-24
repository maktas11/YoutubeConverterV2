package com.maktas.ytconverter

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

/**
 * Initializes the on-device yt-dlp engine once at startup.
 *
 * The youtubedl-android library bundles the yt-dlp binary, a Python runtime and
 * FFmpeg inside the APK and runs them locally — there is no server. On the very
 * first launch it extracts those binaries, which can take a few seconds, so we
 * run init off the main thread and surface the result as Compose state.
 */
class App : Application() {

    /** Current state of the local yt-dlp engine, observed by the UI. */
    var initState: InitState by mutableStateOf(InitState.Initializing)
        private set

    override fun onCreate() {
        super.onCreate()
        // First-launch binary extraction can block for seconds; keep it off the
        // main thread to avoid jank/ANR on cold start.
        Thread {
            initState = try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
                // Aria2c is optional (faster downloads); wire it up in a later phase.
                Log.i(TAG, "youtubedl-android initialized")
                InitState.Ready
            } catch (e: Exception) {
                Log.e(TAG, "failed to initialize youtubedl-android", e)
                InitState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }.start()
    }

    sealed interface InitState {
        data object Initializing : InitState
        data object Ready : InitState
        data class Failed(val message: String) : InitState
    }

    companion object {
        private const val TAG = "App"
    }
}
