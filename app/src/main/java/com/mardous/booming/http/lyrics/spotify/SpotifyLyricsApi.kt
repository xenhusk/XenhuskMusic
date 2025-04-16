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

package com.mardous.booming.http.lyrics.spotify

import android.content.Context
import android.util.Log
import com.mardous.booming.http.lyrics.LyricsApi
import com.mardous.booming.model.DownloadedLyrics
import com.mardous.booming.model.Song
import com.mardous.booming.model.toDownloadedLyrics
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLParameter
import io.ktor.http.userAgent
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Date

class SpotifyLyricsApi(private val context: Context, private val client: HttpClient) : LyricsApi {

    private var lastToken: SpotifyTokenResponse? = null
    private val searchResults = hashMapOf<Long, TrackSearchResult>()

    override suspend fun songLyrics(
        song: Song,
        title: String,
        artist: String
    ): DownloadedLyrics? {
        refreshTokenIfNecessary()
        return searchTrack(song, title, artist).let {
            val response = client.get("https://spotify-lyrics-api-one.vercel.app/") {
                parameter("format", "lrc")
                parameter("trackid", it.tracks.items.first().id)
            }.body<SyncedLinesResponse>()
            parseLyrics(song, response)
        }
    }

    private suspend fun searchTrack(song: Song, title: String, artist: String): TrackSearchResult {
        return searchResults.getOrPut(song.id) {
            client.get("https://api.spotify.com/v1/search") {
                parameter("type", "track")
                parameter("limit", 1)
                parameter("offset", 0)
                bearerAuth(lastToken!!.accessToken)
                url.encodedParameters.append("q", "$artist $title".encodeURLParameter())
            }.body<TrackSearchResult>()
        }
    }

    private suspend fun refreshTokenIfNecessary() {
        if (lastToken == null) {
            lastToken = try {
                File(context.filesDir, TOKEN_FILE_PATH).let { Json.decodeFromString(it.readText()) }
            } catch (e: IOException) {
                Log.w(TAG, "An error occurred while loading the cached access token. Getting a new one.", e)
                null
            }
        }
        if (lastToken == null || Date().time > lastToken!!.accessTokenExpirationTimestampMs) {
            lastToken = client.get("https://open.spotify.com/get_access_token") {
                parameter("reason", "transport")
                parameter("productType", "web_player")
                userAgent(USER_AGENT)
            }.body<SpotifyTokenResponse>().also {
                try {
                    File(context.filesDir, TOKEN_FILE_PATH).writeText(Json.encodeToString(it))
                } catch (e: IOException) {
                    Log.w(TAG, "Failed to cache access token", e)
                }
            }
        }
    }

    private fun parseLyrics(song: Song, result: SyncedLinesResponse): DownloadedLyrics? {
        val lines = result.lines.filter { it.words.isNotBlank() }
        return song.toDownloadedLyrics(
            plainLyrics = lines.joinToString("\n") { it.words },
            syncedLyrics = lines.joinToString("\n") { "[${it.timeTag}] ${it.words}" }
        )
    }

    companion object {
        private const val TAG = "SpotifyLyricsApi"
        private const val TOKEN_FILE_PATH = "/last_spotify_token.json"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
    }
}