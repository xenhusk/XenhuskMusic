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

package com.mardous.booming.service.equalizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.audiofx.AudioEffect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.mardous.booming.model.EQPreset;
import com.mardous.booming.service.playback.Playback;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>This calls listen to events that affect DSP function and responds to them.</p>
 * <ol>
 * <li>new audio session declarations</li>
 * <li>headset plug / unplug events</li>
 * <li>preference update events.</li>
 * </ol>
 *
 * @author alankila
 */
public class PlaybackEQ {
    protected static final String TAG = PlaybackEQ.class.getSimpleName();

    private final Context context;

    /**
     * Send this action if you want to open an internal equalizer session.
     */
    private static final String ACTION_OPEN_EQUALIZER_SESSION = "com.mardous.booming.audiofx.OPEN_SESSION";
    /**
     * Send this action if you want to close an internal equalizer session.
     */
    private static final String ACTION_CLOSE_EQUALIZER_SESSION = "com.mardous.booming.audiofx.CLOSE_SESSION";

    private EqualizerManager equalizerManager;

    public PlaybackEQ(@NonNull Context context, @NonNull EqualizerManager equalizerManager) {
        this.context = context;
        this.equalizerManager = equalizerManager;

        IntentFilter audioFilter = new IntentFilter();
        audioFilter.addAction(ACTION_OPEN_EQUALIZER_SESSION);
        audioFilter.addAction(ACTION_CLOSE_EQUALIZER_SESSION);
        ContextCompat.registerReceiver(context, mAudioSessionReceiver, audioFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        saveDefaults();
    }

    public void reset() {
        saveDefaults();
        update();
    }

    public void release() {
        releaseEffects();

        context.unregisterReceiver(mAudioSessionReceiver);
        equalizerManager = null;
    }

    private void releaseEffects() {
        for (EffectSet effectSet : mAudioSessions.values()) {
            if (effectSet != null) {
                effectSet.release();
            }
        }
    }

    /**
     * Initializes all band levels to zero.
     *
     * @param length the number of bands.
     * @return a zeroed band levels string delimited by ";".
     */
    @NonNull
    public static String getZeroedBandsString(int length) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            stringBuilder.append("0");
            if (i < length - 1) {
                stringBuilder.append(";");
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Known audio sessions and their associated audioeffect suites.
     */
    private final Map<Integer, EffectSet> mAudioSessions = new ConcurrentHashMap<>();

    /**
     * Receive new broadcast intents for adding DSP to session
     */
    private final BroadcastReceiver mAudioSessionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            if (action.equals(ACTION_OPEN_EQUALIZER_SESSION)) {
                if (!mAudioSessions.containsKey(sessionId)) {
                    try {
                        EffectSet effectSet = new EffectSet(sessionId);
                        mAudioSessions.put(sessionId, effectSet);
                    } catch (Exception | ExceptionInInitializerError e) {
                        Log.e(TAG, "Failed to open EQ session.. EffectSet error ", e);
                    }
                }
            }
            if (action.equals(ACTION_CLOSE_EQUALIZER_SESSION)) {
                EffectSet gone = mAudioSessions.remove(sessionId);
                if (gone != null) {
                    gone.release();
                }
            }
            update();
        }
    };

    private void saveDefaults() {
        EffectSet temp;
        try {
            temp = new EffectSet(0);
        } catch (Exception | ExceptionInInitializerError | UnsatisfiedLinkError e) {
            releaseEffects();
            return;
        }

        if (!equalizerManager.isInitialized()) {
            equalizerManager.setDefaultPresets(temp);
            equalizerManager.setNumberOfBands(temp.getNumEqualizerBands());
            equalizerManager.setBandLevelRange(temp.equalizer.getBandLevelRange());
            equalizerManager.setCenterFreqs(temp);
            equalizerManager.setInitialized(true);
        }

        temp.release();
    }

    /**
     * Push new configuration to audio stack.
     */
    public synchronized void update() {
        try {
            for (Integer sessionId : mAudioSessions.keySet()) {
                updateDsp(mAudioSessions.get(sessionId));
            }
        } catch (NoSuchMethodError e) {
            e.printStackTrace();
        }
    }

    private void updateDsp(EffectSet session) {
        final boolean globalEnabled = equalizerManager.isEqualizerEnabled();

        try {
            final EQPreset eqPreset = equalizerManager.getCurrentPreset();
            if (eqPreset != null) {
                final short[] equalizerLevels = new short[eqPreset.getNumberOfBands()];
                for (int i = 0; i < eqPreset.getNumberOfBands(); i++) {
                    equalizerLevels[i] = eqPreset.getLevelShort(i);
                }

                session.enableEqualizer(globalEnabled);
                session.setEqualizerLevels(equalizerLevels);

                try {
                    if (globalEnabled && equalizerManager.isVirtualizerEnabled()) {
                        session.enableVirtualizer(true);
                        session.setVirtualizerStrength((short) equalizerManager.getVirtualizerStrength());
                    } else {
                        session.enableVirtualizer(false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error setting up virtualizer!", e);
                }

                try {
                    if (globalEnabled && equalizerManager.isBassBoostEnabled()) {
                        session.enableBassBoost(true);
                        session.setBassBoostStrength((short) equalizerManager.getBassStrength());
                    } else {
                        session.enableBassBoost(false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error setting up bass boost!", e);
                }

                try {
                    if (globalEnabled && equalizerManager.isPresetReverbEnabled()) {
                        session.enablePresetReverb(true);
                        session.setReverbPreset((short) equalizerManager.getPresetReverbPreset());
                    } else {
                        session.enablePresetReverb(false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error setting up preset reverb!", e);
                }

                try {
                    if (globalEnabled && equalizerManager.isLoudnessEnabled()) {
                        session.enableLoudness(true);
                        session.setLoudnessGain(equalizerManager.getLoudnessGain());
                    } else {
                        session.enableLoudness(false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error setting up loudness enhancer!", e);
                }
            } else {
                session.enableEqualizer(false);
                session.enableVirtualizer(false);
                session.enableBassBoost(false);
                session.enablePresetReverb(false);
                session.enableLoudness(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling equalizer!", e);
        }
    }

    public void updateBalance(@NonNull Playback playback) {
        playback.setBalance(equalizerManager.getBalanceLeft(), equalizerManager.getBalanceRight());
    }

    public void updateTempo(@NonNull Playback playback) {
        playback.setTempo(equalizerManager.getSpeed(), equalizerManager.getPitch());
    }

    /**
     * Sends a broadcast to close any existing audio effect sessions
     */
    public void closeEqualizerSessions(boolean internal, int audioSessionId) {
        Intent intent;
        if (internal) {
            //Close the internal audio session
            intent = new Intent(ACTION_CLOSE_EQUALIZER_SESSION);
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId);
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
            intent.setPackage(context.getPackageName());
        } else {
            intent = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId);
            context.sendBroadcast(intent);

            //Close any external audio sessions on session 0
            intent = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
        }
        context.sendBroadcast(intent);
    }

    public void openEqualizerSession(boolean internal, int audioSessionId) {
        final Intent intent = new Intent(internal ? ACTION_OPEN_EQUALIZER_SESSION : AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        if (internal) {
            intent.setPackage(context.getPackageName());
        }
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId);
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
        intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
        context.sendBroadcast(intent);
    }
}
