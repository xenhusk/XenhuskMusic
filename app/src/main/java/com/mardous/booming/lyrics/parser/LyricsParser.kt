package com.mardous.booming.lyrics.parser

import android.text.format.DateUtils
import com.mardous.booming.lyrics.Lyrics
import java.io.File
import java.io.IOException
import java.io.Reader
import java.util.Locale

interface LyricsParser {

    fun parse(file: File): Lyrics? = try {
        parse(reader = file.reader())
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }

    fun parse(input: String): Lyrics? =
        if (input.isNotBlank()) parse(input.reader()) else null

    fun parse(reader: Reader): Lyrics?

    fun isValid(input: String): Boolean =
        if (input.isNotBlank()) isValid(input.reader()) else false

    fun isValid(reader: Reader): Boolean

    companion object {
        fun formatTime(milli: Long): String {
            val m = (milli / DateUtils.MINUTE_IN_MILLIS).toInt()
            val s = ((milli / DateUtils.SECOND_IN_MILLIS) % 60).toInt()
            val mm = String.format(Locale.getDefault(), "%02d", m)
            val ss = String.format(Locale.getDefault(), "%02d", s)
            return "$mm:$ss"
        }
    }
}
