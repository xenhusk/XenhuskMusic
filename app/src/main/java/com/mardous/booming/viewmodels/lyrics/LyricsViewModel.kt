package com.mardous.booming.viewmodels.lyrics

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.mardous.booming.http.Result
import com.mardous.booming.model.Song
import com.mardous.booming.repository.LyricsRepository
import com.mardous.booming.service.queue.QueueManager
import com.mardous.booming.viewmodels.lyrics.model.EditableLyrics
import com.mardous.booming.viewmodels.lyrics.model.LyricsResult
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @author Christians M. A. (mardous)
 */
class LyricsViewModel(
    queueManager: QueueManager,
    private val lyricsRepository: LyricsRepository
) : ViewModel() {

    private val _lyricsResult = MutableStateFlow(LyricsResult.Companion.Empty)
    val lyricsResult = _lyricsResult.asStateFlow()

    private val silentHandler = CoroutineExceptionHandler { _, _ -> }

    init {
        queueManager.currentSongFlow
            .onEach { updateSong(it) }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)
    }

    private fun updateSong(song: Song) = viewModelScope.launch {
        _lyricsResult.value = LyricsResult(id = song.id, loading = true)
        val result = withContext(Dispatchers.IO) {
            lyricsRepository.allLyrics(song, allowDownload = true, fromEditor = false)
        }
        _lyricsResult.value = result
    }

    fun getOnlineLyrics(song: Song, title: String, artist: String) = liveData(Dispatchers.IO) {
        emit(Result.Loading)
        emit(lyricsRepository.onlineLyrics(song, title, artist))
    }

    fun getAllLyrics(
        song: Song,
        allowDownload: Boolean = false,
        fromEditor: Boolean = false
    ) = liveData(Dispatchers.IO + silentHandler) {
        emit(LyricsResult(id = song.id, loading = true))
        emit(lyricsRepository.allLyrics(song, allowDownload, fromEditor))
    }

    fun getLyrics(song: Song) = liveData(Dispatchers.IO) {
        emit(lyricsRepository.embeddedLyrics(song, requirePlainText = true))
    }

    fun shareSyncedLyrics(song: Song) = liveData(Dispatchers.IO) {
        emit(lyricsRepository.shareSyncedLyrics(song))
    }

    fun getWritableUris(song: Song) = liveData(Dispatchers.IO) {
        emit(lyricsRepository.writableUris(song))
    }

    fun saveLyrics(song: Song, plainLyrics: EditableLyrics?, syncedLyrics: EditableLyrics?) =
        liveData(Dispatchers.IO) {
            emit(lyricsRepository.saveLyrics(song, plainLyrics, syncedLyrics))
        }

    fun importLyrics(song: Song, uri: Uri) = liveData(Dispatchers.IO) {
        emit(lyricsRepository.importLyrics(song, uri))
    }

    fun deleteLyrics() = viewModelScope.launch(Dispatchers.IO) {
        lyricsRepository.deleteAllLyrics()
    }
}