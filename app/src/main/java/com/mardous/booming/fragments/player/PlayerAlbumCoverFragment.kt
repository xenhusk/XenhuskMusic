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

package com.mardous.booming.fragments.player

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.viewpager.widget.ViewPager
import com.mardous.booming.R
import com.mardous.booming.adapters.pager.AlbumCoverPagerAdapter
import com.mardous.booming.databinding.FragmentPlayerAlbumCoverBinding
import com.mardous.booming.extensions.isLandscape
import com.mardous.booming.extensions.resources.BOOMING_ANIM_TIME
import com.mardous.booming.fragments.base.AbsMusicServiceFragment
import com.mardous.booming.helper.color.MediaNotificationProcessor
import com.mardous.booming.model.GestureOnCover
import com.mardous.booming.model.theme.NowPlayingScreen
import com.mardous.booming.transform.CarouselPagerTransformer
import com.mardous.booming.transform.ParallaxPagerTransformer
import com.mardous.booming.util.LEFT_RIGHT_SWIPING
import com.mardous.booming.util.LYRICS_ON_COVER
import com.mardous.booming.util.Preferences
import com.mardous.booming.viewmodels.player.PlayerViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class PlayerAlbumCoverFragment : AbsMusicServiceFragment(), ViewPager.OnPageChangeListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val playerViewModel: PlayerViewModel by activityViewModel()

    private var _binding: FragmentPlayerAlbumCoverBinding? = null
    private val binding get() = _binding!!
    private val viewPager get() = binding.viewPager

    private var coverLyricsFragment: CoverLyricsFragment? = null
    private val nps: NowPlayingScreen by lazy {
        Preferences.nowPlayingScreen
    }

    private var isShowLyricsOnCover: Boolean
        get() = Preferences.showLyricsOnCover
        set(value) { Preferences.showLyricsOnCover = value }

    private var gestureDetector: GestureDetector? = null
    private var callbacks: Callbacks? = null

    private var currentPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gestureDetector =
            GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                    return consumeGesture(GestureOnCover.Tap)
                }

                override fun onDoubleTap(event: MotionEvent): Boolean {
                    return consumeGesture(GestureOnCover.DoubleTap)
                }

                override fun onLongPress(e: MotionEvent) {
                    consumeGesture(GestureOnCover.LongPress)
                }
            })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_player_album_cover, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlayerAlbumCoverBinding.bind(view)
        coverLyricsFragment =
            childFragmentManager.findFragmentById(R.id.coverLyricsFragment) as? CoverLyricsFragment
        setupPageTransformer()
        Preferences.registerOnSharedPreferenceChangeListener(this)
    }

    private fun setupPageTransformer() {
        if (nps == NowPlayingScreen.Peek)
            return

        if (nps.supportsCarouselEffect && Preferences.isCarouselEffect && !resources.isLandscape) {
            val metrics = resources.displayMetrics
            val ratio = metrics.heightPixels.toFloat() / metrics.widthPixels.toFloat()
            val padding = if (ratio >= 1.777f) 40 else 100
            viewPager.clipToPadding = false
            viewPager.setPadding(padding, 0, padding, 0)
            viewPager.pageMargin = 0
            viewPager.setPageTransformer(false, CarouselPagerTransformer(requireContext()))
        } else if (nps == NowPlayingScreen.FullCover) {
            val transformer = ParallaxPagerTransformer(R.id.player_image)
            transformer.setSpeed(0.3f)
            viewPager.offscreenPageLimit = 2
            viewPager.setPageTransformer(false, transformer)
        } else {
            viewPager.offscreenPageLimit = 2
            viewPager.setPageTransformer(true, Preferences.coverSwipingEffect)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onStart() {
        super.onStart()
        viewPager.addOnPageChangeListener(this)
        viewPager.setOnTouchListener { _: View?, motionEvent: MotionEvent? ->
            if (motionEvent != null) {
                gestureDetector?.onTouchEvent(motionEvent) == true
            } else false
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (Preferences.getNowPlayingColorSchemeKey(nps) == key) {
            requestColor(currentPosition)
        } else when (key) {
            LYRICS_ON_COVER -> {
                val isShowLyrics = sharedPreferences.getBoolean(key, true)
                if (isShowLyrics && !binding.coverLyricsFragment.isVisible) {
                    showLyrics()
                } else if (!isShowLyrics && binding.coverLyricsFragment.isVisible) {
                    hideLyrics()
                }
            }

            LEFT_RIGHT_SWIPING -> {
                viewPager.setAllowSwiping(Preferences.allowCoverSwiping)
            }
        }
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
    override fun onPageScrollStateChanged(state: Int) {}
    override fun onPageSelected(position: Int) {
        currentPosition = position
        requestColor(position)
        if (position != playerViewModel.currentPosition) {
            playerViewModel.playSongAt(position)
        }
    }

    override fun onDestroyView() {
        viewPager.removeOnPageChangeListener(this)
        Preferences.unregisterOnSharedPreferenceChangeListener(this)
        gestureDetector = null
        _binding = null
        super.onDestroyView()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        updatePlayingQueue()
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        val position = playerViewModel.currentPosition
        if (viewPager.currentItem != position) {
            viewPager.setCurrentItem(position, true)
        }
    }

    override fun onQueueChanged() {
        super.onQueueChanged()
        updatePlayingQueue()
    }

    val isAllowedToLoadLyrics: Boolean
        get() = nps.supportsCoverLyrics

    fun toggleLyrics() {
        if (isShowLyricsOnCover) {
            hideLyrics(true)
        } else {
            showLyrics(true)
        }
    }

    fun showLyrics(isForced: Boolean = false) {
        if (binding.coverLyricsFragment.isVisible || (!isShowLyricsOnCover && !isForced))
            return

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(
            ObjectAnimator.ofFloat(binding.coverLyricsFragment, View.ALPHA, 1f),
            ObjectAnimator.ofFloat(binding.viewPager, View.ALPHA, 0f)
        )
        animatorSet.duration = BOOMING_ANIM_TIME
        animatorSet.doOnEnd {
            binding.viewPager.isInvisible = true
            it.removeAllListeners()
        }
        animatorSet.doOnStart {
            coverLyricsFragment?.let {
                childFragmentManager.beginTransaction()
                    .setMaxLifecycle(it, Lifecycle.State.RESUMED)
                    .commitAllowingStateLoss()
            }
            isShowLyricsOnCover = true
            binding.coverLyricsFragment.isVisible = true
        }
        callbacks?.onLyricsVisibilityChange(animatorSet, true)
        animatorSet.start()
    }

    fun hideLyrics(isPermanent: Boolean = false) {
        if (!binding.coverLyricsFragment.isVisible) return

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(
            ObjectAnimator.ofFloat(binding.coverLyricsFragment, View.ALPHA, 0f),
            ObjectAnimator.ofFloat(binding.viewPager, View.ALPHA, 1f)
        )
        animatorSet.duration = BOOMING_ANIM_TIME
        animatorSet.doOnStart {
            binding.viewPager.isInvisible = false
        }
        animatorSet.doOnEnd {
            coverLyricsFragment?.let {
                childFragmentManager.beginTransaction()
                    .setMaxLifecycle(it, Lifecycle.State.STARTED)
                    .commitAllowingStateLoss()
            }
            if (isPermanent) {
                isShowLyricsOnCover = false
            }
            binding.coverLyricsFragment.isVisible = false
            it.removeAllListeners()
        }
        callbacks?.onLyricsVisibilityChange(animatorSet, false)
        animatorSet.start()
    }

    private fun updatePlayingQueue() {
        _binding?.viewPager?.apply {
            adapter = AlbumCoverPagerAdapter(parentFragmentManager, playerViewModel.playingQueue)
            currentItem = playerViewModel.currentPosition
        }
        onPageSelected(playerViewModel.currentPosition)
    }

    private fun requestColor(position: Int) {
        if (playerViewModel.playingQueue.isNotEmpty()) {
            (viewPager.adapter as AlbumCoverPagerAdapter?)?.receiveColor(colorReceiver, position)
        }
    }

    private val colorReceiver = object : AlbumCoverPagerAdapter.AlbumCoverFragment.ColorReceiver {
        override fun onColorReady(color: MediaNotificationProcessor, request: Int) {
            if (currentPosition == request) {
                notifyColorChange(color)
            }
        }
    }

    private fun notifyColorChange(color: MediaNotificationProcessor) {
        callbacks?.onColorChanged(color)
    }

    private fun consumeGesture(gesture: GestureOnCover): Boolean {
        return callbacks?.onGestureDetected(gesture) ?: false
    }

    internal fun setCallbacks(callbacks: Callbacks?) {
        this.callbacks = callbacks
    }

    interface Callbacks {
        fun onColorChanged(color: MediaNotificationProcessor)
        fun onGestureDetected(gestureOnCover: GestureOnCover): Boolean
        fun onLyricsVisibilityChange(animatorSet: AnimatorSet, lyricsVisible: Boolean)
    }

    companion object {
        const val TAG = "PlayerAlbumCoverFragment"
    }
}