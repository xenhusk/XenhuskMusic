package com.mardous.booming.viewmodels.equalizer.model

data class VolumeState(
    val currentVolume: Int,
    val maxVolume: Int,
    val minVolume: Int,
    val isFixed: Boolean
) {
    companion object {
        val Unspecified = VolumeState(0, 1, 0, false)
    }
}