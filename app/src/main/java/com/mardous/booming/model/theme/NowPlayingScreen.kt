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

package com.mardous.booming.model.theme

import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.mardous.booming.R

enum class NowPlayingScreen(
    @StringRes
    val titleRes: Int,
    @DrawableRes
    val drawableResId: Int,
    @LayoutRes
    val albumCoverLayoutRes: Int?,
    val supportsCoverLyrics: Boolean
) {
    Default(R.string.normal, R.drawable.np_normal, R.layout.fragment_album_cover_default, true),
    FullCover(R.string.full_cover, R.drawable.np_full, R.layout.fragment_album_cover, false),
    Gradient(R.string.gradient, R.drawable.np_gradient, R.layout.fragment_album_cover, true),
    Peek(R.string.peek, R.drawable.np_peek, R.layout.fragment_album_cover_peek, false);
}