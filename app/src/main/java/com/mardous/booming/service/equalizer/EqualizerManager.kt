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

import android.annotation.SuppressLint
import android.content.Context
import android.media.audiofx.AudioEffect
import androidx.annotation.FloatRange
import androidx.core.content.edit
import com.mardous.booming.extensions.files.getFormattedFileName
import com.mardous.booming.interfaces.IEQInterface
import com.mardous.booming.model.EQPreset
import com.mardous.booming.model.EQPreset.Companion.getEmptyPreset
import com.mardous.booming.service.MusicPlayer
import com.mardous.booming.util.PLAYBACK_PITCH
import com.mardous.booming.util.PLAYBACK_SPEED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID

/**
 * @author Christians M. A. (mardous)
 */
class EqualizerManager internal constructor(context: Context) {

    private val mPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val boundInterfaces = ArrayList<IEQInterface?>()
    private var presets: MutableList<EQPreset>? = null
    private var eqPreset: EQPreset? = null

    var isEqualizerSupported = false
        private set
    var isVirtualizerSupported = false
        private set
    var isBassBoostSupported = false
        private set
    var isLoudnessEnhancerSupported = false
        private set
    var isPresetReverbSupported = false
        private set

    var isEqualizerEnabled: Boolean
        get() = isEqualizerSupported && mPreferences.getBoolean(Keys.GLOBAL_ENABLED, false)
        set(equalizerEnabled) {
            mPreferences.edit {
                putBoolean(Keys.GLOBAL_ENABLED, isEqualizerSupported && equalizerEnabled)
            }

            callInterfaces { it.eqStateChanged(isEqualizerSupported && equalizerEnabled, false) }
        }

    private fun callInterfaces(function: (IEQInterface) -> Unit) {
        boundInterfaces.forEach { it?.let(function) }
    }

    private fun requestState(isBeingReset: Boolean) {
        callInterfaces { it.eqStateChanged(isEqualizerSupported && isEqualizerEnabled, isBeingReset) }
    }

    fun requestState() {
        requestState(false)
    }

    fun requestPresetsList() {
        callInterfaces { it.eqPresetListChanged(getEqualizerPresets()) }
    }

    fun requestCurrentPreset() {
        callInterfaces { it.eqPresetChanged(null, getCurrentPreset()) }
    }

    @Synchronized
    fun resetConfiguration() {
        mPreferences.edit {
            putBoolean(Keys.IS_INITIALIZED, false)
            putBoolean(Keys.GLOBAL_ENABLED, false)
            putBoolean(Keys.LOUDNESS_ENABLED, false)
            putBoolean(Keys.PRESET_REVERB_ENABLED, false)
            remove(Keys.PRESETS)
            remove(Keys.PRESET)
            remove(Keys.CUSTOM_PRESET)
            remove(Keys.BAND_LEVEL_RANGE)
            remove(Keys.LOUDNESS_GAIN)
            remove(Keys.PRESET_REVERB_PRESET)
        }

        eqPreset = null
        presets = null

        MusicPlayer.resetEqualizer()

        requestState(true)
        requestPresetsList()
        requestCurrentPreset()
    }

    var isInitialized: Boolean
        get() = mPreferences.getBoolean(Keys.IS_INITIALIZED, false)
        set(value) = mPreferences.edit {
            putBoolean(Keys.IS_INITIALIZED, value)
        }

    fun registerInterface(eqInterface: IEQInterface?) {
        if (!boundInterfaces.contains(eqInterface)) {
            boundInterfaces.add(eqInterface)
        }
    }

    fun unregisterInterface(eqInterface: IEQInterface?) {
        boundInterfaces.remove(eqInterface)
    }

    @Synchronized
    fun isPresetNameAvailable(presetName: String): Boolean {
        for ((name) in getEqualizerPresets()) {
            if (name.equals(presetName, ignoreCase = true)) return false
        }
        return true
    }

    fun getNewExportName(): String = getFormattedFileName("BoomingEQ", "json")

    @Synchronized
    private fun getCustomPresetFromCurrent(): EQPreset {
        return getCurrentPreset()?.let { EQPreset(it, CUSTOM_PRESET_NAME, true) }
            ?: getEmptyPreset(CUSTOM_PRESET_NAME, true, numberOfBands)
    }

