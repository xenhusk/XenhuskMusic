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
import com.mardous.booming.interfaces.IEQInterface
import com.mardous.booming.model.EQPreset
import com.mardous.booming.mvvm.*
import com.mardous.booming.providers.MediaStoreWriter
import com.mardous.booming.service.MusicPlayer
import com.mardous.booming.service.equalizer.EqualizerManager
import com.mardous.booming.service.equalizer.EqualizerManager.Companion.EFFECT_TYPE_BASS_BOOST
import com.mardous.booming.service.equalizer.EqualizerManager.Companion.EFFECT_TYPE_VIRTUALIZER
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

class EqualizerViewModel(
    private val contentResolver: ContentResolver,
    private val equalizerManager: EqualizerManager,
    private val mediaStoreWriter: MediaStoreWriter
) : ViewModel() {

    private var exportContent: String? = null

    fun bindEqualizer(eqInterface: IEQInterface?) {
        equalizerManager.registerInterface(eqInterface)
        if (eqInterface != null) {
            equalizerManager.requestCurrentPreset()
            equalizerManager.requestState()
        }
    }

    fun unbindEqualizer(eqInterface: IEQInterface?) {
        equalizerManager.unregisterInterface(eqInterface)
    }

    fun isCustomPresetSelected() = equalizerManager.getCurrentPreset()?.isCustom == true

    fun setEqualizerState(isEnabled: Boolean) {
        // update equalizer session
        MusicPlayer.closeEqualizerSessions(!isEnabled)
        MusicPlayer.openEqualizerSession(isEnabled)

        // set parameter and state
        equalizerManager.isEqualizerEnabled = isEnabled
        MusicPlayer.updateEqualizer()
    }

    fun setEqualizerPreset(eqPreset: EQPreset) {
        equalizerManager.setCurrentPreset(eqPreset)
        MusicPlayer.updateEqualizer()
    }

    fun setCustomPresetBandLevel(band: Int, level: Int) = viewModelScope.launch {
        equalizerManager.setCustomPresetBandLevel(band, level)
    }

    fun setBassStrength(level: Float) = viewModelScope.launch {
        equalizerManager.setCustomPresetEffect(EFFECT_TYPE_BASS_BOOST, level)
    }

    fun setVirtualizerStrength(level: Float) = viewModelScope.launch {
        equalizerManager.setCustomPresetEffect(EFFECT_TYPE_VIRTUALIZER, level)
    }

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
        val presets = equalizerManager.getEqualizerPresets()
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

    fun resetEqualizer() = viewModelScope.launch(IO) {
        equalizerManager.resetConfiguration()
    }
}