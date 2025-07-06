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

package com.mardous.booming.service.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.mardous.booming.R
import com.mardous.booming.audio.SoundSettings
import com.mardous.booming.extensions.showToast
import com.mardous.booming.model.Song
import com.mardous.booming.service.CrossFadePlayer
import com.mardous.booming.service.MultiPlayer
import com.mardous.booming.service.equalizer.EqualizerManager
import com.mardous.booming.util.Preferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackManager(
    private val context: Context,
    private val equalizerManager: EqualizerManager,
    private val soundSettings: SoundSettings
) : AudioManager.OnAudioFocusChangeListener {

    private val _progressFlow = MutableStateFlow(-1L)
    val progressFlow = _progressFlow.asStateFlow()

    private val audioManager: AudioManager? = context.getSystemService()
    private val audioFocusRequest: AudioFocusRequestCompat =
        AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(this)
            .setAudioAttributes(
                AudioAttributesCompat.Builder()
                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                    .build()
            ).build()

    var pendingQuit = false
    var gaplessPlayback = Preferences.gaplessPlayback

    private var isPausedByTransientLossOfFocus = false
    private var playback: Playback? = null
    private val equalizerEnabled: Boolean
        get() = equalizerManager.eqState.isUsable

    private var progressObserver: Job? = null
    val isProgressObserverRunning: Boolean
        get() = progressObserver?.isActive == true

    private fun createLocalPlayback(): Playback {
        // Set MultiPlayer when crossfade duration is 0 i.e. off
        return if (Preferences.crossFadeDuration == 0) {
            MultiPlayer(context)
        } else {
            CrossFadePlayer(context)
        }
    }

    fun initialize(callbacks: Playback.PlaybackCallbacks, coroutineScope: CoroutineScope) {
        playback = createLocalPlayback()
        playback?.setCallbacks(callbacks)
        coroutineScope.launch {
            soundSettings.balanceFlow.collect { balance ->
                updateBalance(balance.value.left, balance.value.right)
            }
        }
        coroutineScope.launch {
            soundSettings.tempoFlow.collect { tempo ->
                updateTempo(tempo.value.speed, tempo.value.actualPitch)
            }
        }
    }

    fun getAudioSessionId(): Int = playback?.getAudioSessionId() ?: AudioEffect.ERROR_BAD_VALUE

    fun getPlaybackSpeed(): Float = playback?.getSpeed() ?: 1f

    @RequiresApi(Build.VERSION_CODES.P)
    fun getRoutedDevice(): AudioDeviceInfo? = playback?.getRoutedDevice()

    fun isPlaying(): Boolean = playback?.isPlaying() == true

    fun mayResume(): Boolean = isPausedByTransientLossOfFocus

    fun play(coroutineScope: CoroutineScope, onNotInitialized: () -> Unit) {
        if (!requestFocus()) {
            context.showToast(R.string.audio_focus_denied)
            return
        }
        if (playback != null && !playback!!.isPlaying()) {
            if (!playback!!.isInitialized()) {
                onNotInitialized()
            } else {
                playback?.start()

                updateBalance()
                updateTempo()

                if (!isProgressObserverRunning) {
                    startProgressObserver(coroutineScope)
                }

                if (equalizerEnabled) {
                    //Shutdown any existing external audio sessions
                    closeAudioEffectSession(false)

                    //Start internal equalizer session (will only turn on if enabled)
                    openAudioEffectSession(true)
                } else {
                    openAudioEffectSession(false)
                }
            }
        }
    }

    fun pause() {
        stopProgressObserver()
        if (playback != null && playback!!.isPlaying()) {
            playback?.pause()
            closeAudioEffectSession(false)
        }
    }

    fun position() = playback?.position() ?: -1

    fun duration(): Int = playback?.duration() ?: -1

    fun seek(millis: Int, force: Boolean) {
        if (!isProgressObserverRunning) {
            _progressFlow.value = millis.toLong()
        }
        playback?.seek(millis, force)
    }

    fun setCallbacks(callbacks: Playback.PlaybackCallbacks) {
        playback?.setCallbacks(callbacks)
    }

    fun setDataSource(song: Song, force: Boolean, completion: (success: Boolean) -> Unit) {
        playback?.setDataSource(song, force, completion)
    }

    fun setNextDataSource(song: Song?) {
        playback?.setNextDataSource(song)
    }

    fun setCrossFadeDuration(duration: Int) {
        playback?.setCrossFadeDuration(duration)
    }

    fun setCrossFadeNextDataSource(song: Song) {
        if (playback is CrossFadePlayer) {
            playback?.setNextDataSource(song)
        }
    }

    fun setReplayGain(rg: Float) {
        playback?.setReplayGain(rg)
    }

    fun maybeSwitchToCrossFade(crossFadeDuration: Int): Boolean {
        /* Switch to MultiPlayer if CrossFade duration is 0 and
           Playback is not an instance of MultiPlayer
        */
        if (playback !is MultiPlayer && crossFadeDuration == 0) {
            if (playback != null) {
                playback?.release()
            }
            playback = null
            playback = MultiPlayer(context)
            return true
        } else if (playback !is CrossFadePlayer && crossFadeDuration > 0) {
            if (playback != null) {
                playback?.release()
            }
            playback = null
            playback = CrossFadePlayer(context)
            playback?.setCrossFadeDuration(crossFadeDuration)
            return true
        }
        return false
    }

    fun openAudioEffectSession(internal: Boolean) {
        equalizerManager.openAudioEffectSession(getAudioSessionId(), internal)
    }

    fun closeAudioEffectSession(internal: Boolean) {
        equalizerManager.closeAudioEffectSession(getAudioSessionId(), internal)
    }

    fun updateBalance(
        left: Float = soundSettings.balance.left,
        right: Float = soundSettings.balance.right
    ) {
        playback?.setBalance(left, right)
    }

    fun updateTempo(
        speed: Float = soundSettings.tempo.speed,
        pitch: Float = soundSettings.tempo.actualPitch
    ) {
        playback?.setTempo(speed, pitch)
    }

    fun release() {
        progressObserver?.cancel()
        progressObserver = null
        equalizerManager.release()
        playback?.release()
        playback = null
        abandonFocus()
        closeAudioEffectSession(true)
    }

    private fun startProgressObserver(coroutineScope: CoroutineScope) {
        progressObserver = coroutineScope.launch(Dispatchers.Default) {
            while (isActive) {
                if (isPlaying()) {
                    _progressFlow.value = position().toLong()
                    playback?.setProgressState(position(), duration())
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

    private fun requestFocus(): Boolean {
        return AudioManagerCompat.requestAudioFocus(
            audioManager!!,
            audioFocusRequest
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager!!, audioFocusRequest)
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (!isPlaying() && isPausedByTransientLossOfFocus) {
                    playback?.start()
                    isPausedByTransientLossOfFocus = false
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                // Lost focus for an unbounded amount of time: stop playback and release media playback
                if (!Preferences.ignoreAudioFocus) {
                    pause()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media playback because playback
                // is likely to resume
                if (Preferences.pauseOnTransientFocusLoss) {
                    val wasPlaying = isPlaying()
                    pause()
                    isPausedByTransientLossOfFocus = wasPlaying
                }
            }
        }
    }
}