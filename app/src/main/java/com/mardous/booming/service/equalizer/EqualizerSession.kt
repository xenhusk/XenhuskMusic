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
package com.mardous.booming.service.equalizer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.concurrent.ConcurrentHashMap

open class EqualizerSession(
    private val context: Context,
    private val equalizerManager: EqualizerManager
) {

    /**
     * Known audio sessions and their associated audioeffect suites.
     */
    private val mAudioSessions: MutableMap<Int?, EffectSet?> = ConcurrentHashMap<Int?, EffectSet?>()

    /**
     * Receive new broadcast intents for adding DSP to session
     */
    private val mAudioSessionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action ?: return
            val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
            if (action == ACTION_OPEN_EQUALIZER_SESSION) {
                if (!mAudioSessions.containsKey(sessionId)) {
                    val result = runCatching {
                        mAudioSessions.put(sessionId, EffectSet(sessionId))
                    }
                    if (result.isFailure) {
                        Log.e(TAG, "Failed to open EQ session.. EffectSet error ", result.exceptionOrNull())
                    }
                }
            }
            if (action == ACTION_CLOSE_EQUALIZER_SESSION) {
                mAudioSessions.remove(sessionId)?.release()
            }
            update()
        }
    }

    init {
        val audioFilter = IntentFilter().apply {
            addAction(ACTION_OPEN_EQUALIZER_SESSION)
            addAction(ACTION_CLOSE_EQUALIZER_SESSION)
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(mAudioSessionReceiver, audioFilter)
    }

    /**
     * Push new configuration to audio stack.
     */
    @Synchronized
    fun update() {
        try {
            for (sessionId in mAudioSessions.keys) {
                updateDsp(mAudioSessions[sessionId]!!)
            }
        } catch (e: NoSuchMethodError) {
            e.printStackTrace()
        }
    }

    fun release() {
        releaseEffects()
        LocalBroadcastManager.getInstance(context).unregisterReceiver(mAudioSessionReceiver)
    }

    /**
     * Sends a broadcast to close any existing audio effect sessions
     */
    fun closeEqualizerSessions(internal: Boolean, audioSessionId: Int) {
        val action = if (internal) ACTION_CLOSE_EQUALIZER_SESSION else AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
        val intent = Intent(action)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
        if (internal) {
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        } else {
            context.sendBroadcast(intent)
        }
        if (!internal && audioSessionId != 0) {
            closeEqualizerSessions(false, 0)
        }
    }

    fun openEqualizerSession(internal: Boolean, audioSessionId: Int) {
        val action = if (internal) ACTION_OPEN_EQUALIZER_SESSION else AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
        val intent = Intent(action)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        if (internal) {
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        } else {
            context.sendBroadcast(intent)
        }
    }

    private fun updateDsp(session: EffectSet) {
        try {
            val globalEnabled = equalizerManager.eqState.isUsable
            if (globalEnabled) {
                val eqPreset = equalizerManager.currentPreset
                val equalizerLevels = ShortArray(eqPreset.numberOfBands)
                for (i in 0..<eqPreset.numberOfBands) {
                    equalizerLevels[i] = eqPreset.getLevelShort(i)
                }

                session.enableEqualizer(true)
                session.setEqualizerLevels(equalizerLevels)

                try {
                    val virtualizerState = equalizerManager.virtualizerState
                    if (virtualizerState.isUsable) {
                        session.enableVirtualizer(true)
                        session.setVirtualizerStrength(virtualizerState.value.toInt().toShort())
                    } else {
                        session.enableVirtualizer(false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up virtualizer!", e)
                }

                try {
                    val bassBoostState = equalizerManager.bassBoostState
                    if (bassBoostState.isUsable) {
                        session.enableBassBoost(true)
                        session.setBassBoostStrength(bassBoostState.value.toInt().toShort())
                    } else {
                        session.enableBassBoost(false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up bass boost!", e)
                }

                try {
                    val presetReverbState = equalizerManager.presetReverbState
                    if (presetReverbState.isUsable) {
                        session.enablePresetReverb(true)
                        session.setReverbPreset(presetReverbState.value.toShort())
                    } else {
                        session.enablePresetReverb(false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up preset reverb!", e)
                }

                try {
                    val loudnessGainState = equalizerManager.loudnessGainState
                    if (loudnessGainState.isUsable) {
                        session.enableLoudness(true)
                        session.setLoudnessGain(loudnessGainState.value.toInt())
                    } else {
                        session.enableLoudness(false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up loudness enhancer!", e)
                }
            } else {
                session.enableEqualizer(false)
                session.enableVirtualizer(false)
                session.enableBassBoost(false)
                session.enablePresetReverb(false)
                session.enableLoudness(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling equalizer!", e)
        }
    }

    private fun releaseEffects() {
        for (effectSet in mAudioSessions.values) {
            effectSet?.release()
        }
    }

    companion object {
        protected val TAG: String = EqualizerSession::class.java.getSimpleName()

        /**
         * Send this action if you want to open an internal equalizer session.
         */
        private const val ACTION_OPEN_EQUALIZER_SESSION = "com.mardous.booming.audiofx.OPEN_SESSION"

        /**
         * Send this action if you want to close an internal equalizer session.
         */
        private const val ACTION_CLOSE_EQUALIZER_SESSION =
            "com.mardous.booming.audiofx.CLOSE_SESSION"

        /**
         * Initializes all band levels to zero.
         *
         * @param length the number of bands.
         * @return a zeroed band levels string delimited by ";".
         */
        fun getZeroedBandsString(length: Int): String {
            val stringBuilder = StringBuilder()
            for (i in 0..<length) {
                stringBuilder.append("0")
                if (i < length - 1) {
                    stringBuilder.append(";")
                }
            }
            return stringBuilder.toString()
        }
    }
}
