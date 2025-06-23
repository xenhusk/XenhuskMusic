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

package com.mardous.booming.http.lyrics.applemusic

import kotlinx.serialization.Serializable

@Serializable
class AppleSearchResponse(val id: Long, val songName: String, val artistName: String, val url: String)

@Serializable
class AppleLyricsResponse(val type: String, val content: List<AppleLyrics>?) {
    @Serializable
    class AppleLyrics(
        val text: List<AppleLyricsLine>,
        val oppositeTurn: Boolean,
        val timestamp: Int,
        val endtime: Int
    ) {
        @Serializable
        class AppleLyricsLine(
            val text: String,
            val part: Boolean,
            val timestamp: Int?,
            val endtime: Int?
        )
    }
}