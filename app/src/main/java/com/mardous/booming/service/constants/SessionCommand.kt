package com.mardous.booming.service.constants

interface SessionCommand {
    companion object {
        const val RESTORE_PLAYBACK = ServiceAction.BOOMING_PACKAGE_NAME + ".restoreplayback"
        const val CYCLE_REPEAT = ServiceAction.BOOMING_PACKAGE_NAME + ".cyclerepeat"
        const val TOGGLE_SHUFFLE = ServiceAction.BOOMING_PACKAGE_NAME + ".toggleshuffle"
        const val TOGGLE_FAVORITE = ServiceAction.BOOMING_PACKAGE_NAME + ".togglefavorite"
    }
}