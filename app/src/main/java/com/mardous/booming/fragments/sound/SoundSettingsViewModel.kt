/*
 * Copyright (c) 2025 Christians Martínez Alvarado
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

package com.mardous.booming.fragments.sound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.audio.SoundSettings
import com.mardous.booming.viewmodels.equalizer.model.EqEffectUpdate
import com.mardous.booming.mvvm.soundsettings.BalanceLevel
import com.mardous.booming.mvvm.soundsettings.TempoLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SoundSettingsViewModel(private val soundSettings: SoundSettings) : ViewModel() {

    val balanceFlow get() = soundSettings.balanceFlow
    val tempoFlow get() = soundSettings.tempoFlow

    val balance get() = soundSettings.balance
    val minBalance: Float get() = soundSettings.minBalance
    val maxBalance: Float get() = soundSettings.maxBalance

    val tempo get() = soundSettings.tempo
    val maxSpeed: Float get() = soundSettings.maxSpeed
    val maxPitch: Float get() = soundSettings.maxPitch
    val minSpeed: Float get() = soundSettings.minSpeed
    val minPitch: Float get() = soundSettings.minPitch
    val defaultSpeed: Float get() = soundSettings.defaultSpeed
    val defaultPitch: Float get() = soundSettings.defaultPitch

    fun setBalance(
        right: Float = balance.right,
        left: Float = balance.left,
        apply: Boolean = true
    ) = viewModelScope.launch(Dispatchers.IO) {
        val update = EqEffectUpdate(balanceFlow.value, true, BalanceLevel(left, right))
        soundSettings.setBalance(update, apply)
    }

    fun setTempo(
        speed: Float = tempo.speed,
        pitch: Float = tempo.pitch,
        isFixedPitch: Boolean = tempo.isFixedPitch,
        apply: Boolean = true
    ) = viewModelScope.launch(Dispatchers.IO) {
        val update = EqEffectUpdate(tempoFlow.value, true, TempoLevel(speed, pitch, isFixedPitch))
        soundSettings.setTempo(update, apply)
    }

    fun applyPendingState() = viewModelScope.launch(Dispatchers.IO) {
        soundSettings.applyPendingState()
    }
}