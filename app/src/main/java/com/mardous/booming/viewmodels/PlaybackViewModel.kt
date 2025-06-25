package com.mardous.booming.viewmodels

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.model.Song
import com.mardous.booming.service.playback.Playback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackViewModel : ViewModel() {

    private var mediaController: MediaControllerCompat? = null
    private val transportControls get() = mediaController?.transportControls
    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            _isPlayingFlow.value = state?.state == PlaybackStateCompat.STATE_PLAYING
            if (progressObserver == null) {
                _progressFlow.value = state?.position ?: 0
            }
            if (isPlayingFlow.value) {
                if (progressObserver == null) {
                    startProgressObserver()
                }
            } else {
                stopProgressObserver()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            val mediaMetadata = MediaMetadataCompat.fromMediaMetadata(metadata?.mediaMetadata)
            _durationFlow.value = mediaMetadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatModeFlow.value = Playback.RepeatMode.fromValue(repeatMode)
        }

        override fun onShuffleModeChanged(shuffleMode: Int) {
            _shuffleModeFlow.value = Playback.ShuffleMode.fromValue(shuffleMode)
        }
    }

    private val _progressFlow = MutableStateFlow(0L)
    val progressFlow = _progressFlow.asStateFlow()

    private val _durationFlow = MutableStateFlow(0L)
    val durationFlow = _durationFlow.asStateFlow()

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow = _isPlayingFlow.asStateFlow()
    val isPlaying = isPlayingFlow.value

    private val _repeatModeFlow = MutableStateFlow(Playback.RepeatMode.Off)
    val repeatModeFlow = _repeatModeFlow.asStateFlow()
    val repeatMode = repeatModeFlow.value

    private val _shuffleModeFlow = MutableStateFlow(Playback.ShuffleMode.Off)
    val shuffleModeFlow = _shuffleModeFlow.asStateFlow()
    val shuffleMode = shuffleModeFlow.value

    private val _currentSongFlow = MutableStateFlow(Song.emptySong)
    val currentSongFlow = _currentSongFlow.asStateFlow()
    val currentSong = currentSongFlow.value

    private var progressObserver: Job? = null

    private fun startProgressObserver() {
        progressObserver = viewModelScope.launch {
            while (isActive) {
                if (_isPlayingFlow.value) {
                    _progressFlow.value = mediaController?.playbackState?.position ?: 0L
                    delay(100L)
                } else {
                    delay(300L)
                }
            }
        }
    }

    private fun stopProgressObserver() {
        progressObserver?.cancel()
        progressObserver = null
    }

    fun setMediaController(controller: MediaControllerCompat?) {
        mediaController?.unregisterCallback(controllerCallback)
        mediaController = controller
        controller?.registerCallback(controllerCallback)
    }

    fun togglePlayPause() {
        val currentlyPlaying = _isPlayingFlow.value
        if (currentlyPlaying) {
            transportControls?.pause()
        } else {
            transportControls?.play()
        }
    }

    fun playNext() {
        transportControls?.skipToNext()
    }

    fun playPrevious() {
        transportControls?.skipToPrevious()
    }

    fun fastForward() {
        transportControls?.fastForward()
    }

    fun rewind() {
        transportControls?.rewind()
    }

    fun seekTo(positionMillis: Long) {
        transportControls?.seekTo(positionMillis)
    }

    fun updateSong(song: Song) {
        _currentSongFlow.value = song
    }

    override fun onCleared() {
        stopProgressObserver()
        mediaController?.unregisterCallback(controllerCallback)
        super.onCleared()
    }
}
