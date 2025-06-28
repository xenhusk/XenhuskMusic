package com.mardous.booming.viewmodels.player

import android.content.Context
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.lifecycle.*
import com.mardous.booming.fragments.player.PlayerColorScheme
import com.mardous.booming.fragments.player.PlayerColorSchemeMode
import com.mardous.booming.helper.color.MediaNotificationProcessor
import com.mardous.booming.model.Song
import com.mardous.booming.service.constants.SessionCommand
import com.mardous.booming.service.playback.Playback
import com.mardous.booming.viewmodels.player.model.PlayerProgress
import com.mardous.booming.viewmodels.player.model.SaveCoverResult
import com.mardous.booming.worker.SaveCoverWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PlayerViewModel(
    private val saveCoverWorker: SaveCoverWorker
) : ViewModel() {

    private var mediaController: MediaControllerCompat? = null
    private val transportControls get() = mediaController?.transportControls
    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            _isPlayingFlow.value = state?.state == PlaybackStateCompat.STATE_PLAYING
            if (progressObserver == null) {
                _songProgressFlow.value = state?.position ?: 0
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
            _songDurationFlow.value = mediaMetadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatModeFlow.value = Playback.RepeatMode.fromValue(repeatMode)
        }

        override fun onShuffleModeChanged(shuffleMode: Int) {
            _shuffleModeFlow.value = Playback.ShuffleMode.fromValue(shuffleMode)
        }
    }

    private val _songProgressFlow = MutableStateFlow(0L)
    val songProgressFlow = _songProgressFlow.asStateFlow()

    private val _songDurationFlow = MutableStateFlow(0L)
    val songDurationFlow = _songDurationFlow.asStateFlow()

    val progressFlow = combine(_songProgressFlow, _songDurationFlow) { progress, total ->
        PlayerProgress(progress, total)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = PlayerProgress.Unspecified
    )

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow = _isPlayingFlow.asStateFlow()
    val isPlaying get() = isPlayingFlow.value

    private val _repeatModeFlow = MutableStateFlow(Playback.RepeatMode.Off)
    val repeatModeFlow = _repeatModeFlow.asStateFlow()
    val repeatMode get() = repeatModeFlow.value

    private val _shuffleModeFlow = MutableStateFlow(Playback.ShuffleMode.Off)
    val shuffleModeFlow = _shuffleModeFlow.asStateFlow()
    val shuffleMode get() = shuffleModeFlow.value

    private val _currentSongFlow = MutableStateFlow(Song.emptySong)
    val currentSongFlow = _currentSongFlow.asStateFlow()
    val currentSong get() = currentSongFlow.value

    private val _colorScheme = MutableLiveData<PlayerColorScheme>()
    val colorSchemeObservable: LiveData<PlayerColorScheme> = _colorScheme
    val colorSchemeFlow = _colorScheme.asFlow()
    val colorScheme get() = colorSchemeObservable.value

    private var progressObserver: Job? = null

    private fun startProgressObserver() {
        progressObserver = viewModelScope.launch {
            while (isActive) {
                if (_isPlayingFlow.value) {
                    _songProgressFlow.value = mediaController?.playbackState?.position ?: 0L
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

    fun cycleRepeatMode() {
        transportControls?.sendCustomAction(SessionCommand.TOGGLE_SHUFFLE, null)
    }

    fun toggleShuffleMode() {
        transportControls?.sendCustomAction(SessionCommand.CYCLE_REPEAT, null)
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

    fun loadColorScheme(
        context: Context,
        mode: PlayerColorScheme.Mode,
        mediaColor: MediaNotificationProcessor
    ) = viewModelScope.launch(Dispatchers.IO) {
        val currentScheme = colorScheme?.mode?.takeIf { it == PlayerColorSchemeMode.AppTheme }
        if (currentScheme == mode)
            return@launch

        val result = runCatching {
            PlayerColorScheme.autoColorScheme(context, mediaColor, mode)
        }
        if (result.isSuccess) {
            _colorScheme.postValue(result.getOrThrow())
        } else if (result.isFailure) {
            Log.e(TAG, "Failed to load color scheme", result.exceptionOrNull())
        }
    }

    fun saveCover(song: Song) = liveData {
        emit(SaveCoverResult(true))
        val uri = saveCoverWorker.saveArtwork(song)
        emit(SaveCoverResult(false, uri))
    }

    override fun onCleared() {
        stopProgressObserver()
        mediaController?.unregisterCallback(controllerCallback)
        super.onCleared()
    }

    companion object {
        private const val TAG = "PlayerViewModel"
    }
}