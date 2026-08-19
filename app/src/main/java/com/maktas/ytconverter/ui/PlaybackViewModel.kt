package com.maktas.ytconverter.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.maktas.ytconverter.data.SavedPlaybackState
import com.maktas.ytconverter.data.SettingsRepository
import com.maktas.ytconverter.data.playlist.PlaylistSong
import com.maktas.ytconverter.music.PlaybackPrefs
import com.maktas.ytconverter.music.PlaybackQueueState
import com.maktas.ytconverter.music.PlaybackService
import com.maktas.ytconverter.music.ReshuffleChannel
import com.maktas.ytconverter.music.Song
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** What the mini-player / now-playing UI needs to show. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val artworkUri: String?,
    /** This item's real index in the underlying (unshuffled) player queue, for seeking to it.
     *  Only meaningful for queue-list items; -1 for the plain "current track" display. */
    val mediaItemIndex: Int = -1,
)

enum class RepeatMode { OFF, ALL, ONE }

/**
 * Bridges Media3's [MediaController] (connected to [PlaybackService]) to Compose state.
 */
class PlaybackViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)
    private val controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null

    var nowPlaying: NowPlaying? by mutableStateOf(null)
        private set

    var isPlaying: Boolean by mutableStateOf(false)
        private set

    var shuffleEnabled: Boolean by mutableStateOf(false)
        private set

    var repeatMode: RepeatMode by mutableStateOf(RepeatMode.OFF)
        private set

    /** Queue in actual play order (shuffle-aware), supplied by PlaybackService. */
    var queue: List<NowPlaying> by mutableStateOf(emptyList())
        private set

    /** Index within [queue] that is currently playing. */
    var currentIndex: Int by mutableStateOf(-1)
        private set

    /** True when shuffle is on AND the mode is pure-random (seekTo-based). */
    var isPureRandom: Boolean by mutableStateOf(false)
        private set

    /** ID of the playlist whose songs are currently the active queue (null = not from a playlist). */
    var activePlaylistId: Long? by mutableStateOf(null)
        private set

    private var pureRandom = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState()
            // Persist state when something meaningful changes.
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                )
            ) {
                viewModelScope.launch { saveState() }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Save position when playback pauses so we can restore it accurately.
            if (!isPlaying) viewModelScope.launch { saveState() }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (shuffleEnabled && pureRandom && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                seekToRandom()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && shuffleEnabled && pureRandom) {
                seekToRandom()
                controller?.play()
            }
        }
    }

    init {
        // Collect the actual play-order queue pushed by PlaybackService.
        viewModelScope.launch {
            PlaybackQueueState.state.collect { qs ->
                queue = qs.items.map { NowPlaying(it.title, it.artist, it.artworkUri, it.mediaItemIndex) }
                currentIndex = qs.currentPosition
            }
        }

        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get().also { it.addListener(listener) }
            syncState()
            // Restore the shuffle flag for BOTH modes — Smart shuffle's underlying order is
            // restored service-side (PlaybackService), but the UI's own shuffleEnabled flag
            // (drives the toggle highlight) lives only here and needs restoring separately.
            viewModelScope.launch {
                val saved = repo.loadPlaybackState() ?: return@launch
                if (saved.shuffleEnabled) {
                    shuffleEnabled = true
                    pureRandom = saved.pureRandom
                    isPureRandom = saved.pureRandom
                    if (saved.pureRandom) PlaybackPrefs.pureRandomShuffle = true
                }
            }
        }, ContextCompat.getMainExecutor(app))
    }

    /**
     * Play a playlist's songs as the queue, skipping missing files.
     * [startUri] — if provided, starts at that specific song; otherwise starts at index 0.
     */
    fun playPlaylist(
        songs: List<PlaylistSong>,
        availableUris: Set<String>,
        playlistId: Long? = null,
        startUri: String? = null,
    ) {
        val c = controller ?: return
        val available = songs.filter { it.uri in availableUris }
        val items = available.map { it.toMediaItem() }
        if (items.isEmpty()) return
        activePlaylistId = playlistId
        val startIndex = if (startUri != null) available.indexOfFirst { it.uri == startUri }.coerceAtLeast(0) else 0
        c.setMediaItems(items, startIndex, 0L)
        c.prepare()
        c.play()
    }

    /** Play [songs] as the queue, starting at [startIndex]. */
    fun play(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        activePlaylistId = null
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.play()
    }

    /**
     * Rewrites the title/artist of every queue item pointing at [uri] after a rename, so
     * the now-playing UI, the media notification and the widget update immediately instead
     * of showing the old name until the song is queued again.
     */
    fun updateSongMetadata(uri: String, title: String, artist: String) {
        val c = controller ?: return
        for (i in 0 until c.mediaItemCount) {
            val item = c.getMediaItemAt(i)
            // artworkUri is the same song URI and always survives the controller boundary,
            // so it's a safe fallback when localConfiguration isn't populated.
            val itemUri = item.localConfiguration?.uri?.toString()
                ?: item.mediaMetadata.artworkUri?.toString()
            if (itemUri != uri) continue
            c.replaceMediaItem(
                i,
                item.buildUpon()
                    .setMediaMetadata(
                        item.mediaMetadata.buildUpon()
                            .setTitle(title)
                            .setArtist(artist.ifBlank { null })
                            .build()
                    )
                    .build()
            )
        }
        syncState()
        viewModelScope.launch { saveState() }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    /** Jumps to and plays the queue-sheet item at [queueIndex] (a position within [queue],
     *  not the underlying player index — [NowPlaying.mediaItemIndex] handles that mapping). */
    fun playQueueItem(queueIndex: Int) {
        val c = controller ?: return
        val target = queue.getOrNull(queueIndex) ?: return
        c.seekTo(target.mediaItemIndex, 0L)
        c.play()
    }

    fun next() {
        if (shuffleEnabled && pureRandom) seekToRandom() else controller?.seekToNext()
    }

    fun previous() {
        if (shuffleEnabled && pureRandom) seekToRandom() else controller?.seekToPrevious()
    }

    private fun seekToRandom() {
        val c = controller ?: return
        val count = c.mediaItemCount
        if (count > 0) c.seekTo(Random.nextInt(count), 0L)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        shuffleEnabled = !shuffleEnabled
        pureRandom = PlaybackPrefs.pureRandomShuffle
        isPureRandom = shuffleEnabled && pureRandom
        c.shuffleModeEnabled = shuffleEnabled && !pureRandom
        viewModelScope.launch { saveState() }
    }

    fun setRepeat(mode: RepeatMode) {
        controller?.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun reshuffle() {
        if (pureRandom) seekToRandom()
        else ReshuffleChannel.trigger.tryEmit(Unit)
    }

    private suspend fun saveState() {
        val c = controller ?: return
        val count = c.mediaItemCount
        if (count == 0) return
        val array = JSONArray()
        for (i in 0 until count) {
            val item = c.getMediaItemAt(i)
            array.put(JSONObject().apply {
                put("uri", item.localConfiguration?.uri?.toString() ?: "")
                put("title", item.mediaMetadata.title?.toString() ?: "")
                put("artist", item.mediaMetadata.artist?.toString() ?: "")
            })
        }

        // Walk the CURRENT play order (shuffle-aware if shuffle is on) via the base
        // Timeline API, so the exact "up next" sequence can be reconstructed on restart
        // instead of ExoPlayer generating a brand-new random order from scratch.
        val orderArray = JSONArray()
        val timeline = c.currentTimeline
        var idx = timeline.getFirstWindowIndex(c.shuffleModeEnabled)
        var safety = 0
        while (idx != C.INDEX_UNSET && safety < count) {
            orderArray.put(idx)
            idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            safety++
        }

        repo.savePlaybackState(
            SavedPlaybackState(
                queueJson = array.toString(),
                index = c.currentMediaItemIndex,
                positionMs = c.currentPosition,
                shuffleEnabled = shuffleEnabled,
                pureRandom = pureRandom,
                repeatMode = when (c.repeatMode) {
                    Player.REPEAT_MODE_ALL -> 1
                    Player.REPEAT_MODE_ONE -> 2
                    else -> 0
                },
                shuffleOrderJson = orderArray.toString(),
            )
        )
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    fun durationMs(): Long = (controller?.duration ?: 0L).coerceAtLeast(0L)

    private fun syncState() {
        val c = controller ?: return
        isPlaying = c.isPlaying
        repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }
        nowPlaying = c.currentMediaItem?.let { item ->
            NowPlaying(
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                artworkUri = item.mediaMetadata.artworkUri?.toString(),
            )
        }
        // queue and currentIndex are driven by PlaybackQueueState (updated by PlaybackService)
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }
}

private fun PlaylistSong.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist.ifBlank { null })
            .setArtworkUri(Uri.parse(uri))
            .build()
    )
    .build()

private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist.ifBlank { null })
            .setArtworkUri(Uri.parse(uri))
            .build()
    )
    .build()
