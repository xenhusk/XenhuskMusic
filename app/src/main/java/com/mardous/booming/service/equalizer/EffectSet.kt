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

import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;

/**
 * Helper class representing the full complement of effects attached to one
 * audio session.
 *
 * @author alankila
 */
public class EffectSet {
    /**
     * Session-specific equalizer
     */
    final Equalizer equalizer;
    private final BassBoost bassBoost;
    private final Virtualizer virtualizer;
    private final PresetReverb presetReverb;
    private final LoudnessEnhancer loudnessEnhancer;

    private short mEqNumPresets = -1;
    private short mEqNumBands = -1;

    EffectSet(int sessionId) {
        equalizer = new Equalizer(1, sessionId);
        bassBoost = new BassBoost(1, sessionId);
        virtualizer = new Virtualizer(1, sessionId);
        presetReverb = new PresetReverb(1, sessionId);
        loudnessEnhancer = new LoudnessEnhancer(sessionId);
    }

    /*
     * Take lots of care to not poke values that don't need
     * to be poked- this can cause audible pops.
     */
    void enableEqualizer(boolean enable) {
        if (enable != equalizer.getEnabled()) {
            if (!enable) {
                for (short i = 0; i < getNumEqualizerBands(); i++) {
                    equalizer.setBandLevel(i, (short) 0);
                }
            }
            equalizer.setEnabled(enable);
        }
    }

    void setEqualizerLevels(short[] levels) {
        if (equalizer.getEnabled()) {
            for (short i = 0; i < levels.length; i++) {
                if (equalizer.getBandLevel(i) != levels[i]) {
                    equalizer.setBandLevel(i, levels[i]);
                }
            }
        }
    }

    short getNumEqualizerBands() {
        if (mEqNumBands < 0) {
            mEqNumBands = equalizer.getNumberOfBands();
        }
        if (mEqNumBands > 6) {
            mEqNumBands = 6;
        }
        return mEqNumBands;
    }

    short getNumEqualizerPresets() {
        if (mEqNumPresets < 0) {
            mEqNumPresets = equalizer.getNumberOfPresets();
        }
        return mEqNumPresets;
    }

    void enableBassBoost(boolean enable) {
        if (enable != bassBoost.getEnabled()) {
            if (!enable) {
                bassBoost.setStrength((short) 1);
                bassBoost.setStrength((short) 0);
            }
            bassBoost.setEnabled(enable);
        }
    }

    void setBassBoostStrength(short strength) {
        if (bassBoost.getEnabled() && bassBoost.getRoundedStrength() != strength) {
            bassBoost.setStrength(strength);
        }
    }

    void enableVirtualizer(boolean enable) {
        if (enable != virtualizer.getEnabled()) {
            if (!enable) {
                virtualizer.setStrength((short) 1);
                virtualizer.setStrength((short) 0);
            }
            virtualizer.setEnabled(enable);
        }
    }

    void setVirtualizerStrength(short strength) {
        if (virtualizer.getEnabled() && virtualizer.getRoundedStrength() != strength) {
            virtualizer.setStrength(strength);
        }
    }

    void enablePresetReverb(boolean enable) {
        if (enable != presetReverb.getEnabled()) {
            if (!enable) {
                presetReverb.setPreset(PresetReverb.PRESET_NONE);
            }
            presetReverb.setEnabled(enable);
        }
    }

    void setReverbPreset(short preset) {
        if (presetReverb.getEnabled() && presetReverb.getPreset() != preset) {
            presetReverb.setPreset(preset);
        }
    }

    void enableLoudness(boolean enable) {
        if (enable != loudnessEnhancer.getEnabled()) {
            if (!enable) {
                loudnessEnhancer.setTargetGain(0);
            }
            loudnessEnhancer.setEnabled(enable);
        }
    }

    void setLoudnessGain(int gainmDB) {
        if (loudnessEnhancer.getEnabled() && loudnessEnhancer.getTargetGain() != gainmDB) {
            loudnessEnhancer.setTargetGain(gainmDB);
        }
    }

    public void release() {
        equalizer.release();
        bassBoost.release();
        virtualizer.release();
        presetReverb.release();
        loudnessEnhancer.release();
    }
}
