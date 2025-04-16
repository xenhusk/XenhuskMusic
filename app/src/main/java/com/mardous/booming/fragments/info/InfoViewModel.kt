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
package com.mardous.booming.fragments.info

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.mardous.booming.R
import com.mardous.booming.database.toPlayCount
import com.mardous.booming.extensions.files.*
import com.mardous.booming.extensions.media.audioFile
import com.mardous.booming.extensions.media.replayGainStr
import com.mardous.booming.extensions.media.songDurationStr
import com.mardous.booming.extensions.media.timesStr
import com.mardous.booming.extensions.utilities.dateStr
import com.mardous.booming.extensions.utilities.format
import com.mardous.booming.model.Album
import com.mardous.booming.model.Artist
import com.mardous.booming.model.Song
import com.mardous.booming.mvvm.PlayInfoResult
import com.mardous.booming.mvvm.SongDetailResult
import com.mardous.booming.repository.Repository
import kotlinx.coroutines.Dispatchers.IO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey
import java.io.File

class InfoViewModel(private val repository: Repository) : ViewModel() {

    fun loadAlbum(id: Long): LiveData<Album> = liveData(IO) {
        if (id != -1L) {
            emit(repository.albumById(id))
        } else {
            emit(Album.empty)
        }
    }

    fun loadArtist(id: Long, name: String?): LiveData<Artist> = liveData(IO) {
        if (name.isNullOrEmpty()) {
            emit(repository.artistById(id))
        } else if (id == -1L) {
            emit(repository.albumArtistByName(name))
        } else {
            emit(Artist.empty)
        }
    }

    fun playInfo(songs: List<Song>): LiveData<PlayInfoResult> = liveData(IO) {
        val playCountEntities = repository.playCountSongsFrom(songs).sortedByDescending { it.playCount }
        val totalPlayCount = playCountEntities.sumOf { it.playCount }
        val totalSkipCount = playCountEntities.sumOf { it.skipCount }
        val lastPlayDate = playCountEntities.maxOf { it.timePlayed }
        emit(PlayInfoResult(totalPlayCount, totalSkipCount, lastPlayDate, playCountEntities))
    }

    fun songDetail(context: Context, song: Song): LiveData<SongDetailResult> = liveData(IO) {
        // Play count
        val result = runCatching {
            val playCountEntity = repository.findSongInPlayCount(song.id) ?: song.toPlayCount()
            val playCount = playCountEntity.playCount.timesStr(context)
            val skipCount = playCountEntity.skipCount.timesStr(context)
            val lastPlayed = context.dateStr(playCountEntity.timePlayed)

            val dateModified = song.getModifiedDate().format(context)
            val year = if (song.year > 0) song.year.toString() else null
            val trackLength = song.songDurationStr()
            val replayGain = song.replayGainStr(context)

            val audioFile = song.audioFile()
            if (audioFile == null) {
                SongDetailResult(
                    playCount = playCount,
                    skipCount = skipCount,
                    lastPlayedDate = lastPlayed,
                    filePath = File(song.data).getPrettyAbsolutePath(),
                    fileSize = song.size.asReadableFileSize(),
                    trackLength = trackLength,
                    dateModified = dateModified,
                    title = song.title,
                    albumYear = year,
                    replayGain = replayGain
                )
            } else {
                val tag = audioFile.getBestTag()

                // FILE
                val filePath = audioFile.file.getPrettyAbsolutePath()
                val fileSize = audioFile.file.getHumanReadableSize()

                val audioHeader = getAudioHeader(context, audioFile.audioHeader)

                // MEDIA
                val title = tag?.getFirst(FieldKey.TITLE)
                val album = tag?.getFirst(FieldKey.ALBUM)
                val artist = tag?.getAll(FieldKey.ARTIST)?.merge()
                val albumArtist = tag?.getFirst(FieldKey.ALBUM_ARTIST)

                val trackNumber = getNumberAndTotal(
                    tag?.getFirst(FieldKey.TRACK),
                    tag?.getFirst(FieldKey.TRACK_TOTAL)
                )
                val discNumber = getNumberAndTotal(
                    tag?.getFirst(FieldKey.DISC_NO),
                    tag?.getFirst(FieldKey.DISC_TOTAL)
                )

                val composer = tag?.getAll(FieldKey.COMPOSER)?.merge()
                val conductor = tag?.getAll(FieldKey.CONDUCTOR)?.merge()
                val publisher = tag?.getAll(FieldKey.RECORD_LABEL)?.merge()
                val genre = tag?.getGenres()?.merge()
                val comment = tag?.getFirst(FieldKey.COMMENT)

                SongDetailResult(
                    playCount,
                    skipCount,
                    lastPlayed,
                    filePath,
                    fileSize,
                    trackLength,
                    dateModified,
                    audioHeader,
                    title,
                    album,
                    artist,
                    albumArtist,
                    year,
                    trackNumber,
                    discNumber,
                    composer,
                    conductor,
                    publisher,
                    genre,
                    replayGain,
                    comment
                )
            }
        }
        if (result.isSuccess) {
            emit(result.getOrThrow())
        } else {
            emit(SongDetailResult.Empty)
        }
    }

    private fun getAudioHeader(context: Context, ah: AudioHeader): String {
        var baseHeader = String.format(
            "%s - %s KB/s %s Hz - %s",
            ah.format,
            ah.bitRate,
            ah.sampleRate,
            ah.channels
        )
        if (ah.isVariableBitRate) {
            baseHeader =
                "$baseHeader, ${context.getString(R.string.label_variable_bitrate).lowercase()}"
        }
        if (ah.isLossless) {
            baseHeader = "$baseHeader, ${context.getString(R.string.label_loss_less).lowercase()}"
        }
        return baseHeader
    }

    private fun getNumberAndTotal(number: String?, total: String?): String? {
        val intNumber = number?.toIntOrNull()
        if (intNumber == null || intNumber == 0) {
            return null
        }
        val intTotal = total?.toIntOrNull()
        return if (intTotal == null || intTotal == 0) number else String.format(
            "%s/%s",
            number,
            total
        )
    }
}