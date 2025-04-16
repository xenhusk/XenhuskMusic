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

package com.mardous.booming.extensions.media

import android.content.Context
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.Settings
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.text.SpannableString
import android.util.Log
import androidx.core.content.contentValuesOf
import androidx.core.text.buildSpannedString
import com.mardous.booming.R
import com.mardous.booming.extensions.files.toAudioFile
import com.mardous.booming.extensions.resources.textColorPrimary
import com.mardous.booming.extensions.resources.textColorSecondary
import com.mardous.booming.extensions.resources.toForegroundColorSpan
import com.mardous.booming.extensions.showToast
import com.mardous.booming.extensions.utilities.appendWithDelimiter
import com.mardous.booming.extensions.utilities.buildInfoString
import com.mardous.booming.misc.ReplayGainTagExtractor
import com.mardous.booming.model.NowPlayingInfo
import com.mardous.booming.model.Song
import com.mardous.booming.model.WebSearchEngine
import com.mardous.booming.service.MusicPlayer
import org.jaudiotagger.audio.AudioFile
import java.io.File
import java.util.Locale

val Song.isPlayingSong
    get() = this.id == MusicPlayer.currentSong.id

fun List<Song>.getSpannedTitles(context: Context): List<CharSequence> {
    val primaryColorSpan = context.textColorPrimary().toForegroundColorSpan()
    val secondaryColorSpan = context.textColorSecondary().toForegroundColorSpan()

    return map {
        if (!it.albumArtistName.isArtistNameUnknown()) {
            buildSpannedString {
                append(SpannableString(it.title).apply {
                    setSpan(primaryColorSpan, 0, length, 0)
                })
                append(" ")
                append(SpannableString(context.getString(R.string.by_artist_x, it.albumArtistName)).apply {
                    setSpan(secondaryColorSpan, 0, length, 0)
                })
            }
        } else it.title
    }
}

fun List<Song>.asQueueItems(): List<MediaSessionCompat.QueueItem> {
    return runCatching {
        mapIndexed { index, song -> song.asQueueItem(index + 1L) }
    }.getOrDefault(arrayListOf())
}

fun List<Song>.playlistInfo(context: Context) = buildInfoString(songCountStr(context), songsDurationStr())

fun List<Song>.songsDurationStr() = sumOf { it.duration }.durationStr()

fun List<Song>.songCountStr(context: Context) = size.songsStr(context)

fun List<Song>.indexOfSong(songId: Long): Int = indexOfFirst { song -> song.id == songId }

fun Song.isArtistNameUnknown() = artistName.isArtistNameUnknown()

fun Song.displayArtistName() = artistName.displayArtistName()

fun Song.albumArtistName() = if (albumArtistName.isNullOrBlank()) artistName else albumArtistName!!

fun Song.songDurationStr() = duration.durationStr()

fun Song.searchQuery(engine: WebSearchEngine): String {
    val searchQuery = when (engine) {
        WebSearchEngine.Google, WebSearchEngine.YouTube ->
            if (isArtistNameUnknown()) title else "$artistName $title"

        WebSearchEngine.LastFm, WebSearchEngine.Wikipedia ->
            if (isArtistNameUnknown()) title else if (albumArtistName.isNullOrEmpty()) artistName.toAlbumArtistName() else albumArtistName!!
    }
    return engine.getURLForQuery(searchQuery)
}

fun Song.songInfo(isForWidget: Boolean = false): String {
    return if (isForWidget) {
        buildInfoString(displayArtistName(), albumName)
    } else buildInfoString(songDurationStr(), displayArtistName())
}

fun Song.extraInfo(requestedInfo: List<NowPlayingInfo>): String? {
    if (requestedInfo.isNotEmpty()) {
        try {
            val audioFile = audioFile()
                ?: return null

            val sb = StringBuilder()
            for (songMetadata in requestedInfo) {
                if (songMetadata.isEnabled) {
                    val readableInfo = songMetadata.info
                        .toReadableFormat(audioFile.tagOrCreateAndSetDefault, audioFile.audioHeader)
                    if (!readableInfo.isNullOrEmpty()) {
                        sb.appendWithDelimiter(readableInfo)
                    }
                }
            }
            return sb.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return null
}

fun Song.asQueueItem(itemId: Long): MediaSessionCompat.QueueItem {
    val mediaDescription = MediaDescriptionCompat.Builder()
        .setMediaId(id.toString())
        .setTitle(title)
        .setSubtitle(displayArtistName())
        .setIconUri(albumId.albumCoverUri())
        .setMediaUri(mediaStoreUri)
        .build()
    return MediaSessionCompat.QueueItem(mediaDescription, itemId)
}

fun Song.audioFile(): AudioFile? = File(data).toAudioFile()

fun Song.replayGainStr(context: Context): String? {
    val gainValues = ReplayGainTagExtractor.getReplayGain(this)
    val builder = StringBuilder()
    if (gainValues.rgTrack.toDouble() != 0.0) {
        builder.append(String.format(Locale.ROOT, "%s: %.2f dB", context.getString(R.string.track), gainValues.rgTrack))
    }
    if (gainValues.rgAlbum.toDouble() != 0.0) {
        if (builder.isNotEmpty())
            builder.append(" - ")

        builder.append(String.format(Locale.ROOT, "%s: %.2f dB", context.getString(R.string.album), gainValues.rgAlbum))
    }
    val replayGainValues = builder.toString()
    return replayGainValues.ifEmpty { null }
}

fun Song.configureRingtone(context: Context, isAlarm: Boolean): Boolean {
    val resolver = context.contentResolver
    val uri = mediaStoreUri

    try {
        contentValuesOf(
            MediaStore.Audio.AudioColumns.IS_RINGTONE to "1",
            MediaStore.Audio.AudioColumns.IS_ALARM to if (isAlarm) "1" else "0"
        ).also { values ->
            resolver.update(uri, values, null, null)
        }
    } catch (ignored: UnsupportedOperationException) {
        return false
    }

    try {
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns.TITLE),
            "${BaseColumns._ID}=?",
            arrayOf(id.toString()),
            null
        ).use { cursor ->
            if (cursor != null && cursor.count == 1) {
                cursor.moveToFirst()

                Settings.System.putString(resolver, Settings.System.RINGTONE, uri.toString())
                if (isAlarm) {
                    Settings.System.putString(resolver, Settings.System.ALARM_ALERT, uri.toString())
                }

                context.showToast(
                    if (isAlarm) {
                        context.getString(R.string.x_has_been_set_as_ringtone_and_as_alarm, cursor.getString(0))
                    } else context.getString(R.string.x_has_been_set_as_ringtone, cursor.getString(0))
                )
            }
        }
    } catch (e: SecurityException) {
        Log.e("SongExt", "Couldn't set the ringtone...", e)
        return false
    }
    return true
}