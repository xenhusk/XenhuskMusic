package com.mardous.booming.viewmodels.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.audio.SoundSettings
import com.mardous.booming.viewmodels.equalizer.model.BalanceLevel
import com.mardous.booming.viewmodels.equalizer.model.TempoLevel
import com.mardous.booming.viewmodels.equalizer.model.EqEffectUpdate
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