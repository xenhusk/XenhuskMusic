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

package com.mardous.booming.fragments.player.styles.fullcoverstyle

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.mardous.booming.R
import com.mardous.booming.databinding.FragmentFullCoverPlayerPlaybackControlsBinding
import com.mardous.booming.extensions.resources.applyColor
import com.mardous.booming.extensions.resources.showBounceAnimation
import com.mardous.booming.extensions.resources.toColorStateList
import com.mardous.booming.fragments.player.PlayerAnimator
import com.mardous.booming.fragments.player.base.AbsPlayerControlsFragment
import com.mardous.booming.helper.handler.PrevNextButtonOnTouchHandler
import com.mardous.booming.model.NowPlayingAction
import com.mardous.booming.model.Song
import com.mardous.booming.service.MusicPlayer
import com.mardous.booming.util.Preferences
import java.util.LinkedList

/**
 * @author Christians M. A. (mardous)
 */
class FullCoverPlayerControlsFragment : AbsPlayerControlsFragment(R.layout.fragment_full_cover_player_playback_controls),
    SeekBar.OnSeekBarChangeListener {

    private var _binding: FragmentFullCoverPlayerPlaybackControlsBinding? = null
    private val binding get() = _binding!!

    override val playPauseFab: FloatingActionButton
        get() = binding.playPauseButton

    override val progressSlider: Slider
        get() = binding.progressSlider

    override val songCurrentProgress: TextView
        get() = binding.songCurrentProgress

    override val songTotalTime: TextView
        get() = binding.songTotalTime

    override val songInfoView: TextView
        get() = binding.songInfo

    @ColorInt
    private var isFavoriteIconColor: Int = Color.TRANSPARENT
    private var isFavorite: Boolean = false

    private var playbackControlsColor = 0
    private var disabledPlaybackControlsColor = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isFavoriteIconColor = requireContext().getColor(R.color.favoriteColor)
        _binding = FragmentFullCoverPlayerPlaybackControlsBinding.bind(view)
        setupColors()
        setupListeners()
        setViewAction(binding.favorite, NowPlayingAction.ToggleFavoriteState)
        playerFragment?.inflateMenuInView(binding.menu)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v: View, insets: WindowInsetsCompat ->
            val navigationBar = insets.getInsets(Type.systemBars())
            v.updatePadding(bottom = navigationBar.bottom)
            val displayCutout = insets.getInsets(Type.displayCutout())
            v.updatePadding(left = displayCutout.left, right = displayCutout.right)
            insets
        }
    }

    private fun setupColors() {
        if (playbackControlsColor == 0 || disabledPlaybackControlsColor == 0)
            return

        setColors(Color.TRANSPARENT, playbackControlsColor, disabledPlaybackControlsColor)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        binding.text.setOnClickListener(this)
        binding.playPauseButton.setOnClickListener(this)
        binding.next.setOnTouchListener(PrevNextButtonOnTouchHandler(PrevNextButtonOnTouchHandler.DIRECTION_NEXT))
        binding.previous.setOnTouchListener(PrevNextButtonOnTouchHandler(PrevNextButtonOnTouchHandler.DIRECTION_PREVIOUS))
        binding.shuffleButton.setOnClickListener(this)
        binding.repeatButton.setOnClickListener(this)
    }

    override fun onClick(view: View) {
        super.onClick(view)
        when (view) {
            binding.shuffleButton -> MusicPlayer.toggleShuffleMode()
            binding.repeatButton -> MusicPlayer.cycleRepeatMode()
            binding.playPauseButton -> {
                MusicPlayer.togglePlayPause()
                view.showBounceAnimation()
            }
        }
    }

    override fun onCreatePlayerAnimator(): PlayerAnimator {
        return FullCoverPlayerAnimator(binding, Preferences.animateControls)
    }

    override fun setColors(backgroundColor: Int, primaryControlColor: Int, secondaryControlColor: Int) {
        this.playbackControlsColor = primaryControlColor
        this.disabledPlaybackControlsColor = secondaryControlColor

        if (_binding == null) return
        binding.title.setTextColor(primaryControlColor)
        binding.text.setTextColor(primaryControlColor)
        binding.songInfo.setTextColor(secondaryControlColor)

        val primaryTintList = primaryControlColor.toColorStateList()
        binding.menu.imageTintList = primaryTintList
        binding.favorite.imageTintList = primaryTintList

        binding.progressSlider.applyColor(primaryControlColor)
        binding.songCurrentProgress.setTextColor(secondaryControlColor)
        binding.songTotalTime.setTextColor(secondaryControlColor)

        binding.playPauseButton.backgroundTintList = primaryTintList
        binding.playPauseButton.imageTintList = backgroundColor.toColorStateList()
        binding.shuffleButton.setColors(secondaryControlColor, primaryControlColor)
        binding.repeatButton.setColors(secondaryControlColor, primaryControlColor)
        binding.next.setColorFilter(primaryControlColor, PorterDuff.Mode.SRC_IN)
        binding.previous.setColorFilter(primaryControlColor, PorterDuff.Mode.SRC_IN)
    }

    override fun onSongInfoChanged(song: Song) {
        _binding?.let { nonNullBinding ->
            nonNullBinding.title.text = song.title
            nonNullBinding.text.text = getSongArtist(song)
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

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (binding.progressSlider == seekBar && fromUser) {
            MusicPlayer.seekTo(progress)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar?) {}

    internal fun setFavorite(isFavorite: Boolean, withAnimation: Boolean) {
        if (this.isFavorite != isFavorite) {
            this.isFavorite = isFavorite
            val iconRes = if (withAnimation) {
                if (isFavorite) R.drawable.avd_favorite else R.drawable.avd_unfavorite
            } else {
                if (isFavorite) R.drawable.ic_favorite_24dp else R.drawable.ic_favorite_outline_24dp
            }
            binding.favorite.setImageResource(iconRes)
            binding.favorite.drawable?.let {
                if (it is AnimatedVectorDrawable) {
                    it.start()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class FullCoverPlayerAnimator(
        private val binding: FragmentFullCoverPlayerPlaybackControlsBinding,
        isEnabled: Boolean
    ) : PlayerAnimator(isEnabled) {
        private fun addAlphaAnimation(animators: LinkedList<Animator>, view: View, interpolator: TimeInterpolator) {
            animators.add(ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
                this.interpolator = interpolator
                this.duration = 500
            })
        }

        override fun onAddAnimation(animators: LinkedList<Animator>, interpolator: TimeInterpolator) {
            addAlphaAnimation(animators, binding.playPauseButton, interpolator)
            addAlphaAnimation(animators, binding.next, interpolator)
            addAlphaAnimation(animators, binding.previous, interpolator)
            addAlphaAnimation(animators, binding.shuffleButton, interpolator)
            addAlphaAnimation(animators, binding.repeatButton, interpolator)
            addAlphaAnimation(animators, binding.songCurrentProgress, interpolator)
            addAlphaAnimation(animators, binding.songTotalTime, interpolator)
            addAlphaAnimation(animators, binding.songInfo, interpolator)
        }

        override fun onPrepareForAnimation() {
            binding.playPauseButton.alpha = 0f
            binding.next.alpha = 0f
            binding.previous.alpha = 0f
            binding.shuffleButton.alpha = 0f
            binding.repeatButton.alpha = 0f
            binding.songCurrentProgress.alpha = 0f
            binding.songTotalTime.alpha = 0f
            binding.songInfo.alpha = 0f
        }
    }
}