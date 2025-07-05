package com.mardous.booming.service.constants

import com.mardous.booming.service.constants.ServiceAction.Companion.BOOMING_PACKAGE_NAME

interface SessionCommand {
    companion object {
        const val RESTORE_PLAYBACK = "$BOOMING_PACKAGE_NAME.command.restore_playback"
        const val CYCLE_REPEAT = "$BOOMING_PACKAGE_NAME.command.cycle_repeat"
        const val TOGGLE_SHUFFLE = "$BOOMING_PACKAGE_NAME.command.toggle_shuffle"
        const val TOGGLE_FAVORITE = "$BOOMING_PACKAGE_NAME.command.toggle_favorite"
    }
}