    fun getNewPresetFromCustom(presetName: String): EQPreset {
        return getCustomPreset().copy(name = presetName, isCustom = false)
    }

    @Synchronized
    fun getEqualizerPresets(): MutableList<EQPreset> {
        return presets ?: run {
            val json = mPreferences.getString(Keys.PRESETS, null).orEmpty()
            presets = if (json.isBlank()) {
                arrayListOf()
            } else {
                Json.decodeFromString<List<EQPreset>>(json).toMutableList()
            }
            presets!!
        }
    }

    @Synchronized
    fun getEqualizerPresetsWithCustom(presets: List<EQPreset> = getEqualizerPresets()) =
        ArrayList(presets).apply {
            add(getCustomPreset())
        }

    @Synchronized
    fun renamePreset(preset: EQPreset, newName: String): Boolean {
        if (newName.trim().isEmpty())
            return false

        val temp = getEqualizerPresets()
        for ((name) in temp) {
            if (name.equals(newName, ignoreCase = true)) {
                return false
            }
        }

        val newPreset = preset.copy(name = newName)
        val index = temp.indexOfFirst { it.name == preset.name }
        if (index > -1) {
            temp[index] = newPreset
            setEqualizerPresets(temp, true)
            if (preset == getCurrentPreset()) {
                setCurrentPreset(newPreset)
            }
            return true
        }
        return false
    }

    @Synchronized
    fun addPreset(preset: EQPreset, allowReplace: Boolean, usePreset: Boolean): Boolean {
        if (!preset.isValid)
            return false

        val temp = getEqualizerPresets()
        for (i in temp.indices) {
            val value = temp[i]
            if (value.name.equals(preset.name, ignoreCase = true)) {
                if (allowReplace) { // copy bands and effects
                    temp[i] = preset
                    setEqualizerPresets(temp, true)
                    if (usePreset) {
                        setCurrentPreset(preset)
                    }
                    return true
                }
                return false
            }
        }

        if (temp.add(preset)) {
            setEqualizerPresets(temp, true)
            if (usePreset) {
                setCurrentPreset(preset)
            }
            return true
        }

        return false
    }

    @Synchronized
    fun removePreset(preset: EQPreset): Boolean {
        val temp = getEqualizerPresets()
        if (temp.removeIf { it.name == preset.name }) {
            if (preset == getCurrentPreset()) {
                setCurrentPreset(getCustomPreset())
            }
            setEqualizerPresets(temp, true)
            return true
        }
        return false
    }

    @Synchronized
    fun importPresets(toImport: List<EQPreset>): Int {
        var imported = 0
        val numberOfBands = numberOfBands
        val temp = getEqualizerPresets()
        for (importing in toImport) {
            if (!importing.isValid || importing.numberOfBands != numberOfBands)
                continue

            val existent = temp.firstOrNull { it.name.equals(importing.name, ignoreCase = true) }
            if (existent != null) {
                val index = temp.indexOf(existent)
                temp[index] = importing
                imported++
            } else if (temp.add(importing)) {
                imported++
            }
        }
        setEqualizerPresets(temp, true)
        return imported
    }

    @Synchronized
    private fun setEqualizerPresets(presets: List<EQPreset>, callInterface: Boolean) {
        mPreferences.edit {
            putString(Keys.PRESETS, Json.encodeToString(presets))
        }
        if (callInterface) {
            callInterfaces { it.eqPresetListChanged(presets) }
        }
    }

    @SuppressLint("KotlinPropertyAccess")
    fun setDefaultPresets(effectSet: EffectSet) {
        val presets: MutableList<EQPreset> = ArrayList()

        val numPresets = effectSet.numEqualizerPresets.toInt()
        val numBands = effectSet.numEqualizerBands.toInt()

        for (i in 0 until numPresets) {
            val name = effectSet.equalizer.getPresetName(i.toShort())

            val levels = IntArray(numBands)
            try {
                effectSet.equalizer.usePreset(i.toShort())
            } catch (e: RuntimeException) {
                e.printStackTrace()
            }

            for (j in 0 until numBands) {
                levels[j] = effectSet.equalizer.getBandLevel(j.toShort()).toInt()
            }

            presets.add(EQPreset(name, levels, isCustom = false))
        }

        setEqualizerPresets(presets, false)
    }

