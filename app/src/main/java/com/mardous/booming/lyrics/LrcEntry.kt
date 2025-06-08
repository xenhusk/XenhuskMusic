/*
 * Copyright (C) 2017 wangchenyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.mardous.booming.lyrics

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.util.Locale

/**
 * 一行歌词实体
 */
class LrcEntry(
    val time: Long,
    val text: String
) : Comparable<LrcEntry?> {

    var staticLayout: StaticLayout? = null
        private set

    /**
     * 歌词距离视图顶部的距离
     */
    @JvmField
    var offset: Float = Float.MIN_VALUE
    val height: Int
        get() {
            if (staticLayout == null) {
                return 0
            }
            return staticLayout!!.height
        }

    fun init(paint: TextPaint, width: Int, gravity: Int) {
        val align = when (gravity) {
            GRAVITY_LEFT -> Layout.Alignment.ALIGN_NORMAL
            GRAVITY_CENTER -> Layout.Alignment.ALIGN_CENTER
            GRAVITY_RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }

        staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.50f)
            .setIncludePad(false)
            .build()

        offset = Float.MIN_VALUE
    }

    fun getFormattedText(): String =
        "[${time.formatTime()}] $text"

    override fun compareTo(other: LrcEntry?): Int {
        if (other == null) {
            return -1
        }
        return (time - other.time).toInt()
    }

    private fun Long.formatTime() = String.format(
        Locale.getDefault(),
        "%d:%02d.%03d",
        this / 1000 / 60, //minutes
        this / 1000 % 60, //seconds
        this % 100
    )

    companion object {
        const val GRAVITY_CENTER: Int = 0
        const val GRAVITY_LEFT: Int = 1
        const val GRAVITY_RIGHT: Int = 2
    }
}
