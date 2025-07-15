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

package com.mardous.booming.fragments.player.base

import android.animation.ObjectAnimator
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.mardous.booming.R
import com.mardous.booming.extensions.getShapeAppearanceModel
import com.mardous.booming.extensions.launchAndRepeatWithViewLifecycle
import com.mardous.booming.extensions.media.durationStr
import com.mardous.booming.extensions.resources.applyColor
import com.mardous.booming.fragments.player.PlayerAnimator
import com.mardous.booming.fragments.player.PlayerColorScheme
import com.mardous.booming.fragments.player.PlayerTintTarget
import com.mardous.booming.model.NowPlayingAction
import com.mardous.booming.model.Song
import com.mardous.booming.preferences.dialog.NowPlayingExtraInfoPreferenceDialog
import com.mardous.booming.service.playback.Playback
import com.mardous.booming.util.*
import com.mardous.booming.viewmodels.player.PlayerViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * @author Christians M. A. (mardous)
 */
abstract class AbsPlayerControlsFragment(@LayoutRes layoutRes: Int) : Fragment(layoutRes),
    View.OnClickListener, View.OnLongClickListener, SharedPreferences.OnSharedPreferenceChangeListener {

    val playerViewModel: PlayerViewModel by activityViewModel()

    protected var playerFragment: AbsPlayerFragment? = null
    private var playerAnimator: PlayerAnimator? = null

    protected open val playPauseFab: FloatingActionButton? = null
    protected open val progressSlider: Slider? = null
    protected open val seekBar: SeekBar? = null
    protected open val repeatButton: MaterialButton? = null
    protected open val shuffleButton: MaterialButton? = null
    protected open val songTotalTime: TextView? = null
    protected open val songCurrentProgress: TextView? = null
    protected open val songInfoView: TextView? = null

    protected val isShuffleModeOn: Boolean
        get() = playerViewModel.shuffleMode.isOn

    protected val isRepeatModeOn: Boolean
        get() = playerViewModel.repeatMode.isOn

    private var lastPlaybackControlsColor: Int = 0
    private var lastDisabledPlaybackControlsColor: Int = 0

    private var isSeeking = false
    private var progressAnimator: ObjectAnimator? = null

    private var isShown = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        playerFragment = parentFragment as? AbsPlayerFragment
            ?: error("${javaClass.name} must be a child of ${AbsPlayerFragment::class.java.name}")
    }

    override fun onDetach() {
        super.onDetach()
        playerFragment = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.launchAndRepeatWithViewLifecycle {
            playerViewModel.colorSchemeFlow.collect { scheme ->
                lastPlaybackControlsColor = scheme.primaryControlColor
                lastDisabledPlaybackControlsColor = scheme.secondaryControlColor
            }
        }
        viewLifecycleOwner.launchAndRepeatWithViewLifecycle {
            playerViewModel.currentSongFlow.collect {
                onSongInfoChanged(it)
            }
        }
        viewLifecycleOwner.launchAndRepeatWithViewLifecycle {
            playerViewModel.extraInfoFlow.collect { extraInfo ->
                onExtraInfoChanged(extraInfo)
            }
        }
        viewLifecycleOwner.launchAndRepeatWithViewLifecycle {
            playerViewModel.isPlayingFlow.collect { isPlaying ->
                onUpdatePlayPause(isPlaying)
            }
        }
        viewLifecycleOwner.launchAndRepeatWithViewLifecycle {
            playerViewModel.repeatModeFlow.collect { repeatMode ->
                onUpdateRepeatMode(repeatMode)
            }
        }
        viewLifecycleOwner.launchAndRepeatWithViewLifecycle {
            playerViewModel.shuffleModeFlow.collect { shuffleMode ->
                onUpdateShuffleMode(shuffleMode)
            }
        }
        viewLifecycleOwner.launchAndRepeatWithViewLifecycle {
            playerViewModel.progressFlow.collect { progress ->
                if (!isSeeking) {
                    onUpdateSlider(progress.progress, progress.total)
                }
            }
        }
        Preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStart() {
        super.onStart()
        playerAnimator = onCreatePlayerAnimator()
        if (Preferences.circularPlayButton) {
            playPauseFab?.shapeAppearanceModel = requireContext().getShapeAppearanceModel(
                com.google.android.material.R.style.ShapeAppearance_Material3_Corner_Large,
                R.style.CircularShapeAppearance
            )
        }
        songTotalTime?.setOnClickListener(this)
        songInfoView?.setOnClickListener(this)
        songInfoView?.setOnLongClickListener(this)
        setUpProgressSlider()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.title -> {
                goToAlbum(requireActivity(), playerViewModel.currentSong)
            }
            R.id.text -> {
                goToArtist(requireActivity(), playerViewModel.currentSong)
            }
            R.id.songInfo -> {
                val playerView = this.view
                val infoString = playerViewModel.extraInfoFlow.value
                if (playerView != null && !infoString.isNullOrEmpty()) {
                    Snackbar.make(playerView, infoString, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (view.id == R.id.songInfo) {
            NowPlayingExtraInfoPreferenceDialog().show(childFragmentManager, "NOW_PLAYING_EXTRA_INFO")
            return true
        }
        return false
    }

    private fun setUpProgressSlider() {
        progressSlider?.addOnChangeListener { _, value, fromUser ->
            onProgressChange(value.toLong(), fromUser)
        }
        progressSlider?.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                onStartTrackingTouch()
            }

            override fun onStopTrackingTouch(slider: Slider) {
                onStopTrackingTouch(slider.value.toInt())
            }
        })

        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onProgressChange(progress.toLong(), fromUser)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                onStartTrackingTouch()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                onStopTrackingTouch(seekBar?.progress ?: 0)
            }
        })
    }

    private fun onUpdateSlider(progress: Long, total: Long) {
        if (seekBar == null) {
            progressSlider?.valueTo = total.toFloat().coerceAtLeast(1f)
            progressSlider?.value = progress.toFloat().coerceIn(progressSlider?.valueFrom, progressSlider?.valueTo)
        } else {
            if (isSeeking) {
                seekBar?.max = total.toInt()
                seekBar?.progress = progress.toInt()
            } else {
                progressAnimator = ObjectAnimator.ofInt(seekBar, "progress", progress.toInt()).apply {
                    duration = SLIDER_ANIMATION_TIME
                    interpolator = LinearInterpolator()
                    start()
                }
            }
        }
        songCurrentProgress?.text = progress.durationStr()
        songTotalTime?.text = if (Preferences.preferRemainingTime){
            (total - progress).coerceAtLeast(0L).durationStr()
        } else {
            total.durationStr()
        }
    }

    private fun onProgressChange(value: Long, fromUser: Boolean) {
        if (fromUser) {
            onUpdateSlider(value, playerViewModel.songDuration)
        }
    }

    private fun onStartTrackingTouch() {
        isSeeking = true
        progressAnimator?.cancel()
    }

    private fun onStopTrackingTouch(value: Int) {
        isSeeking = false
        playerViewModel.seekTo(value.toLong())
    }

    protected open fun onCreatePlayerAnimator(): PlayerAnimator? = null

    protected abstract fun onSongInfoChanged(song: Song)

    protected abstract fun onExtraInfoChanged(extraInfo: String?)

    abstract fun onQueueInfoChanged(newInfo: String?)

    protected abstract fun onUpdatePlayPause(isPlaying: Boolean)

    open fun onUpdateRepeatMode(repeatMode: Playback.RepeatMode) {
        val iconResource = when (repeatMode) {
            Playback.RepeatMode.One -> R.drawable.ic_repeat_one_24dp
            else -> R.drawable.ic_repeat_24dp
        }
        repeatButton?.let {
            it.setIconResource(iconResource)
            it.applyColor(
                getPlaybackControlsColor(repeatMode != Playback.RepeatMode.Off),
                isIconButton = true
            )
        }
    }

    open fun onUpdateShuffleMode(shuffleMode: Playback.ShuffleMode) {
        shuffleButton?.applyColor(
            getPlaybackControlsColor(shuffleMode == Playback.ShuffleMode.On),
            isIconButton = true
        )
    }

    /**
     * Called to notify that the player has been expanded.
     */
    internal open fun onShow() {
        isShown = true
        playerAnimator?.start()
    }

    /**
     * Called to notify that the player has been collapsed.
     */
    internal open fun onHide() {
        isShown = false
        playerAnimator?.prepare()
    }

    override fun onResume() {
        super.onResume()
        if (isShown && playerAnimator?.isPrepared == true) {
            onShow()
        } else if (!isShown && playerAnimator?.isPrepared == false) {
            onHide()
        }
    }

    abstract fun getTintTargets(scheme: PlayerColorScheme): List<PlayerTintTarget>

    protected fun setViewAction(view: View, action: NowPlayingAction) =
        playerFragment?.setViewAction(view, action)

    protected fun getSongArtist(song: Song) =
        playerFragment?.getSongArtist(song)

    protected fun isExtraInfoEnabled() =
        playerFragment?.isExtraInfoEnabled() ?: false

    protected fun getPlaybackControlsColor(
        isEnabled: Boolean,
        controlColor: Int = lastPlaybackControlsColor,
        disabledControlColor: Int = lastDisabledPlaybackControlsColor
    ) = if (isEnabled) controlColor else disabledControlColor

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            DISPLAY_EXTRA_INFO,
            EXTRA_INFO,
            DISPLAY_ALBUM_TITLE,
            PREFER_ALBUM_ARTIST_NAME -> onSongInfoChanged(playerViewModel.currentSong)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    companion object {
        const val SLIDER_ANIMATION_TIME: Long = 400
    }
}