    @Synchronized
    fun getCurrentPreset(): EQPreset? {
        if (eqPreset == null) {
            val savedPreset = mPreferences.getString(Keys.PRESET, null)
            if (savedPreset == null || savedPreset.trim().isEmpty()) {
                return null
            }
            eqPreset = Json.decodeFromString(savedPreset)
        }
        return eqPreset
    }

    @Synchronized
    fun setCurrentPreset(eqPreset: EQPreset) {
        callInterfaces { it.eqPresetChanged(this.eqPreset, eqPreset) }
        this.eqPreset = eqPreset
        mPreferences.edit {
            putString(Keys.PRESET, Json.encodeToString(eqPreset))
        }
    }

    @Synchronized
    private fun getCustomPreset(): EQPreset =
        mPreferences.getString(Keys.CUSTOM_PRESET, null).let { json ->
            if (json == null || json.trim().isEmpty()) {
                getEmptyPreset(CUSTOM_PRESET_NAME, true, numberOfBands).also { emptyPreset ->
                    setCustomPreset(emptyPreset, false)
                }
            } else Json.decodeFromString(json)
        }

    @Synchronized
    private fun setCustomPreset(preset: EQPreset, usePreset: Boolean = true) {
        if (preset.isCustom) {
            if (usePreset) {
                setCurrentPreset(preset)
            }
            mPreferences.edit {
                putString(Keys.CUSTOM_PRESET, Json.encodeToString(preset))
            }
        }
    }

    /**
     * Copies the current preset to a "Custom" configuration
     * and sets the band level on it
     */
    suspend fun setCustomPresetBandLevel(band: Int, level: Int) {
        var currentPreset = getCurrentPreset()
        if (currentPreset == null || !currentPreset.isCustom) {
            currentPreset = withContext(Dispatchers.Default) {
                getCustomPresetFromCurrent()
            }
        }
        currentPreset.setBandLevel(band, level)
        setCustomPreset(currentPreset)
    }

    /**
     * Copies the current preset to a "Custom" configuration
     * and sets the effect value on it
     */
    suspend fun setCustomPresetEffect(effect: String, value: Float) {
        var currentPreset = getCurrentPreset()
        if (currentPreset == null || !currentPreset.isCustom) {
            currentPreset = withContext(Dispatchers.Default) {
                getCustomPresetFromCurrent()
            }
        }
        if (value == 0f) { // zero means "disabled", we must remove disabled effects
            currentPreset.removeEffect(effect)
        } else {
            currentPreset.setEffect(effect, value)
        }
        setCustomPreset(currentPreset)
    }

    var isPresetReverbEnabled: Boolean
        get() = isPresetReverbSupported && mPreferences.getBoolean(Keys.PRESET_REVERB_ENABLED, false)
        set(presetReverbEnabled) = mPreferences.edit {
            putBoolean(Keys.PRESET_REVERB_ENABLED, presetReverbEnabled)
        }

    var presetReverbPreset: Int
        get() = mPreferences.getInt(Keys.PRESET_REVERB_PRESET, 0)
        set(presetReverbPreset) = mPreferences.edit {
            putInt(Keys.PRESET_REVERB_PRESET, presetReverbPreset)
        }

    var isLoudnessEnabled: Boolean
        get() = isLoudnessEnhancerSupported && mPreferences.getBoolean(Keys.LOUDNESS_ENABLED, false)
        set(loudnessEnabled) = mPreferences.edit {
            putBoolean(Keys.LOUDNESS_ENABLED, loudnessEnabled)
        }

    var loudnessGain: Int
        get() = mPreferences.getInt(Keys.LOUDNESS_GAIN, OpenSLESConstants.MINIMUM_LOUDNESS_GAIN)
        set(loudnessGain) = mPreferences.edit {
            putInt(Keys.LOUDNESS_GAIN, loudnessGain)
        }

    val isBassBoostEnabled: Boolean
        get() = isBassBoostSupported && getCurrentPreset()?.hasEffect(EFFECT_TYPE_BASS_BOOST) == true

    val bassStrength: Float
        get() = getCurrentPreset()?.getEffect(EFFECT_TYPE_BASS_BOOST) ?: 0f

    val isVirtualizerEnabled: Boolean
        get() = isVirtualizerSupported && getCurrentPreset()?.hasEffect(EFFECT_TYPE_VIRTUALIZER) == true

    val virtualizerStrength: Float
        get() = getCurrentPreset()?.getEffect(EFFECT_TYPE_VIRTUALIZER) ?: 0f

