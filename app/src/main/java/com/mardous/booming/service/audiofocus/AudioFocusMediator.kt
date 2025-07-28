package com.mardous.booming.service.audiofocus

import android.content.Context
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
import androidx.core.content.getSystemService
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.mardous.booming.service.playback.PlaybackManager

class AudioFocusMediator(
    context: Context,
    private val playbackManager: PlaybackManager,
    private val listener: AudioFocusListener? = (context as? AudioFocusListener)
) : AudioManager.OnAudioFocusChangeListener {

    private val focusLock = Any()

    private val audioManager by lazy { context.getSystemService<AudioManager>() }
    private val audioFocusRequest: AudioFocusRequestCompat =
        AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(this)
            .setAudioAttributes(
                AudioAttributesCompat.Builder()
                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                    .build()
            ).build()

    var mayResumeOnFocusGain = false
        private set

    fun requestFocus(): Boolean {
        return audioManager?.let {
            AudioManagerCompat.requestAudioFocus(it, audioFocusRequest) == AUDIOFOCUS_REQUEST_GRANTED
        } ?: false
    }

    fun abandonFocus() {
        audioManager?.let {
            AudioManagerCompat.abandonAudioFocusRequest(it, audioFocusRequest)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                listener?.audioFocusGain(mayResumeOnFocusGain)
                synchronized(focusLock) {
                    mayResumeOnFocusGain = false
                }
            }

            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val isTransient = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                val paused = listener?.audioFocusLoss(isTransient) ?: false
                synchronized(focusLock) {
                    mayResumeOnFocusGain = playbackManager.isPlaying() && isTransient && paused
                }
            }
        }
    }

    interface AudioFocusListener {
        fun audioFocusGain(isPausedByTransientLossOfFocus: Boolean)
        fun audioFocusLoss(isTransient: Boolean): Boolean
    }
}