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

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.mardous.booming.R
import com.mardous.booming.databinding.FragmentMiniPlayerBinding
import com.mardous.booming.extensions.glide.getDefaultGlideTransition
import com.mardous.booming.extensions.glide.getSongGlideModel
import com.mardous.booming.extensions.glide.songOptions
import com.mardous.booming.extensions.isTablet
import com.mardous.booming.extensions.launchAndRepeatWithViewLifecycle
import com.mardous.booming.extensions.media.displayArtistName
import com.mardous.booming.extensions.resources.show
import com.mardous.booming.extensions.resources.textColorPrimary
import com.mardous.booming.extensions.resources.textColorSecondary
import com.mardous.booming.extensions.resources.toForegroundColorSpan
import com.mardous.booming.extensions.utilities.DEFAULT_INFO_DELIMITER
import com.mardous.booming.util.Preferences
import com.mardous.booming.viewmodels.player.PlayerViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import kotlin.math.abs

class MiniPlayerFragment : Fragment(R.layout.fragment_mini_player),
    View.OnClickListener {

    private val playerViewModel: PlayerViewModel by activityViewModel()

    private var _binding: FragmentMiniPlayerBinding? = null
    private val binding get() = _binding!!

    private lateinit var primaryColorSpan: ForegroundColorSpan
    private lateinit var secondaryColorSpan: ForegroundColorSpan

    private var target: Target<Bitmap>? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMiniPlayerBinding.bind(view)
        viewLifecycleOwner.launchAndRepeatWithViewLifecycle {
            playerViewModel.currentSongFlow.collect {
                updateCurrentSong()
            }
        }
        viewLifecycleOwner.launchAndRepeatWithViewLifecycle {
            playerViewModel.progressFlow.collect {
                binding.progressBar.max = it.total.toInt()
                binding.progressBar.setProgressCompat(it.progress.toInt(), true)
            }
        }
        viewLifecycleOwner.launchAndRepeatWithViewLifecycle {
            playerViewModel.isPlayingFlow.collect { isPlaying ->
                if (isPlaying) {
                    binding.actionPlayPause.setIconResource(R.drawable.ic_pause_24dp)
                } else {
                    binding.actionPlayPause.setIconResource(R.drawable.ic_play_24dp)
                }
            }
        }
        primaryColorSpan = textColorPrimary().toForegroundColorSpan()
        secondaryColorSpan = textColorSecondary().toForegroundColorSpan()
        setUpButtons()
        view.setOnTouchListener { _, event -> flingPlayBackController.onTouchEvent(event) }
    }

    private fun setUpButtons() {
        setupExtraControls()
        binding.actionNext.setOnClickListener(this)
        binding.actionPrevious.setOnClickListener(this)
        binding.actionPlayPause.setOnClickListener(this)
    }

    fun setupExtraControls() {
        if (resources.isTablet) {
            binding.actionNext.show()
            binding.actionPrevious.show()
        } else {
            binding.actionNext.isVisible = Preferences.extraControls
            binding.actionPrevious.isVisible = Preferences.extraControls
        }
    }

    override fun onClick(view: View) {
        when (view) {
            binding.actionPlayPause -> playerViewModel.togglePlayPause()
            binding.actionNext -> playerViewModel.playNext()
            binding.actionPrevious -> playerViewModel.playPrevious()
        }
    }

    override fun onDestroyView() {
        Glide.with(this).clear(target)
        super.onDestroyView()
        _binding = null
    }

    private fun updateCurrentSong() {
        val song = playerViewModel.currentSong

        val builder = SpannableStringBuilder()
        val title = SpannableString(song.title).apply {
            setSpan(primaryColorSpan, 0, length, 0)
        }
        val text = SpannableString(song.displayArtistName()).apply {
            setSpan(secondaryColorSpan, 0, length, 0)
        }

        //binding.songInfo.isSelected = true
        binding.songInfo.text = builder.append(title).append(DEFAULT_INFO_DELIMITER).append(text)

        target = Glide.with(this@MiniPlayerFragment)
            .asBitmap()
            .load(song.getSongGlideModel())
            .transition(getDefaultGlideTransition())
            .songOptions(song)
            .into(binding.image)
    }

    private var flingPlayBackController = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (abs(velocityX) > abs(velocityY)) {
                    if (velocityX < 0) {
                        playerViewModel.playNext()
                        return true
                    } else if (velocityX > 0) {
                        playerViewModel.playPrevious()
                        return true
                    }
                }
                return false
            }
        })
}