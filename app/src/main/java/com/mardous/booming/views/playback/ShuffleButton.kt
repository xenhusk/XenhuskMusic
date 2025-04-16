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

class ShuffleButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AppCompatImageButton(context, attrs, defStyleAttr) {

    private var shuffleMode = 0

    private var normalColor = Color.WHITE
    private var selectedColor = Color.WHITE

    private val shuffleOff = AppCompatResources.getDrawable(getContext(), R.drawable.ic_shuffle_24dp)!!.mutate()
        .apply { alpha = (0.6 * 255).toInt() }
    private val shuffleOn = AppCompatResources.getDrawable(getContext(), R.drawable.ic_shuffle_24dp)!!.mutate()

    fun setColors(normal: Int, selected: Int) {
        normalColor = normal
        selectedColor = selected

        shuffleOff.setTint(normal)
        shuffleOn.setTint(selected)
    }

    fun setShuffleMode(shuffleMode: Int) {
        if (this.shuffleMode != shuffleMode) {
            this.shuffleMode = shuffleMode
            setColors(normalColor, selectedColor)
            when (shuffleMode) {
                Playback.ShuffleMode.OFF -> {
                    setImageDrawable(shuffleOff)
                }

                Playback.ShuffleMode.ON -> {
                    setImageDrawable(shuffleOn)
                }
            }
        }
    }

    init {
        setShuffleMode(Playback.ShuffleMode.OFF)
        setImageDrawable(shuffleOff)
    }
}