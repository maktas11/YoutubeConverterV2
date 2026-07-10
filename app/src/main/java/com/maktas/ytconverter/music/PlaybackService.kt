package com.maktas.ytconverter.music

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.maktas.ytconverter.MainActivity
import com.maktas.ytconverter.data.CoverArtRepository
import com.maktas.ytconverter.data.SettingsRepository
import com.maktas.ytconverter.widget.PlayerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Hosts the ExoPlayer + MediaSession. As a [MediaSessionService] it gives us
 * background playback, the media notification, and lock-screen / Bluetooth /
 * headset controls for free. The UI talks to it via a MediaController.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.maktas.ytconverter.TOGGLE_PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "com.maktas.ytconverter.SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "com.maktas.ytconverter.SKIP_PREVIOUS"
    }

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

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PlaybackQueueState.updateIsPlaying(isPlaying)
                serviceScope.launch { updateWidget() }
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
            if (saved.shuffleEnabled && !saved.pureRandom) {
                player.shuffleModeEnabled = true
            }
            player.prepare()
        }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setBitmapLoader(LibraryArtworkBitmapLoader(this))
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = exoPlayer
        if (player != null) {
            when (intent?.action) {
                ACTION_TOGGLE_PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
                ACTION_SKIP_NEXT -> player.seekToNext()
                ACTION_SKIP_PREVIOUS -> player.seekToPrevious()
            }
        }
        return super.onStartCommand(intent, flags, startId)
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
            serviceScope.launch { updateWidget() }
            return
        }
        val currentMediaIndex = player.currentMediaItemIndex

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

        PlaybackQueueState.update(
            items,
            currentPosition = playOrder.indexOf(currentMediaIndex),
            isPlaying = player.isPlaying,
        )
        serviceScope.launch { updateWidget() }
    }

    private suspend fun updateWidget() {
        val manager = GlanceAppWidgetManager(this@PlaybackService)
        val ids = manager.getGlanceIds(PlayerWidget::class.java)
        for (id in ids) PlayerWidget().update(this@PlaybackService, id)
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

/**
 * Supplies artwork for the system media notification / lock-screen controls.
 * Mirrors [com.maktas.ytconverter.ui.AudioArtwork]'s own resolution order: a custom
 * cover saved via [CoverArtRepository] keyed by title, else the audio file's embedded
 * thumbnail. The default Media3 loader can't do either — it only decodes [MediaMetadata
 * .artworkUri] as a literal image file, which fails silently for an audio file URI.
 */
private class LibraryArtworkBitmapLoader(private val context: Context) : BitmapLoader {
    private val executor = Executors.newSingleThreadExecutor()

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        Futures.submit(
            Callable {
                BitmapFactory.decodeByteArray(data, 0, data.size)
                    ?: throw IOException("Could not decode artwork bytes")
            },
            executor
        )

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        Futures.submit(
            Callable { loadThumbnail(uri) ?: throw IOException("Could not load thumbnail for $uri") },
            executor
        )

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val artworkData = metadata.artworkData
        val artworkUri = metadata.artworkUri
        if (artworkData == null && artworkUri == null) return null
        return Futures.submit(
            Callable {
                val title = metadata.title?.toString()
                val customCover = title
                    ?.let { CoverArtRepository.getFile(context, it) }
                    ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                customCover
                    ?: artworkUri?.let { loadThumbnail(it) }
                    ?: artworkData?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    ?: throw IOException("No artwork for $title")
            },
            executor
        )
    }

    private fun loadThumbnail(uri: Uri): Bitmap? = runCatching {
        context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
    }.getOrNull()
}
