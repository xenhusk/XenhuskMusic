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

package com.mardous.booming.fragments.player.styles.plainstyle

import android.animation.Animator
import android.animation.TimeInterpolator
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.BounceInterpolator
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mardous.booming.R
import com.mardous.booming.databinding.FragmentPlainPlayerPlaybackControlsBinding
import com.mardous.booming.extensions.resources.controlColorNormal
import com.mardous.booming.extensions.resources.getPrimaryTextColor
import com.mardous.booming.fragments.player.PlayerAnimator
import com.mardous.booming.fragments.player.base.AbsPlayerControlsFragment
import com.mardous.booming.helper.handler.PrevNextButtonOnTouchHandler
import com.mardous.booming.model.Song
import com.mardous.booming.service.MusicPlayer
import com.mardous.booming.util.Preferences
import java.util.LinkedList

/**
 * @author Christians M. A. (mardous)
 */
class PlainPlayerControlsFragment : AbsPlayerControlsFragment(R.layout.fragment_plain_player_playback_controls) {

    private var _binding: FragmentPlainPlayerPlaybackControlsBinding? = null
    private val binding get() = _binding!!

    override val playPauseFab: FloatingActionButton
        get() = binding.playPauseButton

    override val seekBar: SeekBar
        get() = binding.progressSlider

    override val songCurrentProgress: TextView
        get() = binding.songCurrentProgress

    override val songTotalTime: TextView
        get() = binding.songTotalTime

    override val songInfoView: TextView?
        get() = binding.songInfo

    private var playbackControlsColor = 0
    private var disabledPlaybackControlsColor = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlainPlayerPlaybackControlsBinding.bind(view)
        setupColors()
        setupListeners()
    }

    private fun setupColors() {
        playbackControlsColor = controlColorNormal()
        disabledPlaybackControlsColor = getPrimaryTextColor(requireContext(), isDisabled = true)
        setColors(Color.TRANSPARENT, playbackControlsColor, disabledPlaybackControlsColor)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        binding.playPauseButton.setOnClickListener(this)
        binding.nextButton.setOnTouchListener(PrevNextButtonOnTouchHandler(PrevNextButtonOnTouchHandler.DIRECTION_NEXT))
        binding.previousButton.setOnTouchListener(PrevNextButtonOnTouchHandler(PrevNextButtonOnTouchHandler.DIRECTION_PREVIOUS))
        binding.shuffleButton.setOnClickListener(this)
        binding.repeatButton.setOnClickListener(this)
    }

    override fun onCreatePlayerAnimator(): PlayerAnimator {
        return PlainPlayerAnimator(binding, Preferences.animateControls)
    }

    override fun onSongInfoChanged(song: Song) {
        _binding?.let { nonNullBinding ->
            if (isExtraInfoEnabled()) {
                nonNullBinding.songInfo.text = getExtraInfoString(song)
                nonNullBinding.songInfo.isVisible = true
            } else {
                nonNullBinding.songInfo.isVisible = false
            }
        }
    }

    override fun onQueueInfoChanged(newInfo: String?) {}

    override fun onUpdatePlayPause(isPlaying: Boolean) {
        if (isPlaying) {
            _binding?.playPauseButton?.setImageResource(R.drawable.ic_pause_24dp)
        } else {
            _binding?.playPauseButton?.setImageResource(R.drawable.ic_play_24dp)
        }
    }

    override fun onUpdateRepeatMode(repeatMode: Int) {
        _binding?.repeatButton?.setRepeatMode(repeatMode)
    }

    override fun onUpdateShuffleMode(shuffleMode: Int) {
        _binding?.shuffleButton?.setShuffleMode(shuffleMode)
    }

    override fun onShow() {
        super.onShow()
        binding.playPauseButton.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setInterpolator(BounceInterpolator())
            .start()
    }

    override fun onHide() {
        super.onHide()
        binding.playPauseButton.apply {
            scaleX = 0f
            scaleY = 0f
        }
    }

    override fun onClick(view: View) {
        super.onClick(view)
        when (view) {
            binding.repeatButton -> MusicPlayer.cycleRepeatMode()
            binding.shuffleButton -> MusicPlayer.toggleShuffleMode()
            binding.playPauseButton -> MusicPlayer.togglePlayPause()
        }
    }

    override fun setColors(backgroundColor: Int, primaryControlColor: Int, secondaryControlColor: Int) {
        _binding?.shuffleButton?.setColors(secondaryControlColor, primaryControlColor)
        _binding?.repeatButton?.setColors(secondaryControlColor, primaryControlColor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class PlainPlayerAnimator(
        private val binding: FragmentPlainPlayerPlaybackControlsBinding,
        isEnabled: Boolean
    ) : PlayerAnimator(isEnabled) {
        override fun onAddAnimation(animators: LinkedList<Animator>, interpolator: TimeInterpolator) {
            addScaleAnimation(animators, binding.shuffleButton, interpolator, 100)
            addScaleAnimation(animators, binding.repeatButton, interpolator, 100)
            addScaleAnimation(animators, binding.previousButton, interpolator, 100)
            addScaleAnimation(animators, binding.nextButton, interpolator, 100)
            addScaleAnimation(animators, binding.songCurrentProgress, interpolator, 200)
            addScaleAnimation(animators, binding.songTotalTime, interpolator, 200)
        }

        override fun onPrepareForAnimation() {
            prepareForScaleAnimation(binding.previousButton)
            prepareForScaleAnimation(binding.nextButton)
            prepareForScaleAnimation(binding.shuffleButton)
            prepareForScaleAnimation(binding.repeatButton)
            prepareForScaleAnimation(binding.songCurrentProgress)
            prepareForScaleAnimation(binding.songTotalTime)
        }
    }
}
