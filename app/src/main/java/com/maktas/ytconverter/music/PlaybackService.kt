package com.maktas.ytconverter.music

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.maktas.ytconverter.MainActivity
import com.maktas.ytconverter.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Hosts the ExoPlayer + MediaSession. As a [MediaSessionService] it gives us
 * background playback, the media notification, and lock-screen / Bluetooth /
 * headset controls for free. The UI talks to it via a MediaController.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer = player

        // Keep PlaybackQueueState in sync so the UI can show actual play order.
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED,
                    )
                ) {
                    updateQueueState(player as ExoPlayer)
                }
            }
        })

        // When PlaybackViewModel requests a reshuffle, generate a fresh random order.
        serviceScope.launch {
            ReshuffleChannel.trigger.collect {
                val p = exoPlayer ?: return@collect
                val count = p.mediaItemCount
                if (count > 0) {
                    p.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(count))
                    updateQueueState(p)
                }
            }
        }

        // Restore last playback state so the user resumes where they left off.
        serviceScope.launch {
            val saved = SettingsRepository(this@PlaybackService).loadPlaybackState()
                ?: return@launch
            val items = parseQueueJson(saved.queueJson)
            if (items.isEmpty()) return@launch

            val startIndex = saved.index.coerceIn(0, items.size - 1)
            player.setMediaItems(items, startIndex, saved.positionMs)
            player.repeatMode = saved.repeatMode
            // Smart shuffle: re-enable; pure random doesn't touch shuffleModeEnabled.
            if (saved.shuffleEnabled && !saved.pureRandom) {
                player.shuffleModeEnabled = true
            }
            player.prepare()
            // Do NOT call player.play() — restore to paused so user decides when to continue.
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()
    }

    private fun parseQueueJson(json: String): List<MediaItem> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.getJSONObject(i)
            val uriStr = obj.optString("uri").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            MediaItem.Builder()
                .setUri(uriStr)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(obj.optString("title").takeIf { it.isNotEmpty() })
                        .setArtist(obj.optString("artist").takeIf { it.isNotEmpty() })
                        .setArtworkUri(Uri.parse(uriStr))
                        .build()
                )
                .build()
        }
    }.getOrElse { emptyList() }

    private fun updateQueueState(player: ExoPlayer) {
        val count = player.mediaItemCount
        if (count == 0) {
            PlaybackQueueState.clear()
            return
        }
        val currentMediaIndex = player.currentMediaItemIndex

        // When shuffle is on, use ExoPlayer's internal ShuffleOrder to get the real play sequence.
        val playOrder: List<Int> = if (player.shuffleModeEnabled) {
            val shuffleOrder = player.shuffleOrder
            val result = mutableListOf<Int>()
            var idx = shuffleOrder.firstIndex
            var safety = 0
            while (idx != C.INDEX_UNSET && safety < count) {
                result.add(idx)
                idx = shuffleOrder.getNextIndex(idx)
                safety++
            }
            result.ifEmpty { (0 until count).toList() }
        } else {
            (0 until count).toList()
        }

        val items = playOrder.map { i ->
            val item = player.getMediaItemAt(i)
            QueueItem(
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                artworkUri = item.mediaMetadata.artworkUri?.toString(),
            )
        }

        PlaybackQueueState.update(items, currentPosition = playOrder.indexOf(currentMediaIndex))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        PlaybackQueueState.clear()
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }
}
