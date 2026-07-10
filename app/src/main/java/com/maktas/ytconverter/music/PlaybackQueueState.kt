package com.maktas.ytconverter.music

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QueueItem(
    val title: String,
    val artist: String,
    val artworkUri: String?,
)

/**
 * Single source of truth for the playback queue in actual play order.
 * Written by [PlaybackService] (which has direct ExoPlayer access to read the shuffle order)
 * and read by [PlaybackViewModel] via StateFlow collection.
 */
object PlaybackQueueState {

    data class State(
        val items: List<QueueItem> = emptyList(),
        /** Index into [items] that is currently playing (-1 when nothing). */
        val currentPosition: Int = -1,
        val isPlaying: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(items: List<QueueItem>, currentPosition: Int, isPlaying: Boolean = false) {
        _state.value = State(items, currentPosition, isPlaying)
    }

    fun updateIsPlaying(isPlaying: Boolean) {
        _state.value = _state.value.copy(isPlaying = isPlaying)
    }

    fun clear() {
        _state.value = State()
    }
}

/** Signal from PlaybackViewModel → PlaybackService to regenerate the shuffle order. */
object ReshuffleChannel {
    val trigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