    var numberOfBands: Int
        get() = mPreferences.getInt(Keys.NUM_BANDS, 5)
        set(numberOfBands) = mPreferences.edit {
            putInt(Keys.NUM_BANDS, numberOfBands)
        }

    val bandLevelRange: IntArray
        get() {
            val bandLevelRange = mPreferences.getString(Keys.BAND_LEVEL_RANGE, null)
            if (bandLevelRange == null || bandLevelRange.trim().isEmpty()) {
                return intArrayOf(-1500, 1500)
            }
            val ranges = bandLevelRange.split(";").toTypedArray()
            val values = IntArray(ranges.size)
            for (i in values.indices) {
                values[i] = ranges[i].toInt()
            }
            return values
        }

    fun setBandLevelRange(bandLevelRange: ShortArray?) {
        if (bandLevelRange?.size == 2) {
            mPreferences.edit {
                putString(
                    Keys.BAND_LEVEL_RANGE,
                    String.format(Locale.ROOT, "%d;%d", bandLevelRange[0], bandLevelRange[1])
                )
            }
        }
    }

    val centerFreqs: IntArray
        get() = mPreferences.getString(Keys.CENTER_FREQUENCIES, PlaybackEQ.getZeroedBandsString(numberOfBands))!!
            .split(DEFAULT_DELIMITER).toTypedArray().let { savedValue ->
                val frequencies = IntArray(savedValue.size)
                for (i in frequencies.indices) {
                    frequencies[i] = savedValue[i].toInt()
                }
                frequencies
            }

    fun setCenterFreqs(effectSet: EffectSet) {
        val numBands = numberOfBands
        val centerFreqs = StringBuilder()
        for (i in 0 until numBands) {
            centerFreqs.append(effectSet.equalizer.getCenterFreq(i.toShort()))
            if (i < numBands - 1) {
                centerFreqs.append(DEFAULT_DELIMITER)
            }
        }
        mPreferences.edit {
            putString(Keys.CENTER_FREQUENCIES, centerFreqs.toString())
        }
    }

    @get:FloatRange(from = OpenSLESConstants.MIN_BALANCE.toDouble(), to = OpenSLESConstants.MAX_BALANCE.toDouble())
    var balanceLeft: Float
        get() = normalizeBalanceValue(mPreferences.getFloat(Keys.LEFT_BALANCE, OpenSLESConstants.MAX_BALANCE))
        set(balanceDivisorLeft) = mPreferences.edit {
            putFloat(Keys.LEFT_BALANCE, normalizeBalanceValue(balanceDivisorLeft))
        }

    @get:FloatRange(from = OpenSLESConstants.MIN_BALANCE.toDouble(), to = OpenSLESConstants.MAX_BALANCE.toDouble())
    var balanceRight: Float
        get() = normalizeBalanceValue(mPreferences.getFloat(Keys.RIGHT_BALANCE, OpenSLESConstants.MAX_BALANCE))
        set(balanceDivisorRight) = mPreferences.edit {
            putFloat(Keys.RIGHT_BALANCE, normalizeBalanceValue(balanceDivisorRight))
        }

    val maximumSpeed: Float
        get() = if (isFixedPitchEnabled) OpenSLESConstants.MAXIMUM_SPEED else OpenSLESConstants.MAXIMUM_SPEED_NO_PITCH

    val minimumSpeed: Float
        get() = if (isFixedPitchEnabled) OpenSLESConstants.MINIMUM_SPEED else OpenSLESConstants.MINIMUM_SPEED_NO_PITCH

    var speed: Float
        get() = normalizeValue(
            mPreferences.getFloat(Keys.SPEED, OpenSLESConstants.DEFAULT_SPEED),
            minimumSpeed,
            maximumSpeed
        )
        set(speed) {
            if (isFixedPitchEnabled) {
                pitch = speed
            }
            mPreferences.edit {
                putFloat(Keys.SPEED, normalizeValue(speed, minimumSpeed, maximumSpeed))
            }
        }

