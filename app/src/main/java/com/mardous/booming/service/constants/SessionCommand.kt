package com.mardous.booming.service.constants

interface SessionCommand {
    companion object {
        const val RESTORE_PLAYBACK = ServiceEvent.BOOMING_PACKAGE_NAME + ".restoreplayback"
        const val CYCLE_REPEAT = ServiceEvent.BOOMING_PACKAGE_NAME + ".cyclerepeat"
        const val TOGGLE_SHUFFLE = ServiceEvent.BOOMING_PACKAGE_NAME + ".toggleshuffle"
        const val TOGGLE_FAVORITE = ServiceEvent.BOOMING_PACKAGE_NAME + ".togglefavorite"
    }
}