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

package com.mardous.booming.fragments.equalizer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.mardous.booming.R
import com.mardous.booming.extensions.MIME_TYPE_APPLICATION
import com.mardous.booming.extensions.files.getContentUri
import com.mardous.booming.extensions.files.readString
import com.mardous.booming.model.EQPreset
import com.mardous.booming.mvvm.*
import com.mardous.booming.mvvm.equalizer.EqEffectState
import com.mardous.booming.mvvm.equalizer.EqEffectUpdate
import com.mardous.booming.mvvm.equalizer.EqState
import com.mardous.booming.mvvm.equalizer.EqUpdate
import com.mardous.booming.providers.MediaStoreWriter
import com.mardous.booming.service.MusicPlayer
import com.mardous.booming.service.equalizer.EqualizerManager
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

class EqualizerViewModel(
    private val contentResolver: ContentResolver,
    private val equalizerManager: EqualizerManager,
    private val mediaStoreWriter: MediaStoreWriter
) : ViewModel() {

    val eqStateFlow: StateFlow<EqState> get() = equalizerManager.eqStateFlow
    val currentPresetFlow: StateFlow<EQPreset> get() = equalizerManager.currentPresetFlow
    val bassBoostFlow: StateFlow<EqEffectState<Float>> = equalizerManager.bassBoostFlow
    val virtualizerFlow: StateFlow<EqEffectState<Float>> = equalizerManager.virtualizerFlow
    val loudnessGainFlow: StateFlow<EqEffectState<Float>> = equalizerManager.loudnessGainFlow
    val presetReverbFlow: StateFlow<EqEffectState<Int>> = equalizerManager.presetReverbFlow
    val presetsFlow: StateFlow<List<EQPreset>> get() = equalizerManager.presetsFlow

    val eqState: EqState get() = equalizerManager.eqState
    val bassBoostState: EqEffectState<Float> get() = equalizerManager.bassBoostState
    val virtualizerState: EqEffectState<Float> get() = equalizerManager.virtualizerState
    val loudnessGainState: EqEffectState<Float> get() = equalizerManager.loudnessGainState
    val presetReverbState: EqEffectState<Int> get() = equalizerManager.presetReverbState

    val numberOfBands: Int get() = equalizerManager.numberOfBands
    val bandLevelRange: IntArray get() = equalizerManager.bandLevelRange
    val centerFreqs: IntArray get() = equalizerManager.centerFreqs

    private var exportContent: String? = null

    fun isCustomPresetSelected() = equalizerManager.currentPreset.isCustom

    fun setEqualizerState(isEnabled: Boolean, apply: Boolean = true) {
        // update equalizer session
        equalizerManager.closeAudioEffectSession(MusicPlayer.audioSessionId, !isEnabled)
        equalizerManager.openAudioEffectSession(MusicPlayer.audioSessionId, isEnabled)

        // set parameter and state
        viewModelScope.launch(Default) {
            equalizerManager.setEqualizerState(EqUpdate<EqState>(eqState, isEnabled), apply)
            if (apply) updateEqualizer()
        }
    }

    fun setLoudnessGain(
        isEnabled: Boolean = loudnessGainState.isUsable,
        value: Float = loudnessGainState.value,
        apply: Boolean = true
    ) = viewModelScope.launch(Default) {
        equalizerManager.setLoudnessGain(EqEffectUpdate(loudnessGainState, isEnabled, value), apply)
        if (apply) updateEqualizer()
    }

    fun setPresetReverb(
        isEnabled: Boolean = presetReverbState.isUsable,
        value: Int = presetReverbState.value,
        apply: Boolean = true
    ) = viewModelScope.launch(Default) {
        equalizerManager.setPresetReverb(EqEffectUpdate(presetReverbState, isEnabled, value), apply)
        if (apply) updateEqualizer()
    }

    fun setBassBoost(
        isEnabled: Boolean = bassBoostState.isUsable,
        value: Float = bassBoostState.value,
        apply: Boolean = true
    ) = viewModelScope.launch(Default) {
        equalizerManager.setBassBoost(EqEffectUpdate(bassBoostState, isEnabled, value), apply)
        if (apply) updateEqualizer()
    }

    fun setVirtualizer(
        isEnabled: Boolean = virtualizerState.isUsable,
        value: Float = virtualizerState.value,
        apply: Boolean = true
    ) = viewModelScope.launch(Default) {
        equalizerManager.setVirtualizer(EqEffectUpdate(virtualizerState, isEnabled, value), apply)
        if (apply) updateEqualizer()
    }

    fun setEqualizerPreset(eqPreset: EQPreset) = viewModelScope.launch(IO) {
        equalizerManager.setCurrentPreset(eqPreset)
        updateEqualizer()
    }

    fun setCustomPresetBandLevel(band: Int, level: Int) = viewModelScope.launch(Default) {
        equalizerManager.setCustomPresetBandLevel(band, level)
        updateEqualizer()
    }

    fun applyPendingStates() = viewModelScope.launch(IO) {
        equalizerManager.applyPendingStates()
        updateEqualizer()
    }

    fun getEqualizerPresetsWithCustom(presets: List<EQPreset>) =
        equalizerManager.getEqualizerPresetsWithCustom(presets)

    fun savePreset(presetName: String?, canReplace: Boolean): LiveData<PresetOpResult> = liveData(IO) {
        if (presetName.isNullOrBlank()) {
            emit(PresetOpResult(false, R.string.preset_name_is_empty, canDismiss = false))
        } else {
            if (!canReplace && !equalizerManager.isPresetNameAvailable(presetName)) {
                emit(PresetOpResult(false, R.string.that_name_is_already_in_use, canDismiss = false))
            } else {
                val newPreset = equalizerManager.getNewPresetFromCustom(presetName)
                if (equalizerManager.addPreset(newPreset, canReplace, usePreset = true)) {
                    emit(PresetOpResult(true, R.string.preset_saved_successfully))
                } else {
                    emit(PresetOpResult(false, R.string.could_not_save_preset))
                }
            }
        }
    }

    fun renamePreset(preset: EQPreset, newName: String?): LiveData<PresetOpResult> = liveData(IO) {
        if (newName.isNullOrBlank()) {
            emit(PresetOpResult(false, canDismiss = false))
        } else {
            if (equalizerManager.renamePreset(preset, newName)) {
                emit(PresetOpResult(true, R.string.preset_renamed))
            } else {
                emit(PresetOpResult(false, R.string.preset_not_renamed))
            }
        }
    }

    fun deletePreset(preset: EQPreset): LiveData<PresetOpResult> = liveData(IO) {
        emit(PresetOpResult(equalizerManager.removePreset(preset)))
    }

    fun requestExport(): LiveData<ExportRequestResult> = liveData(IO) {
        val presets = equalizerManager.equalizerPresets
        val names = presets.map { it.name }
        if (names.isEmpty()) {
            emit(ExportRequestResult(false, R.string.there_is_nothing_to_export))
        } else {
            emit(ExportRequestResult(true, presets = presets, presetNames = names))
        }
    }

    fun generateExportData(presets: List<EQPreset>): LiveData<String> = liveData(IO) {
        exportContent = Json.encodeToString(presets)
        emit(equalizerManager.getNewExportName())
    }

    fun exportConfiguration(data: Uri?): LiveData<PresetExportResult> = liveData(IO) {
        if (data == null || exportContent.isNullOrEmpty()) {
            emit(PresetExportResult(false))
        } else {
            val result = runCatching {
                mediaStoreWriter.toContentResolver(null, data) { stream ->
                    when {
                        !exportContent.isNullOrEmpty() -> {
                            stream.bufferedWriter().use { it.write(exportContent) }
                            exportContent = null
                            true
                        }

                        else -> false
                    }
                }
            }

            if (result.isFailure || result.getOrThrow().resultCode == MediaStoreWriter.Result.Code.ERROR) {
                emit(PresetExportResult(false, R.string.could_not_export_configuration))
            } else {
                emit(PresetExportResult(true, R.string.configuration_exported_successfully, data, MIME_TYPE_APPLICATION))
            }
        }
    }

    fun requestImport(data: Uri?): LiveData<ImportRequestResult> = liveData(IO) {
        if (data == null || data.path?.endsWith(".json") == false) {
            emit(ImportRequestResult(false, R.string.there_is_nothing_to_import))
        } else {
            val result = runCatching {
                contentResolver.openInputStream(data)?.use { stream ->
                    Json.decodeFromString<List<EQPreset>>(stream.readString())
                }
            }
            val presets = result.getOrNull()
            if (result.isFailure || presets == null) {
                emit(ImportRequestResult(false, R.string.there_is_nothing_to_import))
            } else {
                emit(ImportRequestResult(true, presets = presets, presetNames = presets.map { it.name }))
            }
        }
    }

    fun importPresets(presets: List<EQPreset>): LiveData<PresetImportResult> = liveData(IO) {
        if (presets.isNotEmpty()) {
            emit(PresetImportResult(true, imported = equalizerManager.importPresets(presets)))
        } else {
            emit(PresetImportResult(false, R.string.no_preset_imported))
        }
    }

    fun sharePresets(context: Context, presets: List<EQPreset>): LiveData<PresetExportResult> = liveData(IO) {
        if (presets.isNotEmpty()) {
            val cacheDir = context.externalCacheDir
            if (cacheDir == null || (!cacheDir.exists() && !cacheDir.mkdirs())) {
                emit(PresetExportResult(false, R.string.could_not_create_configurations_file))
            } else {
                val name = equalizerManager.getNewExportName()
                val result = runCatching {
                    File(cacheDir, name).also {
                        it.writeText(Json.encodeToString(presets))
                    }.getContentUri(context)
                }
                if (result.isSuccess) {
                    emit(PresetExportResult(true, data = result.getOrThrow(), mimeType = MIME_TYPE_APPLICATION))
                } else {
                    emit(PresetExportResult(false, R.string.could_not_create_configurations_file))
                }
            }
        } else {
            emit(PresetExportResult(false))
        }
    }

    fun updateEqualizer() = viewModelScope.launch(IO) {
        equalizerManager.update()
    }

    fun resetEqualizer() = viewModelScope.launch(IO) {
        equalizerManager.resetConfiguration()
    }
}