    var pitch: Float
        get() = if (isFixedPitchEnabled) {
            speed
        } else {
            normalizeValue(
                mPreferences.getFloat(Keys.PITCH, OpenSLESConstants.DEFAULT_PITCH),
                OpenSLESConstants.MINIMUM_PITCH,
                OpenSLESConstants.MAXIMUM_PITCH
            )
        }
        set(pitch) = mPreferences.edit {
            putFloat(
                Keys.PITCH,
                normalizeValue(pitch, OpenSLESConstants.MINIMUM_PITCH, OpenSLESConstants.MAXIMUM_PITCH)
            )
        }

    var isFixedPitchEnabled: Boolean
        get() = mPreferences.getBoolean(Keys.IS_FIXED_PITCH, true)
        set(enabled) {
            if (enabled) {
                pitch = speed
            }
            mPreferences.edit {
                putBoolean(Keys.IS_FIXED_PITCH, enabled)
            }
        }

    private fun normalizeBalanceValue(balance: Float): Float {
        return normalizeValue(balance, OpenSLESConstants.MIN_BALANCE, OpenSLESConstants.MAX_BALANCE)
    }

    private fun normalizeValue(value: Float, minimumValue: Float, maximumValue: Float): Float {
        if (value < minimumValue) {
            return minimumValue
        } else if (value > maximumValue) {
            return maximumValue
        }
        return value
    }

    interface Keys {
        companion object {
            const val GLOBAL_ENABLED = "audiofx.global.enable"
            const val NUM_BANDS = "equalizer.number_of_bands"
            const val LEFT_BALANCE = "equalizer.balance.left"
            const val RIGHT_BALANCE = "equalizer.balance.right"
            const val SPEED = PLAYBACK_SPEED
            const val PITCH = PLAYBACK_PITCH
            const val IS_FIXED_PITCH = "equalizer.pitch.fixed"
            const val IS_INITIALIZED = "equalizer.initialized"
            const val LOUDNESS_ENABLED = "audiofx.eq.loudness.enable"
            const val LOUDNESS_GAIN = "audiofx.eq.loudness.gain"
            const val PRESET_REVERB_ENABLED = "audiofx.eq.presetreverb.enable"
            const val PRESET_REVERB_PRESET = "audiofx.eq.presetreverb.preset"
            const val PRESETS = "audiofx.eq.presets"
            const val PRESET = "audiofx.eq.preset"
            const val CUSTOM_PRESET = "audiofx.eq.preset.custom"
            const val BAND_LEVEL_RANGE = "equalizer.band_level_range"
            const val CENTER_FREQUENCIES = "equalizer.center_frequencies"
        }
    }

    companion object {

        const val EFFECT_TYPE_EQUALIZER = "0bed4300-ddd6-11db-8f34-0002a5d5c51b"
        const val EFFECT_TYPE_BASS_BOOST = "0634f220-ddd4-11db-a0fc-0002a5d5c51b"
        const val EFFECT_TYPE_VIRTUALIZER = "37cc2c00-dddd-11db-8577-0002a5d5c51b"
        const val EFFECT_TYPE_LOUDNESS_ENHANCER = "fe3199be-aed0-413f-87bb-11260eb63cf1"
        const val EFFECT_TYPE_PRESET_REVERB = "47382d60-ddd8-11db-bf3a-0002a5d5c51b"

        /**
         * Max number of EQ bands supported
         */
        const val EQUALIZER_MAX_BANDS = 6

        const val PREFERENCES_NAME = "BoomingAudioFX"
        private const val CUSTOM_PRESET_NAME = "Custom"
        private const val DEFAULT_DELIMITER = ";"
    }

    init {
        try {
            //Query available effects
            val effects = AudioEffect.queryEffects()
            //Determine available/supported effects
            if (!effects.isNullOrEmpty()) {
                for (effect in effects) {
                    when (effect.type) {
                        UUID.fromString(EFFECT_TYPE_EQUALIZER) -> isEqualizerSupported = true
                        UUID.fromString(EFFECT_TYPE_BASS_BOOST) -> isBassBoostSupported = true
                        UUID.fromString(EFFECT_TYPE_VIRTUALIZER) -> isVirtualizerSupported = true
                        UUID.fromString(EFFECT_TYPE_LOUDNESS_ENHANCER) -> isLoudnessEnhancerSupported = true
                        UUID.fromString(EFFECT_TYPE_PRESET_REVERB) -> isPresetReverbSupported = true
                    }
                }
            }
        } catch (ignored: NoClassDefFoundError) {
            //The user doesn't have the AudioEffect/AudioEffect.Descriptor class. How sad.
        }
    }
}