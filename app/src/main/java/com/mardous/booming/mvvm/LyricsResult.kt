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

package com.mardous.booming.mvvm

import com.mardous.booming.lyrics.LrcLyrics

class LyricsResult(
    val id: Long,
    val data: String? = null,
    val lrcData: LrcLyrics = LrcLyrics(),
    val fromLocalFile: Boolean = false,
    val loading: Boolean = false,
) {
    val hasData: Boolean get() = !data.isNullOrEmpty()
    val isSynced: Boolean get() = lrcData.hasLines
    val isEmpty: Boolean get() = !hasData && !isSynced
}