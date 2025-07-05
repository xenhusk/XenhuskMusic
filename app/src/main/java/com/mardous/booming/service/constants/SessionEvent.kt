package com.mardous.booming.service.constants

import com.mardous.booming.service.constants.ServiceAction.Companion.BOOMING_PACKAGE_NAME

interface SessionEvent {
    companion object {
        const val PLAYBACK_RESTORED = "$BOOMING_PACKAGE_NAME.event.playback_restored"
        const val MEDIA_CONTENT_CHANGED = "$BOOMING_PACKAGE_NAME.event.media_content_changed"
        const val FAVORITE_CONTENT_CHANGED = "$BOOMING_PACKAGE_NAME.event.favorite_content_changed"
    }
}