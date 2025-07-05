package com.mardous.booming.viewmodels.player.model

import com.mardous.booming.service.constants.SessionEvent

enum class MediaEvent(val code: String) {
    MediaContentChanged(SessionEvent.MEDIA_CONTENT_CHANGED),
    FavoriteContentChanged(SessionEvent.FAVORITE_CONTENT_CHANGED),
    PlaybackRestored(SessionEvent.PLAYBACK_RESTORED);

    companion object {
        fun fromSessionEvent(sessionEvent: String?) =
            entries.firstOrNull { event -> event.code == sessionEvent }
    }
}
