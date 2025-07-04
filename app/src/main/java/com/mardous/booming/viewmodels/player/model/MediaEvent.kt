package com.mardous.booming.viewmodels.player.model

sealed class MediaEvent {
    object MediaContentChanged : MediaEvent()
    object FavoriteContentChanged : MediaEvent()
    data class PlaybackError(val message: String) : MediaEvent()
}
