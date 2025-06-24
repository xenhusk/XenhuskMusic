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

package com.mardous.booming.fragments.lyrics

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.mardous.booming.http.Result
import com.mardous.booming.model.Song
import com.mardous.booming.mvvm.LyricsResult
import com.mardous.booming.repository.LyricsRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @author Christians M. A. (mardous)
 */
class LyricsViewModel(private val lyricsRepository: LyricsRepository) : ViewModel() {

    private val _lyricsResult = MutableStateFlow(LyricsResult.Empty)
    val lyricsResult = _lyricsResult.asStateFlow()

    private val silentHandler = CoroutineExceptionHandler { _, _ -> }

    fun updateSong(song: Song) = viewModelScope.launch {
        _lyricsResult.value = LyricsResult(id = song.id, loading = true)
        val result = withContext(IO) {
            lyricsRepository.allLyrics(song, allowDownload = true, fromEditor = false)
        }
        _lyricsResult.value = result
    }

    fun getOnlineLyrics(song: Song, title: String, artist: String) = liveData(IO) {
        emit(Result.Loading)
        emit(lyricsRepository.onlineLyrics(song, title, artist))
    }

    fun getAllLyrics(
        song: Song,
        allowDownload: Boolean = false,
        fromEditor: Boolean = false
    ) = liveData(IO + silentHandler) {
        emit(LyricsResult(id = song.id, loading = true))
        emit(lyricsRepository.allLyrics(song, allowDownload, fromEditor))
    }

    fun getLyrics(song: Song) = liveData(IO) {
        emit(lyricsRepository.embeddedLyrics(song, requirePlainText = true))
    }

    fun shareSyncedLyrics(song: Song) = liveData(IO) {
        emit(lyricsRepository.shareSyncedLyrics(song))
    }

    fun saveLyrics(
        song: Song,
        plainLyrics: String?,
        syncedLyrics: String?,
        plainLyricsModified: Boolean
    ) = liveData(IO) {
        emit(lyricsRepository.saveLyrics(song, plainLyrics, syncedLyrics, plainLyricsModified))
    }

    fun setLRCContentFromUri(song: Song, uri: Uri?) = liveData(IO) {
        emit(lyricsRepository.saveSyncedLyricsFromUri(song, uri))
    }

    fun deleteLyrics() = viewModelScope.launch(IO) {
        lyricsRepository.deleteAllLyrics()
    }
}