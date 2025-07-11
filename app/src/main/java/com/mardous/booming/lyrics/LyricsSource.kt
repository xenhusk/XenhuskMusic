package com.mardous.booming.lyrics

import android.content.Context
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.mardous.booming.R

enum class LyricsSource(
    @field:IdRes
    val applicableButtonId: Int,
    @field:StringRes
    val titleRes: Int,
    @field:StringRes
    val descriptionRes: Int = 0,
    private val helpShownKey: String = ""
) {
    Embedded(
        applicableButtonId = R.id.embeddedButton,
        titleRes = R.string.embedded_lyrics
    ),
    Downloaded(
        applicableButtonId = R.id.externalButton,
        titleRes = R.string.downloaded_lyrics
    ),
    Lrc(
        applicableButtonId = R.id.externalButton,
        titleRes = R.string.lrc_lyrics,
        descriptionRes = R.string.lyrics_source_lrc_file,
        helpShownKey = "lyrics_help_lrc"
    ),
    Ttml(
        applicableButtonId = R.id.externalButton,
        titleRes = R.string.ttml_lyrics,
        descriptionRes = R.string.lyrics_source_ttml_file,
        helpShownKey = "lyrics_help_ttml"
    );

    val isExternalSource: Boolean
        get() = this != Embedded

    fun canShowHelp(context: Context): Boolean =
        descriptionRes != 0 && helpShownKey.isNotEmpty() &&
                !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(helpShownKey, false)

    fun setHelpShown(context: Context) {
        if (helpShownKey.isNotEmpty()) {
            PreferenceManager.getDefaultSharedPreferences(context).edit {
                putBoolean(helpShownKey, true)
            }
        }
    }
}