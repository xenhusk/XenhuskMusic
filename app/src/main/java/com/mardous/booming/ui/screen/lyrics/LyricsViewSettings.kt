package com.mardous.booming.ui.screen.lyrics

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
class LyricsViewSettings(
    private val mode: Mode,
    val isCenterCurrentLine: Boolean,
    syncedFontSize: Int,
    unsyncedFontSize: Int
) {
    val syncedFontSize = syncedFontSize.sp
    val unsyncedFontSize = unsyncedFontSize.sp

    val contentPadding by lazy {
        when (mode) {
            Mode.Full -> {
                PaddingValues(
                    vertical = 96.dp,
                    horizontal = 16.dp
                )
            }

            Mode.Player -> {
                PaddingValues(vertical = 72.dp, horizontal = 8.dp)
            }
        }
    }

    fun calculateCenterOffset(
        currentIndex: Int,
        listState: LazyListState,
        density: Density,
        insets: WindowInsets
    ): Int {
        val topPadding = contentPadding.calculateTopPadding().value
        val viewportHeight = with(listState.layoutInfo) { viewportEndOffset - viewportStartOffset }
        val currentItemHeight = listState.layoutInfo.visibleItemsInfo.find { it.index == currentIndex }
            ?.size ?: (syncedFontSize.value * 2).toInt()
        val offset = (viewportHeight / 2) - (currentItemHeight / 2) - topPadding.toInt() - insets.getTop(density)
        return -offset
    }

    enum class Mode(
        private val syncedKey: String,
        private val syncedDefault: Int,
        private val unsyncedKey: String,
        private val unsyncedDefault: Int,
    ) {
        Player(
            syncedDefault = 24,
            syncedKey = Key.SYNCED_FONT_SIZE_PLAYER,
            unsyncedDefault = 16,
            unsyncedKey = Key.UNSYNCED_FONT_SIZE_PLAYER
        ),
        Full(
            syncedKey = Key.SYNCED_FONT_SIZE_FULL,
            syncedDefault = 30,
            unsyncedKey = Key.UNSYNCED_FONT_SIZE_FULL,
            unsyncedDefault = 20
        );

        fun isCenterCurrentLine(preferences: SharedPreferences): Boolean {
            return preferences.getBoolean(Key.CENTER_CURRENT_LINE, false)
        }

        fun getSyncedFontSize(preferences: SharedPreferences): Int {
            return preferences.getInt(syncedKey, syncedDefault)
        }

        fun getUnsyncedFontSize(preferences: SharedPreferences): Int {
            return preferences.getInt(unsyncedKey, unsyncedDefault)
        }
    }

    interface Key {
        companion object {
            const val CENTER_CURRENT_LINE = "lyrics_center_current_line"
            const val SYNCED_FONT_SIZE_PLAYER = "synced_lyrics_font_size_player"
            const val UNSYNCED_FONT_SIZE_PLAYER = "unsynced_lyrics_font_size_player"
            const val SYNCED_FONT_SIZE_FULL = "synced_lyrics_font_size_full"
            const val UNSYNCED_FONT_SIZE_FULL = "unsynced_lyrics_font_size_full"
        }
    }
}