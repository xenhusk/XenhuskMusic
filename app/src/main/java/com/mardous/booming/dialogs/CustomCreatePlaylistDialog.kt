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

package com.mardous.booming.dialogs

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogsBuilder
import com.mardous.booming.R
import com.mardous.booming.databinding.DialogCreatePlaylistCustomBinding
import com.mardous.booming.extensions.colorAccent
import com.mardous.booming.extensions.colorOnSurface
import com.mardous.booming.extensions.materialDialog
import com.mardous.booming.viewmodels.library.LibraryViewModel
import com.mardous.booming.viewmodels.library.model.AddToPlaylistResult

class CustomCreatePlaylistDialog : DialogFragment() {

    private var _binding: DialogCreatePlaylistCustomBinding? = null
    private val binding get() = _binding!!
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    
    private var selectedImageUri: Uri? = null
    private lateinit var imagePickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                selectedImageUri = uri
                loadSelectedImage()
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCreatePlaylistCustomBinding.inflate(LayoutInflater.from(requireContext()))
        
        binding.selectImageFab.setOnClickListener {
            selectImage()
        }
        
        return materialDialog(R.string.new_playlist)
            .setView(binding.root)
            .setPositiveButton(R.string.create_action) { _, _ ->
                val playlistName = binding.playlistNameEditText.text?.toString()?.trim()
                val description = binding.playlistDescriptionEditText.text?.toString()?.trim()
                
                if (playlistName.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), R.string.playlist_name_cannot_be_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                createCustomPlaylist(playlistName, description)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun selectImage() {
        imagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun loadSelectedImage() {
        selectedImageUri?.let { uri ->
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(binding.playlistCoverImageView)
        }
    }

    private fun createCustomPlaylist(playlistName: String, description: String?) {
        val customCoverUri = selectedImageUri?.toString()
        val finalDescription = if (description.isNullOrBlank()) null else description
        
        libraryViewModel.createCustomPlaylist(
            playlistName = playlistName,
            customCoverUri = customCoverUri,
            description = finalDescription
        ).observe(this, Observer { result ->
            handlePlaylistCreationResult(result)
        })
    }

    private fun handlePlaylistCreationResult(result: AddToPlaylistResult) {
        if (result.isWorking) return
        
        if (result.playlistCreated) {
            Toast.makeText(
                requireContext(),
                getString(R.string.playlist_created, result.playlistName),
                Toast.LENGTH_SHORT
            ).show()
            dismiss()
        } else {
            Toast.makeText(
                requireContext(),
                getString(R.string.playlist_exists, result.playlistName),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun create(): CustomCreatePlaylistDialog {
            return CustomCreatePlaylistDialog()
        }
    }
}
