/*
 * Copyright (c) 2025 Christians Martínez Alvarado
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

package com.mardous.booming.http.lastfm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class LastFmAlbum(val album: Album?) {
    @Serializable
    class Album(val image: List<LastFmImage>, val wiki: LastFmWiki?)
}

@Serializable
class LastFmArtist(val artist: Artist?) {
    @Serializable
    class Artist(val bio: LastFmWiki?)
}

@Serializable
class LastFmTrack(val track: Track?) {
    @Serializable
    class Track(val artist: Artist?, val album: Album?) {
        @Serializable
        class Artist(val name: String?)
        @Serializable
        class Album(val title: String?, val image: List<LastFmImage>)
    }
}

@Serializable
class LastFmImage(
    @SerialName("#text")
    val text: String?,
    val size: String?
)

@Serializable
class LastFmWiki(val content: String?)