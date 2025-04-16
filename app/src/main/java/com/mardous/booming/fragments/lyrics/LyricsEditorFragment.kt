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

import android.app.Activity
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mardous.booming.R
import com.mardous.booming.databinding.DialogLyricsSelectorBinding
import com.mardous.booming.databinding.DialogSongSearchBinding
import com.mardous.booming.databinding.FragmentLyricsEditorBinding
import com.mardous.booming.extensions.*
import com.mardous.booming.extensions.files.copyToUri
import com.mardous.booming.extensions.glide.getDefaultGlideTransition
import com.mardous.booming.extensions.glide.getSongGlideModel
import com.mardous.booming.extensions.glide.songOptions
import com.mardous.booming.extensions.media.albumArtistName
import com.mardous.booming.extensions.media.displayArtistName
import com.mardous.booming.extensions.media.isArtistNameUnknown
import com.mardous.booming.extensions.resources.animateToggle
import com.mardous.booming.fragments.base.AbsMainActivityFragment
import com.mardous.booming.http.Result
import com.mardous.booming.model.DownloadedLyrics
import com.mardous.booming.model.Song
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import java.io.File

/**
 * @author Christians M. A. (mardous)
 */
class LyricsEditorFragment : AbsMainActivityFragment(R.layout.fragment_lyrics_editor),
    MaterialButtonToggleGroup.OnButtonCheckedListener {

    private val lyricsViewModel: LyricsViewModel by activityViewModel()
    private val args: LyricsEditorFragmentArgs by navArgs()
    private val song get() = args.extraSong

    private var _binding: FragmentLyricsEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var editLyricsLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var pendingWrite: Pair<File, Uri>? = null

    private var plainLyricsModified = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLyricsEditorBinding.bind(view)

        materialSharedAxis(view)
        view.applyWindowInsets(left = true, right = true, bottom = true, ime = !isLandscape())
        setSupportActionBar(binding.toolbar)
        editLyricsLauncher = registerForActivityResult(StartIntentSenderForResult()) {
            if (it.resultCode == Activity.RESULT_OK && pendingWrite != null) {
                pendingWrite!!.first.copyToUri(requireContext(), pendingWrite!!.second)
            }
        }

        binding.toggleGroup.check(R.id.normalButton)
        binding.toggleGroup.addOnButtonCheckedListener(this)
        binding.search.setOnClickListener { searchLyrics() }
        binding.download.setOnClickListener { downloadLyrics() }
        binding.save.setOnClickListener { saveLyrics() }
        binding.title.text = song.title
        binding.text.text = song.displayArtistName()

        Glide.with(this)
            .asBitmap()
            .load(song.getSongGlideModel())
            .songOptions(song)
            .transition(getDefaultGlideTransition())
            .into(binding.image)

        updateEditor()

        lyricsViewModel.getAllLyrics(song).observe(viewLifecycleOwner) {
            binding.plainInput.setText(it.data)
            binding.plainInput.doOnTextChanged { _, _, _, _ -> plainLyricsModified = true }
            binding.syncedInput.setText(it.lrcData.getText())
        }
    }

    private fun updateEditor() {
        val isSyncedMode = binding.toggleGroup.checkedButtonId == R.id.syncedButton
        binding.plainInputLayout.isGone = isSyncedMode
        binding.syncedInputLayout.isVisible = isSyncedMode
    }

    private fun searchLyrics() {
        showSongTagsInput(song, R.string.search_lyrics) { title: String, artist: String ->
            val searchSuffix = getString(R.string.lyrics).lowercase()
            if (artist.isArtistNameUnknown()) {
                context?.webSearch(title, searchSuffix)
            } else {
                context?.webSearch(artist, title, searchSuffix)
            }
        }
    }

    private fun downloadLyrics() {
        if (!requireContext().isOnline(false)) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.connection_unavailable)
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        showSongTagsInput(song, R.string.download_lyrics) { title: String, artist: String ->
            lyricsViewModel.getOnlineLyrics(song, title, artist).observe(viewLifecycleOwner) { result ->
                if (result is Result.Loading) {
                    binding.download.isEnabled = false
                    binding.progressIndicator.show()
                } else {
                    binding.download.isEnabled = true
                    binding.progressIndicator.hide()
                    if (result is Result.Success) {
                        val downloadedLyrics = result.data
                        if (downloadedLyrics.hasMultiOptions) {
                            showLyricsSelector(downloadedLyrics)
                        } else if (!downloadedLyrics.syncedLyrics.isNullOrEmpty()) {
                            binding.syncedInput.setText(downloadedLyrics.syncedLyrics)
                            binding.toggleGroup.check(R.id.syncedButton)
                        } else if (!downloadedLyrics.plainLyrics.isNullOrEmpty()) {
                            binding.plainInput.setText(downloadedLyrics.plainLyrics)
                            binding.toggleGroup.check(R.id.normalButton)
                        } else {
                            downloadError()
                        }
                    } else {
                        downloadError()
                    }
                }
            }
        }
    }

    private fun showSongTagsInput(song: Song, titleRes: Int, onInput: (title: String, artistName: String) -> Unit) {
        val dialogBinding = DialogSongSearchBinding.inflate(layoutInflater)
        dialogBinding.titleInput.setText(song.title)
        dialogBinding.artistInput.setText(song.albumArtistName())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                val title = dialogBinding.titleInput.text?.toString()
                val artist = dialogBinding.artistInput.text?.toString()
                if (title.isNullOrBlank() || artist.isNullOrBlank()) {
                    showToast(R.string.album_or_artist_empty)
                } else {
                    onInput(title, artist)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLyricsSelector(downloadedLyrics: DownloadedLyrics) {
        val dialogBinding = DialogLyricsSelectorBinding.inflate(layoutInflater)
        dialogBinding.normalLyrics.setOnClickListener { dialogBinding.normalLyricsCheck.animateToggle() }
        dialogBinding.syncedLyrics.setOnClickListener { dialogBinding.syncedLyricsCheck.animateToggle() }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.choose_lyrics)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                if (dialogBinding.normalLyricsCheck.isChecked) {
                    binding.plainInput.setText(downloadedLyrics.plainLyrics ?: "")
                }
                if (dialogBinding.syncedLyricsCheck.isChecked) {
                    binding.syncedInput.setText(downloadedLyrics.syncedLyrics ?: "")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadError() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.cannot_download_lyrics)
            .setPositiveButton(R.string.yes) { _: DialogInterface, _: Int ->
                searchLyrics()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun saveLyrics() {
        lyricsViewModel.saveLyrics(
            requireContext(),
            song,
            binding.plainInput.text?.toString(),
            binding.syncedInput.text?.toString(),
            plainLyricsModified
        ).observe(viewLifecycleOwner) {
            if (it.isPending && hasR()) {
                pendingWrite = it.pendingWrite
                val pendingIntent = MediaStore.createWriteRequest(
                    requireContext().contentResolver, listOf(song.mediaStoreUri)
                )
                editLyricsLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
            } else {
                if (it.isSuccess) {
                    showToast(R.string.lyrics_were_updated_successfully, Toast.LENGTH_SHORT)
                } else {
                    showToast(R.string.failed_to_update_lyrics, Toast.LENGTH_SHORT)
                }
            }
        }
    }

    override fun onButtonChecked(group: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean) {
        updateEditor()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}
    override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
        when (menuItem.itemId) {
            android.R.id.home -> {
                findNavController().navigateUp()
                true
            }
            else -> false
        }
}