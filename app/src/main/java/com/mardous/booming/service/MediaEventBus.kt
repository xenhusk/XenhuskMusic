package com.mardous.booming.service

import com.mardous.booming.viewmodels.player.model.MediaEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MediaEventBus {

    private val _mediaEventFlow = MutableSharedFlow<MediaEvent>(
        replay = 1,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val mediaEvent = _mediaEventFlow.asSharedFlow()

    suspend fun submitEvent(event: MediaEvent) {
        _mediaEventFlow.emit(event)
    }
}