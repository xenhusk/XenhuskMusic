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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.mardous.booming.R
import com.mardous.booming.extensions.showToast
import com.mardous.booming.service.MultiPlayer
import com.mardous.booming.service.MusicService
import com.mardous.booming.service.constants.ServiceAction
import com.mardous.booming.service.equalizer.EqualizerManager
import com.mardous.booming.util.Preferences

class PlaybackManager(
    private val context: Context,
    private val equalizerManager: EqualizerManager
) : AudioManager.OnAudioFocusChangeListener {

    private val audioManager: AudioManager? = context.getSystemService()

    private var becomingNoisyReceiverRegistered = false
    private val becomingNoisyReceiverIntentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != null && intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (Preferences.isPauseOnDisconnect(false)) {
                    val serviceIntent = Intent(context, MusicService::class.java)
                        .setAction(ServiceAction.ACTION_PAUSE)
                    context.startService(serviceIntent)
                }
            }
        }
    }

    private val audioFocusRequest: AudioFocusRequestCompat =
        AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(this)
            .setAudioAttributes(
                AudioAttributesCompat.Builder()
                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                    .build()
            ).build()

    var isGaplessPlayback = false
    private var isPausedByTransientLossOfFocus = false

    private var playback: Playback? = null

    private val equalizerEnabled: Boolean
        get() = equalizerManager.eqState.isUsable

    init {
        playback = MultiPlayer(context)
        isGaplessPlayback = Preferences.gaplessPlayback
    }

    fun getAudioSessionId(): Int = playback?.getAudioSessionId() ?: AudioEffect.ERROR_BAD_VALUE

    fun getPlaybackSpeed(): Float = playback?.getSpeed() ?: 1f

    @RequiresApi(Build.VERSION_CODES.P)
    fun getRoutedDevice(): AudioDeviceInfo? = playback?.getRoutedDevice()

    fun getSongDurationMillis(): Int = playback?.duration() ?: -1

    fun getSongProgressMillis(): Int = playback?.position() ?: -1

    fun isPlaying(): Boolean = playback?.isPlaying() == true

    fun mayResume(): Boolean = isPausedByTransientLossOfFocus

    fun setCallbacks(callbacks: Playback.PlaybackCallbacks) {
        playback?.setCallbacks(callbacks)
    }

    fun play(onNotInitialized: () -> Unit) {
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

                registerBecomingNoisyReceiver()
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

    fun pause(onPause: () -> Unit) {
        if (playback != null && playback!!.isPlaying()) {
            playback?.pause()
            unregisterBecomingNoisyReceiver()
            closeAudioEffectSession(false)
            onPause()
        }
    }

    fun seek(millis: Int) {
        playback?.seek(millis)
    }

    fun setDataSource(path: String, completion: (success: Boolean) -> Unit) {
        playback?.setDataSource(path, completion)
    }

    fun setNextDataSource(path: String?) {
        playback?.setNextDataSource(path)
    }

    fun setReplayGain(rg: Float) {
        playback?.setReplayGain(rg)
    }

    fun openAudioEffectSession(internal: Boolean) {
        equalizerManager.openAudioEffectSession(getAudioSessionId(), internal)
    }

    fun closeAudioEffectSession(internal: Boolean) {
        equalizerManager.closeAudioEffectSession(getAudioSessionId(), internal)
    }

    fun updateBalance() {
        playback?.setBalance(equalizerManager.balanceLeft, equalizerManager.balanceRight)
    }

    fun updateTempo() {
        playback?.setTempo(equalizerManager.speed, equalizerManager.pitch)
    }

    fun release() {
        equalizerManager.release()
        playback?.release()
        playback = null
        abandonFocus()
        unregisterBecomingNoisyReceiver()
        closeAudioEffectSession(true)
    }

    private fun unregisterBecomingNoisyReceiver() {
        if (becomingNoisyReceiverRegistered) {
            context.unregisterReceiver(becomingNoisyReceiver)
            becomingNoisyReceiverRegistered = false
        }
    }

    private fun registerBecomingNoisyReceiver() {
        if (!becomingNoisyReceiverRegistered) {
            ContextCompat.registerReceiver(context, becomingNoisyReceiver, becomingNoisyReceiverIntentFilter, ContextCompat.RECEIVER_EXPORTED)
            becomingNoisyReceiverRegistered = true
        }
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
                    playback?.getCallbacks()?.onPlayStateChanged()
                    isPausedByTransientLossOfFocus = false
                }
                playback?.setDuckingFactor(Volume.NORMAL)
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                // Lost focus for an unbounded amount of time: stop playback and release media playback
                if (!Preferences.ignoreAudioFocus) {
                    playback?.pause()
                    playback?.getCallbacks()?.onPlayStateChanged()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media playback because playback
                // is likely to resume
                if (Preferences.pauseOnTransientFocusLoss) {
                    val wasPlaying = isPlaying()
                    playback?.pause()
                    playback?.getCallbacks()?.onPlayStateChanged()
                    isPausedByTransientLossOfFocus = wasPlaying
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (Preferences.audioDucking) {
                    playback?.setDuckingFactor(Volume.DUCK)
                }
            }
        }
    }

    object Volume {
        /**
         * The volume we set the media player to when we lose audio focus, but are
         * allowed to reduce the volume instead of stopping playback.
         */
        const val DUCK = 0.2f

        /** The volume we set the media player when we have audio focus.  */
        const val NORMAL = 1.0f
    }
}