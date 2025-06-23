package com.mardous.booming.http.lyrics.lrclib

import com.mardous.booming.http.lyrics.LyricsApi
import com.mardous.booming.model.DownloadedLyrics
import com.mardous.booming.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.encodeURLParameter

class LrcLibApi(private val client: HttpClient) : LyricsApi {

    override suspend fun songLyrics(song: Song, title: String, artist: String): DownloadedLyrics? {
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