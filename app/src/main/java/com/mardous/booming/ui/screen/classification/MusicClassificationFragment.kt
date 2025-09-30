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

package com.mardous.booming.ui.screen.classification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mardous.booming.R
import com.mardous.booming.databinding.FragmentMusicClassificationBinding
import com.mardous.booming.extensions.launchAndRepeatWithViewLifecycle
import com.mardous.booming.extensions.showToast
import com.mardous.booming.ui.component.base.AbsMainActivityFragment
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Fragment for managing music classifications
 */
class MusicClassificationFragment : AbsMainActivityFragment(R.layout.fragment_music_classification) {
    
    private var _binding: FragmentMusicClassificationBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MusicClassificationViewModel by viewModel()
    
    private lateinit var classificationAdapter: ClassificationAdapter
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMusicClassificationBinding.bind(view)
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        
        // Check cloud service health on start
        viewModel.checkCloudServiceHealth()
    }
    
    private fun setupRecyclerView() {
        classificationAdapter = ClassificationAdapter { classification ->
            showClassificationDetails(classification)
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = classificationAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.apply {
            // Check cloud service health
            btnCheckHealth.setOnClickListener {
                viewModel.checkCloudServiceHealth()
            }
            
            // Classify all songs
            btnClassifyAll.setOnClickListener {
                showClassifyAllDialog()
            }
            
            // Refresh classifications
            btnRefresh.setOnClickListener {
                viewModel.loadAllClassifications()
            }
            
            // Generate playlists
            btnGeneratePlaylists.setOnClickListener {
                showGeneratePlaylistsDialog()
            }
            
            // Clear error
            btnClearError.setOnClickListener {
                viewModel.clearError()
            }
        }
    }
    
    private fun observeViewModel() {
        launchAndRepeatWithViewLifecycle {
            // Observe UI state
            viewModel.uiState.collect { state ->
                updateUi(state)
            }
        }
        
        launchAndRepeatWithViewLifecycle {
            // Observe stats
            viewModel.stats.collect { stats ->
                updateStats(stats)
            }
        }
        
        launchAndRepeatWithViewLifecycle {
            // Observe classifications
            viewModel.classifications.collect { classifications ->
                classificationAdapter.submitList(classifications)
                binding.emptyState.isVisible = classifications.isEmpty()
            }
        }
    }
    
    private fun updateUi(state: ClassificationUiState) {
        binding.apply {
            // Loading states
            progressBar.isVisible = state.isLoading
            batchProgressBar.isVisible = state.isBatchLoading
            healthProgressBar.isVisible = state.isCheckingHealth
            playlistProgressBar.isVisible = state.isGeneratingPlaylists
            
            // Error handling
            errorLayout.isVisible = state.error != null
            if (state.error != null) {
                errorText.text = state.error
            }
            
            // Cloud service health
            cloudServiceStatus.isVisible = state.isCloudServiceHealthy != null
            if (state.isCloudServiceHealthy == true) {
                cloudServiceStatus.text = "✅ Cloud service is healthy"
                cloudServiceStatus.setTextColor(getColor(R.color.success_color))
            } else if (state.isCloudServiceHealthy == false) {
                cloudServiceStatus.text = "❌ Cloud service is unavailable"
                cloudServiceStatus.setTextColor(getColor(R.color.error_color))
                if (state.cloudServiceError != null) {
                    cloudServiceStatus.text = "❌ ${state.cloudServiceError}"
                }
            }
            
            // Batch result
            if (state.batchResult != null) {
                showBatchResult(state.batchResult)
            }
            
            // Playlist generation result
            if (state.playlistGenerationResult != null) {
                showPlaylistGenerationResult(state.playlistGenerationResult)
            }
            
            // Last classification
            if (state.lastClassification != null) {
                showToast("Classified: ${state.lastClassification.songTitle} as ${state.lastClassification.classificationType}")
            }
        }
    }
    
    private fun updateStats(stats: ClassificationStats) {
        binding.apply {
            christianCount.text = stats.christianCount.toString()
            secularCount.text = stats.secularCount.toString()
            totalCount.text = stats.totalCount.toString()
            
            val christianPercentage = if (stats.totalCount > 0) {
                (stats.christianCount * 100 / stats.totalCount)
            } else 0
            val secularPercentage = if (stats.totalCount > 0) {
                (stats.secularCount * 100 / stats.totalCount)
            } else 0
            
            christianPercentageText.text = "$christianPercentage%"
            secularPercentageText.text = "$secularPercentage%"
        }
    }
    
    private fun showClassificationDetails(classification: com.mardous.booming.data.local.room.SongClassificationEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Classification Details")
            .setMessage(
                """
                Song: ${classification.songTitle}
                Artist: ${classification.artistName}
                Classification: ${classification.classificationType}
                Confidence: ${(classification.confidence * 100).toInt()}%
                Christian Probability: ${(classification.christianProbability * 100).toInt()}%
                Secular Probability: ${(classification.secularProbability * 100).toInt()}%
                Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(classification.classificationTimestamp))}
                ${if (classification.isManualOverride) "Manual Override: Yes" else ""}
                """.trimIndent()
            )
            .setPositiveButton("OK") { _, _ -> }
            .setNeutralButton("Change") { _, _ ->
                showChangeClassificationDialog(classification)
            }
            .setNegativeButton("Delete") { _, _ ->
                showDeleteConfirmationDialog(classification)
            }
            .show()
    }
    
    private fun showChangeClassificationDialog(classification: com.mardous.booming.data.local.room.SongClassificationEntity) {
        val options = arrayOf("Christian", "Secular")
        val currentIndex = if (classification.classificationType == "Christian") 0 else 1
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Classification")
            .setSingleChoiceItems(options, currentIndex) { _, which ->
                val newClassification = options[which]
                viewModel.updateClassificationManually(classification.songId, newClassification)
            }
            .setPositiveButton("Save") { _, _ -> }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }
    
    private fun showDeleteConfirmationDialog(classification: com.mardous.booming.data.local.room.SongClassificationEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Classification")
            .setMessage("Are you sure you want to delete the classification for '${classification.songTitle}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteClassification(classification.songId)
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }
    
    private fun showClassifyAllDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Classify All Songs")
            .setMessage("This will classify all songs in your library. This may take a while and use data. Continue?")
            .setPositiveButton("Start") { _, _ ->
                // TODO: Get all songs from repository and classify them
                showToast("Feature coming soon - will classify all songs in library")
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }
    
    private fun showBatchResult(result: com.mardous.booming.data.local.repository.ClassificationBatchResult) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Batch Classification Complete")
            .setMessage(
                """
                Processed: ${result.totalProcessed} songs
                Successful: ${result.successCount}
                Failed: ${result.failureCount}
                Success Rate: ${(result.successRate * 100).toInt()}%
                
                ${if (result.error != null) "Error: ${result.error}" else ""}
                """.trimIndent()
            )
            .setPositiveButton("OK") { _, _ ->
                viewModel.clearBatchResult()
            }
            .show()
    }
    
    private fun showGeneratePlaylistsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Generate Playlists")
            .setMessage("This will create 'Christian Music' and 'Secular Music' playlists based on your classifications. Continue?")
            .setPositiveButton("Generate") { _, _ ->
                viewModel.generatePlaylists()
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }
    
    private fun showPlaylistGenerationResult(result: com.mardous.booming.data.local.repository.PlaylistGenerationResult) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Playlist Generation Complete")
            .setMessage(
                """
                Christian Playlist: ${result.christianSongCount} songs
                Secular Playlist: ${result.secularSongCount} songs
                
                ${if (result.error != null) "Error: ${result.error}" else "Playlists created successfully!"}
                """.trimIndent()
            )
            .setPositiveButton("OK") { _, _ ->
                viewModel.clearPlaylistGenerationResult()
            }
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
