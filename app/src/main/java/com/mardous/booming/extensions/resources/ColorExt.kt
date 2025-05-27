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

package com.mardous.booming.extensions.resources

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.style.ForegroundColorSpan
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.mardous.booming.R
import com.mardous.booming.extensions.hasS
import com.mardous.booming.extensions.isNightMode
import com.mardous.booming.extensions.resolveColor
import com.mardous.booming.util.Preferences

val Int.isColorLight: Boolean
    get() = (1 - (0.299 * Color.red(this) + 0.587 * Color.green(this) + 0.114 * Color.blue(this)) / 255) < 0.4

val Int.darkenColor: Int
    get() = shiftColor(0.9f)

fun Int.shiftColor(by: Float): Int {
    if (by == 1f) return this
    val alpha = Color.alpha(this)
    val hsv = FloatArray(3)
    Color.colorToHSV(this, hsv)
    hsv[2] *= by // value component

    return (alpha shl 24) + (0x00ffffff and Color.HSVToColor(hsv))
}

fun Int.withAlpha(alpha: Float): Int {
    val a = 255.coerceAtMost(0.coerceAtLeast((alpha * 255).toInt())) shl 24
    val rgb = 0x00ffffff and this
    return a + rgb
}

fun Int.toForegroundColorSpan() = ForegroundColorSpan(this)

fun Int.toColorStateList(): ColorStateList = ColorStateList.valueOf(this)

@ColorInt
fun Context.getColorCompat(@ColorRes res: Int) = ContextCompat.getColor(this, res)

@ColorInt
fun Fragment.surfaceColor() = requireContext().surfaceColor()

@ColorInt
fun Context.surfaceColor() = resolveColor(com.google.android.material.R.attr.colorSurface)

@ColorInt
fun Fragment.accentColor() = requireContext().accentColor()

@ColorInt
fun Context.accentColor() =
    if (hasS() && Preferences.materialYou) getColorCompat(R.color.m3_accent_color) else primaryColor()

@ColorInt
fun Fragment.primaryColor() = requireContext().primaryColor()

@ColorInt
fun Context.primaryColor() = resolveColor(com.google.android.material.R.attr.colorPrimary)

@ColorInt
fun Fragment.secondaryColor() = requireContext().secondaryColor()

@ColorInt
fun Context.secondaryColor() = resolveColor(com.google.android.material.R.attr.colorSecondary)

@ColorInt
fun Fragment.controlColorNormal() = requireContext().controlColorNormal()

@ColorInt
fun Context.controlColorNormal() = resolveColor(com.google.android.material.R.attr.colorControlNormal)

@ColorInt
fun Fragment.textColorPrimary() = requireContext().textColorPrimary()

@ColorInt
fun Context.textColorPrimary() = resolveColor(android.R.attr.textColorPrimary)

@ColorInt
fun Fragment.textColorSecondary() = requireContext().textColorSecondary()

@ColorInt
fun Context.textColorSecondary() = resolveColor(android.R.attr.textColorSecondary)

@ColorInt
fun Context.defaultFooterColor() = resolveColor(R.attr.defaultFooterColor)

fun Slider.applyColor(@ColorInt color: Int) {
    color.toColorStateList().run {
        thumbTintList = this
        trackActiveTintList = this
        trackInactiveTintList = ColorStateList.valueOf(color.withAlpha(0.1f))
        haloTintList = this
    }
}

fun MaterialButton.applyColor(color: Int, isIconButton: Boolean = false) {
    if (isIconButton) {
        val colorTintList = color.toColorStateList()
        setTextColor(colorTintList)
        iconTint = colorTintList
    } else {
        val backgroundColorStateList = color.toColorStateList()
        val textColorColorStateList = getPrimaryTextColor(context, color.isColorLight).toColorStateList()
        backgroundTintList = backgroundColorStateList
        setTextColor(textColorColorStateList)
        iconTint = textColorColorStateList
    }
}

fun FloatingActionButton.applyColor(color: Int) {
    val textColor = getPrimaryTextColor(context, color.isColorLight)
    backgroundTintList = ColorStateList.valueOf(color)
    imageTintList = ColorStateList.valueOf(textColor)
}

fun getPrimaryTextColor(context: Context, isDark: Boolean = !context.isNightMode, isDisabled: Boolean = false): Int {
    val resolveColor = if (isDark) {
        if (isDisabled) R.color.primary_text_disabled_light else R.color.primary_text_light
    } else {
        if (isDisabled) R.color.primary_text_disabled_dark else R.color.primary_text_dark
    }
    return context.getColorCompat(resolveColor)
}

fun getSecondaryTextColor(context: Context, isDark: Boolean = !context.isNightMode, isDisabled: Boolean = false): Int {
    val resolveColor = if (isDark) {
        if (isDisabled) R.color.secondary_text_disabled_light else R.color.secondary_text_light
    } else {
        if (isDisabled) R.color.secondary_text_disabled_dark else R.color.secondary_text_dark
    }
    return context.getColorCompat(resolveColor)
}