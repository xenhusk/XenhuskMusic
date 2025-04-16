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

package com.mardous.booming.http.lyrics

import android.content.Context
import com.mardous.booming.extensions.media.albumArtistName
import com.mardous.booming.http.lyrics.applemusic.AppleMusicLyricsApi
import com.mardous.booming.http.lyrics.spotify.SpotifyLyricsApi
import com.mardous.booming.model.DownloadedLyrics
import com.mardous.booming.model.Song
import com.mardous.booming.model.toDownloadedLyrics
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.encodeURLParameter

class LyricsService(context: Context, private val client: HttpClient) {

    private val appleMusicLyricsApi = AppleMusicLyricsApi(client)
    private val spotifyLyricsApi = SpotifyLyricsApi(context, client)

    suspend fun getLyrics(
        song: Song,
        title: String = song.title,
        artist: String = song.albumArtistName()
    ): DownloadedLyrics {
        if (song == Song.emptySong) {
            return song.toDownloadedLyrics()
        }
        var downloadedLyrics = lrcLibLyrics(song, title, artist) ?: song.toDownloadedLyrics()
        if (!downloadedLyrics.hasMultiOptions) {
            val spotifyLyrics = runCatching { spotifyLyricsApi.songLyrics(song, title, artist) }.getOrNull()
            if (downloadedLyrics.plainLyrics.isNullOrEmpty()) {
                downloadedLyrics = downloadedLyrics.copy(plainLyrics = spotifyLyrics?.plainLyrics)
            }
            if (downloadedLyrics.syncedLyrics.isNullOrEmpty()) {
                downloadedLyrics = downloadedLyrics.copy(syncedLyrics = spotifyLyrics?.syncedLyrics)
            }
            if (downloadedLyrics.syncedLyrics.isNullOrEmpty()) {
                val applyMusicLyrics = runCatching { appleMusicLyricsApi.songLyrics(song, title, artist) }.getOrNull()
                downloadedLyrics = downloadedLyrics.copy(syncedLyrics = applyMusicLyrics?.syncedLyrics)
            }
        }
        return downloadedLyrics
    }

    private suspend fun lrcLibLyrics(song: Song, title: String, artist: String): DownloadedLyrics? {
        val lyrics = client.get("https://lrclib.net/api/search") {
            url.encodedParameters.append("q", "$artist $title".encodeURLParameter())
        }.body<List<DownloadedLyrics>>()
        if (lyrics.isEmpty()) {
            return null
        } else {
            val songDurationInSeconds = (song.duration / 1000).toDouble()
            var matchingLyrics = lyrics.firstOrNull {
                val maxValue = maxOf(songDurationInSeconds, it.duration)
                val minValue = minOf(songDurationInSeconds, it.duration)
                ((maxValue - minValue) < 2)
            }
            if (matchingLyrics == null) {
                matchingLyrics = lyrics.first { !it.isSynced }
            }
            return matchingLyrics
        }
    }
}