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

package com.mardous.booming.fragments.other

import android.Manifest
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.mardous.booming.R
import com.mardous.booming.audio.AudioDevice
import com.mardous.booming.audio.AudioOutputObserver
import com.mardous.booming.databinding.FragmentSoundSettingsBinding
import com.mardous.booming.extensions.create
import com.mardous.booming.extensions.dp
import com.mardous.booming.extensions.hasPie
import com.mardous.booming.extensions.requireAlertDialog
import com.mardous.booming.extensions.resources.controlColorNormal
import com.mardous.booming.extensions.resources.hide
import com.mardous.booming.extensions.resources.primaryColor
import com.mardous.booming.extensions.resources.show
import com.mardous.booming.service.MusicPlayer
import com.mardous.booming.service.equalizer.EqualizerManager
import com.mardous.booming.service.equalizer.OpenSLESConstants
import me.bogerchan.niervisualizer.NierVisualizerManager
import me.bogerchan.niervisualizer.renderer.columnar.ColumnarType1Renderer
import org.koin.android.ext.android.inject
import java.util.Locale
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * @author Christians M. A. (mardous)
 */
class SoundSettingsFragment : DialogFragment(), View.OnClickListener,
    Slider.OnChangeListener, Slider.OnSliderTouchListener, AudioOutputObserver.Callback {

    private var _binding: FragmentSoundSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var visualizerManager: NierVisualizerManager
    private lateinit var permissionRequestLauncher: ActivityResultLauncher<String>

    private val visualizerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val equalizerManager: EqualizerManager by inject()

    private lateinit var audioOutputObserver: AudioOutputObserver

    private val audioManager: AudioManager
        get() = audioOutputObserver.audioManager

    private var isFixedPitchEnabled: Boolean by Delegates.observable(equalizerManager.isFixedPitchEnabled) { _: KProperty<*>, _: Boolean, newValue: Boolean ->
        equalizerManager.isFixedPitchEnabled = newValue

        updateTempoValues()
        updateFixedPitchState()

        binding.pitchSlider.isEnabled = !newValue
        binding.pitchIcon.isEnabled = !newValue

        MusicPlayer.updateTempo()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioOutputObserver = AudioOutputObserver(requireContext(), this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = FragmentSoundSettingsBinding.inflate(layoutInflater)
        setupVisualizer()
        setupVolumeViews()
        setupTempoViews()
        initializeVisualizer(false)
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sound_settings)
            .setIcon(R.drawable.ic_volume_up_24dp)
            .setView(binding.root)
            .setPositiveButton(R.string.close_action, null)
            .create {
                audioOutputObserver.requestVolume()
                audioOutputObserver.requestAudioDevice()

                // set initial slider values
                updateTempoValues()
                updateFixedPitchState()
            }
    }

    override fun onClick(view: View) {
        when (view) {
            binding.speedIcon -> {
                equalizerManager.speed = OpenSLESConstants.DEFAULT_SPEED
                if (isFixedPitchEnabled) {
                    equalizerManager.pitch = OpenSLESConstants.DEFAULT_PITCH
                }
                MusicPlayer.updateTempo()
                updateTempoValues()
            }

            binding.pitchIcon -> {
                equalizerManager.pitch = OpenSLESConstants.DEFAULT_PITCH
                MusicPlayer.updateTempo()
                updateTempoValues()
            }

            binding.fixedPitchIcon -> {
                val isFixedPitch = isFixedPitchEnabled
                isFixedPitchEnabled = !isFixedPitch
            }
        }
    }

    private fun setupVisualizer() {
        visualizerPaint.color = primaryColor()

        binding.activateVisualizer.setOnClickListener {
            initializeVisualizer(true)
        }

        visualizerManager = NierVisualizerManager()
        permissionRequestLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission ->
                if (hasPermission) {
                    initializeVisualizer(false)
                } else {
                    val permDialog = MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.permissions_needed)
                        .setMessage(R.string.visualizer_permission_request)
                        .setPositiveButton(R.string.action_grant) { _: DialogInterface, _: Int ->
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", requireActivity().packageName, null)
                                )
                            )
                        }
                        .create()
                    permDialog.setOnShowListener { requireDialog().hide() }
                    permDialog.setOnDismissListener { requireDialog().show() }
                    permDialog.show()
                }
            }
    }

    private fun setupVolumeViews() {
        binding.volumeSlider.addOnChangeListener(this@SoundSettingsFragment)
        binding.volumeSlider.addOnSliderTouchListener(this@SoundSettingsFragment)
        binding.leftBalanceSlider.apply {
            valueFrom = OpenSLESConstants.MIN_BALANCE
            valueTo = OpenSLESConstants.MAX_BALANCE
            value = equalizerManager.balanceLeft

            addOnChangeListener(this@SoundSettingsFragment)
        }
        binding.rightBalanceSlider.apply {
            valueFrom = OpenSLESConstants.MIN_BALANCE
            valueTo = OpenSLESConstants.MAX_BALANCE
            value = equalizerManager.balanceRight

            addOnChangeListener(this@SoundSettingsFragment)
        }
    }

    private fun setupTempoViews() {
        binding.speedSlider.apply {
            setLabelFormatter { value ->
                String.format(Locale.getDefault(), "%.1f%s", value, "x")
            }
            addOnChangeListener(this@SoundSettingsFragment)
            addOnSliderTouchListener(this@SoundSettingsFragment)
        }
        binding.pitchSlider.apply {
            isEnabled = !isFixedPitchEnabled
            setLabelFormatter { value ->
                String.format(Locale.getDefault(), "%.1f", value)
            }
            addOnSliderTouchListener(this@SoundSettingsFragment)
        }

        binding.pitchIcon.isEnabled = !isFixedPitchEnabled
        binding.pitchIcon.setOnClickListener(this@SoundSettingsFragment)
        binding.speedIcon.setOnClickListener(this@SoundSettingsFragment)
        binding.fixedPitchIcon.setOnClickListener(this@SoundSettingsFragment)
    }

    private fun initializeVisualizer(canRequestPermission: Boolean) {
        if (requireContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (visualizerManager.init(MusicPlayer.audioSessionId) == NierVisualizerManager.SUCCESS) {
                visualizerManager.start(binding.visualizer, arrayOf(ColumnarType1Renderer(visualizerPaint)))
                binding.root.updatePadding(top = 16.dp(resources))
                binding.visualizer.setZOrderOnTop(true)
                binding.visualizer.holder?.setFormat(PixelFormat.TRANSLUCENT)
                binding.visualizer.show()
                binding.activateVisualizer.hide()
            }
        } else if (canRequestPermission) {
            permissionRequestLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun updateFixedPitchState() {
        if (isFixedPitchEnabled) {
            binding.fixedPitchIcon.setImageResource(R.drawable.ic_lock_24dp)
            binding.fixedPitchIcon.setColorFilter(controlColorNormal(), PorterDuff.Mode.SRC_IN)
        } else {
            binding.fixedPitchIcon.setImageResource(R.drawable.ic_lock_open_24dp)
            binding.fixedPitchIcon.clearColorFilter()
        }
    }

    private fun updateTempoValues() {
        binding.speedSlider.apply {
            valueFrom = equalizerManager.minimumSpeed
            valueTo = equalizerManager.maximumSpeed
            setValueAnimated(equalizerManager.speed)
        }

        binding.pitchSlider.apply {
            valueFrom = OpenSLESConstants.MINIMUM_PITCH
            valueTo = OpenSLESConstants.MAXIMUM_PITCH
            setValueAnimated(equalizerManager.pitch)
        }
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (fromUser) {
            when (slider) {
                binding.volumeSlider -> audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value.toInt(), 0)
                binding.leftBalanceSlider -> {
                    equalizerManager.balanceLeft = value
                    MusicPlayer.updateBalance()
                }

                binding.rightBalanceSlider -> {
                    equalizerManager.balanceRight = value
                    MusicPlayer.updateBalance()
                }

                binding.speedSlider -> {
                    if (isFixedPitchEnabled) {
                        binding.pitchSlider.value = value
                    }
                }
            }
        }
    }

    override fun onStartTrackingTouch(slider: Slider) {}

    override fun onStopTrackingTouch(slider: Slider) {
        when (slider) {
            binding.speedSlider -> {
                equalizerManager.speed = binding.speedSlider.value
                MusicPlayer.updateTempo()
            }

            binding.pitchSlider -> {
                equalizerManager.pitch = binding.pitchSlider.value
                MusicPlayer.updateTempo()
            }
        }
    }

    override fun onAudioOutputDeviceChange(currentDevice: AudioDevice) {
        requireAlertDialog().setIcon(currentDevice.type.iconRes)
        if (hasPie()) {
            requireAlertDialog().setTitle(currentDevice.getDeviceName(requireContext()))
        } else {
            requireAlertDialog().setTitle(getString(R.string.sound_settings))
        }
    }

    override fun onVolumeChange(newVolume: Int, minVolume: Int, maxVolume: Int) {
        binding.volumeSlider.apply {
            valueFrom = minVolume.toFloat()
            valueTo = maxVolume.toFloat()
            if (!isDragging) {
                setValueAnimated(newVolume.toFloat())
            }
        }
    }

    override fun onFixedVolumeStateChange(isFixed: Boolean) {
        binding.volumeSlider.isEnabled = !isFixed
    }

    override fun onPause() {
        super.onPause()
        visualizerManager.pause()
    }

    override fun onResume() {
        super.onResume()
        visualizerManager.resume()
    }

    override fun onStart() {
        super.onStart()
        audioOutputObserver.startObserver()
    }

    override fun onStop() {
        super.onStop()
        audioOutputObserver.stopObserver(false)
    }

    override fun onDestroy() {
        binding.speedSlider.clearOnChangeListeners()
        binding.speedSlider.clearOnSliderTouchListeners()
        binding.pitchSlider.clearOnSliderTouchListeners()
        super.onDestroy()
        visualizerManager.release()
        audioOutputObserver.stopObserver()
        _binding = null
    }
}