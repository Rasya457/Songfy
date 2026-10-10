package com.lagu.songfy.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PlaybackEvent {
    SKIP_NEXT,
    SKIP_PREVIOUS,
    STOP
}

@Singleton
class PlaybackEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    fun emit(event: PlaybackEvent) {
        _events.tryEmit(event)
    }
}
