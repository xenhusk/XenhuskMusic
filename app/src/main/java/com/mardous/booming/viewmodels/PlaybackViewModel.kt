package com.mardous.booming.viewmodels

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
            _isPlaying.value = state?.state == PlaybackStateCompat.STATE_PLAYING
            if (progressObserver == null) {
                _progressFlow.value = state?.position ?: 0
            }
            if (isPlaying.value) {
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
            _repeatMode.value = Playback.RepeatMode.fromValue(repeatMode)
        }

        override fun onShuffleModeChanged(shuffleMode: Int) {
            _shuffleMode.value = Playback.ShuffleMode.fromValue(shuffleMode)
        }
    }

    private val _progressFlow = MutableStateFlow(0L)
    val progressFlow = _progressFlow.asStateFlow()

    private val _durationFlow = MutableStateFlow(0L)
    val durationFlow = _durationFlow.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _repeatMode = MutableStateFlow(Playback.RepeatMode.Off)
    val repeatMode = _repeatMode.asStateFlow()

    private val _shuffleMode = MutableStateFlow(Playback.ShuffleMode.Off)
    val shuffleMode = _shuffleMode.asStateFlow()

    private var progressObserver: Job? = null

    private fun startProgressObserver() {
        progressObserver = viewModelScope.launch {
            while (isActive) {
                if (_isPlaying.value) {
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
        val currentlyPlaying = _isPlaying.value
        if (currentlyPlaying) {
            transportControls?.pause()
        } else {
            transportControls?.play()
        }
    }

    fun playNext() {
        transportControls?.skipToNext()
    }

    fun back() {
        transportControls?.skipToPrevious()
    }

    fun seekTo(positionMillis: Long) {
        transportControls?.seekTo(positionMillis)
    }

    override fun onCleared() {
        stopProgressObserver()
        mediaController?.unregisterCallback(controllerCallback)
        super.onCleared()
    }
}
