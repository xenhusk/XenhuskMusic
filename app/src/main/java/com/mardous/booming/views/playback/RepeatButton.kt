/*
 * Copyright (c) 2024 Christians Martínez Alvarado
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mardous.booming.views.playback

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import com.mardous.booming.R
import com.mardous.booming.service.playback.Playback

class RepeatButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AppCompatImageButton(context, attrs, defStyleAttr) {

    private var repeatMode = 0

    private var normalColor = Color.WHITE
    private var selectedColor = Color.WHITE

    private val offDrawable = AppCompatResources.getDrawable(getContext(), R.drawable.ic_repeat_24dp)!!.mutate()
        .apply { alpha = (0.6 * 255).toInt() }
    private val oneDrawable = AppCompatResources.getDrawable(getContext(), R.drawable.ic_repeat_one_24dp)!!.mutate()
    private val allDrawable = AppCompatResources.getDrawable(getContext(), R.drawable.ic_repeat_24dp)!!.mutate()

    fun setColors(normal: Int, selected: Int) {
        normalColor = normal
        selectedColor = selected

        offDrawable.setTint(normal)
        oneDrawable.setTint(selected)
        allDrawable.setTint(selected)
    }

    fun setRepeatMode(repeatMode: Int) {
        if (repeatMode != this.repeatMode) {
            this.repeatMode = repeatMode
            setColors(normalColor, selectedColor)
            when (repeatMode) {
                Playback.RepeatMode.ALL -> {
                    setImageDrawable(allDrawable)
                }

                Playback.RepeatMode.CURRENT -> {
                    setImageDrawable(oneDrawable)
                }

                Playback.RepeatMode.OFF -> {
                    setImageDrawable(offDrawable)
                }
            }
        }
    }

    init {
        setRepeatMode(Playback.RepeatMode.OFF)
        setImageDrawable(offDrawable)
    }
}