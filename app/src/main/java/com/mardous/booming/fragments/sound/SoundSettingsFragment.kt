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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.bogerchan.niervisualizer.NierVisualizerManager
import me.bogerchan.niervisualizer.renderer.columnar.ColumnarType1Renderer
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale

/**
 * @author Christians M. A. (mardous)
 */
class SoundSettingsFragment : DialogFragment(), View.OnClickListener,
    Slider.OnChangeListener, Slider.OnSliderTouchListener, AudioOutputObserver.Callback {

    private val viewModel: SoundSettingsViewModel by viewModel()

    private var _binding: FragmentSoundSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var visualizerManager: NierVisualizerManager
    private lateinit var permissionRequestLauncher: ActivityResultLauncher<String>

    private val visualizerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private lateinit var audioOutputObserver: AudioOutputObserver

    private val audioManager: AudioManager
        get() = audioOutputObserver.audioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioOutputObserver = AudioOutputObserver(requireContext(), this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = FragmentSoundSettingsBinding.inflate(layoutInflater)
        setupVisualizer()
        setupVolumeViews()
        setupTempoViews()
        launchFlow()
        initializeVisualizer(false)
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sound_settings)
            .setIcon(R.drawable.ic_volume_up_24dp)
            .setView(binding.root)
            .setPositiveButton(R.string.close_action, null)
            .create {
                audioOutputObserver.requestVolume()
                audioOutputObserver.requestAudioDevice()
            }
    }

    private fun launchAndRepeatWithViewLifecycle(block: suspend CoroutineScope.() -> Unit) {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED, block)
        }
    }

    private fun launchFlow() {
        launchAndRepeatWithViewLifecycle {
            viewModel.balanceFlow.collect {
                binding.leftBalanceSlider.value = it.value.left
                binding.rightBalanceSlider.value = it.value.right
            }
        }
        launchAndRepeatWithViewLifecycle {
            viewModel.tempoFlow.collect {
                binding.speedSlider.valueFrom = viewModel.minSpeed
                binding.speedSlider.valueTo = viewModel.maxSpeed
                binding.speedSlider.setValueAnimated(it.value.speed)
                binding.pitchSlider.valueFrom = viewModel.minPitch
                binding.pitchSlider.valueTo = viewModel.maxPitch
                binding.pitchSlider.setValueAnimated(it.value.actualPitch)
                if (it.value.isFixedPitch) {
                    binding.fixedPitchIcon.setImageResource(R.drawable.ic_lock_24dp)
                    binding.fixedPitchIcon.setColorFilter(controlColorNormal(), PorterDuff.Mode.SRC_IN)
                } else {
                    binding.fixedPitchIcon.setImageResource(R.drawable.ic_lock_open_24dp)
                    binding.fixedPitchIcon.clearColorFilter()
                }
                binding.pitchIcon.isEnabled = !it.value.isFixedPitch
                binding.pitchSlider.isEnabled = !it.value.isFixedPitch
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
        binding.volumeSlider.addOnChangeListener(this)
        binding.leftBalanceSlider.apply {
            valueFrom = viewModel.minBalance
            valueTo = viewModel.maxBalance
            addOnChangeListener(this@SoundSettingsFragment)
            addOnSliderTouchListener(this@SoundSettingsFragment)
        }
        binding.rightBalanceSlider.apply {
            valueFrom = viewModel.minBalance
            valueTo = viewModel.maxBalance
            addOnChangeListener(this@SoundSettingsFragment)
            addOnSliderTouchListener(this@SoundSettingsFragment)
        }
    }

    private fun setupTempoViews() {
        binding.speedSlider.apply {
            setLabelFormatter { value ->
                String.Companion.format(Locale.getDefault(), "%.1f%s", value, "x")
            }
            addOnChangeListener(this@SoundSettingsFragment)
            addOnSliderTouchListener(this@SoundSettingsFragment)
        }
        binding.pitchSlider.apply {
            setLabelFormatter { value ->
                String.Companion.format(Locale.getDefault(), "%.1f", value)
            }
            addOnChangeListener(this@SoundSettingsFragment)
            addOnSliderTouchListener(this@SoundSettingsFragment)
        }

        binding.pitchIcon.setOnClickListener(this)
        binding.speedIcon.setOnClickListener(this)
        binding.fixedPitchIcon.setOnClickListener(this)
    }

    private fun initializeVisualizer(canRequestPermission: Boolean) {
        if (requireContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (visualizerManager.init(MusicPlayer.audioSessionId) == NierVisualizerManager.Companion.SUCCESS) {
                visualizerManager.start(binding.visualizer, arrayOf(
                    ColumnarType1Renderer(
                        visualizerPaint
                    )
                ))
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

    override fun onClick(view: View) {
        when (view) {
            binding.speedIcon -> viewModel.setTempo(speed = viewModel.defaultSpeed)
            binding.pitchIcon -> viewModel.setTempo(pitch = viewModel.defaultPitch)
            binding.fixedPitchIcon -> viewModel.setTempo(isFixedPitch = !viewModel.tempo.isFixedPitch)
        }
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (fromUser) {
            when (slider) {
                binding.volumeSlider -> audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value.toInt(), 0)
                binding.leftBalanceSlider -> viewModel.setBalance(left = value, apply = false)
                binding.rightBalanceSlider -> viewModel.setBalance(right = value, apply = false)
                binding.speedSlider -> viewModel.setTempo(speed = value, apply = false)
                binding.pitchSlider -> viewModel.setTempo(pitch = value, apply = false)
            }
        }
    }

    override fun onStartTrackingTouch(slider: Slider) {}

    override fun onStopTrackingTouch(slider: Slider) {
        viewModel.applyPendingState()
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
        binding.volumeSlider.clearOnChangeListeners()
        binding.leftBalanceSlider.clearOnChangeListeners()
        binding.leftBalanceSlider.clearOnSliderTouchListeners()
        binding.rightBalanceSlider.clearOnChangeListeners()
        binding.rightBalanceSlider.clearOnSliderTouchListeners()
        binding.speedSlider.clearOnChangeListeners()
        binding.speedSlider.clearOnSliderTouchListeners()
        binding.pitchSlider.clearOnChangeListeners()
        binding.pitchSlider.clearOnSliderTouchListeners()
        super.onDestroy()
        visualizerManager.release()
        audioOutputObserver.stopObserver()
        _binding = null
    }
}