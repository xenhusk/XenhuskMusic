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

package com.mardous.booming.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media.AudioManagerCompat.getStreamMaxVolume
import androidx.media.AudioManagerCompat.getStreamMinVolume
import com.mardous.booming.service.playback.PlaybackManager
import com.mardous.booming.viewmodels.equalizer.model.VolumeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioOutputObserver(
    private val context: Context,
    private val playbackManager: PlaybackManager
) : BroadcastReceiver() {

    private val _volumeStateFlow = MutableStateFlow(VolumeState.Unspecified)
    val volumeStateFlow = _volumeStateFlow.asStateFlow()

    private val _audioDeviceFlow = MutableStateFlow(AudioDevice.UnknownDevice)
    val audioDeviceFlow = _audioDeviceFlow.asStateFlow()

    private var mediaRouter = context.getSystemService<MediaRouter>()
    var audioManager = context.getSystemService<AudioManager>()
        private set

    private var isObserving = false

    init {
        requestVolume()
        requestAudioDevice()
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == VOLUME_CHANGED_ACTION || action == Intent.ACTION_HEADSET_PLUG) {
            requestVolume()
        }
    }

    fun startObserver() {
        if (!isObserving) {
            val filter = IntentFilter().apply {
                addAction(VOLUME_CHANGED_ACTION)
                addAction(Intent.ACTION_HEADSET_PLUG)
            }
            ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_EXPORTED)
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, null)
            this.isObserving = true
        }
    }

    fun stopObserver() {
        if (isObserving) {
            context.unregisterReceiver(this)
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
            this.isObserving = false
        }
    }

    private fun getCurrentAudioDevice(): AudioDevice {
        var audioDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            playbackManager.getRoutedDevice()?.let { deviceInfo ->
                AudioDevice(
                    code = deviceInfo.type,
                    type = deviceInfo.getDeviceType(),
                    productName = deviceInfo.productName
                )
            }
        } else {
            null
        }
        if (audioDevice == null) {
            val route = mediaRouter?.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
            if (route != null && route.isEnabled) {
                val deviceType = when (route.deviceType) {
                    MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER -> AudioDeviceType.Speaker
                    MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH -> AudioDeviceType.Bluetooth
                    else -> null
                }
                if (deviceType != null) {
                    audioDevice = AudioDevice(route.deviceType, deviceType, route.name, true)
                }
            }
            if (audioDevice == null) {
                audioDevice = AudioDevice.UnknownDevice
            }
        }
        return audioDevice
    }

    private fun requestVolume() {
        audioManager?.let {
            _volumeStateFlow.value = VolumeState(
                currentVolume = it.getStreamVolume(AudioManager.STREAM_MUSIC),
                maxVolume = getStreamMaxVolume(it, AudioManager.STREAM_MUSIC),
                minVolume = getStreamMinVolume(it, AudioManager.STREAM_MUSIC),
                isFixed = it.isVolumeFixed
            )
        }
    }

    private fun requestAudioDevice() {
        _audioDeviceFlow.value = getCurrentAudioDevice()
        _volumeStateFlow.value = volumeStateFlow.value.copy(
            isFixed = audioManager?.isVolumeFixed == true
        )
    }

    private val audioDeviceCallback: AudioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            requestAudioDevice()
            requestVolume()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            requestAudioDevice()
            requestVolume()
        }
    }

    companion object {
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }
}