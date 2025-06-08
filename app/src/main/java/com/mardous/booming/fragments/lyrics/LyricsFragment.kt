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
package com.mardous.booming.fragments.lyrics

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.mardous.booming.R
import com.mardous.booming.databinding.FragmentLyricsBinding
import com.mardous.booming.extensions.applyWindowInsets
import com.mardous.booming.extensions.keepScreenOn
import com.mardous.booming.extensions.materialSharedAxis
import com.mardous.booming.fragments.base.AbsMainActivityFragment
import com.mardous.booming.helper.MusicProgressViewUpdateHelper
import com.mardous.booming.model.Song
import com.mardous.booming.service.MusicPlayer
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import kotlin.properties.Delegates

/**
 * @author Christians M. A. (mardous)
 */
class LyricsFragment : AbsMainActivityFragment(R.layout.fragment_lyrics),
    MusicProgressViewUpdateHelper.Callback {

    private var _binding: FragmentLyricsBinding? = null
    private val binding get() = _binding!!

    private val lyricsViewModel: LyricsViewModel by activityViewModel()

    private lateinit var progressViewUpdateHelper: MusicProgressViewUpdateHelper

    private var song: Song by Delegates.notNull()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        materialSharedAxis(view)
        view.applyWindowInsets(top = true, left = true, right = true, bottom = true)
        _binding = FragmentLyricsBinding.bind(view)
        progressViewUpdateHelper = MusicProgressViewUpdateHelper(this, 500, 1000)
        setupViews()
    }

    private fun setupViews() {
        binding.edit.setOnClickListener { editLyrics(song) }
        binding.lyricsView.apply {
            setDraggable(true) {
                MusicPlayer.seekTo(it.toInt())
                true
            }
        }
    }

    private fun loadLyrics() {
        lyricsViewModel.getAllLyrics(song, allowDownload = true)
            .observe(viewLifecycleOwner) { lyrics ->
                if (lyrics.loading) {
                    binding.progress.show()
                    binding.normalLyrics.isGone = true
                    binding.lyricsView.isGone = true
                } else {
                    binding.progress.hide()
                    binding.normalLyrics.text = lyrics.data
                    binding.normalLyrics.isGone = lyrics.isEmpty || lyrics.isSynced
                    binding.lyricsView.setLRCContent(lyrics.lrcData)
                    binding.lyricsView.updateTime(MusicPlayer.songProgressMillis.toLong())
                    binding.lyricsView.isVisible = lyrics.isEmpty || lyrics.isSynced
                }
            }
    }

    private fun updateCurrentSong() {
        song = MusicPlayer.currentSong
        if (song == Song.emptySong) {
            binding.edit.hide()
        } else {
            binding.edit.show()
        }
        loadLyrics()
    }

    override fun onResume() {
        super.onResume()
        updateCurrentSong()
        progressViewUpdateHelper.start()
        requireActivity().keepScreenOn(true)
    }

    override fun onPause() {
        super.onPause()
        progressViewUpdateHelper.stop()
        requireActivity().keepScreenOn(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onUpdateProgressViews(progress: Long, total: Long) {
        binding.lyricsView.updateTime(progress)
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        updateCurrentSong()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}
    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false

    private fun editLyrics(song: Song) {
        findNavController().navigate(
            R.id.nav_lyrics_editor,
            LyricsEditorFragmentArgs.Builder(song)
                .build()
                .toBundle()
        )
    }
}