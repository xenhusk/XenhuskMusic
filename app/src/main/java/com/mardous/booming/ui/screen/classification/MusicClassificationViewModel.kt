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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.data.local.repository.MusicClassificationRepository
import com.mardous.booming.data.local.repository.ClassificationStats
import com.mardous.booming.data.local.repository.ClassificationBatchResult
import com.mardous.booming.data.local.repository.PlaylistGenerationService
import com.mardous.booming.data.local.repository.PlaylistGenerationResult
import com.mardous.booming.data.local.room.SongClassificationEntity
import com.mardous.booming.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for music classification management
 */
class MusicClassificationViewModel(
    private val classificationRepository: MusicClassificationRepository,
    private val playlistGenerationService: PlaylistGenerationService
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(ClassificationUiState())
    val uiState: StateFlow<ClassificationUiState> = _uiState.asStateFlow()
    
    // Classification stats
    private val _stats = MutableStateFlow(ClassificationStats(0, 0, 0))
    val stats: StateFlow<ClassificationStats> = _stats.asStateFlow()
    
    // All classifications
    private val _classifications = MutableStateFlow<List<SongClassificationEntity>>(emptyList())
    val classifications: StateFlow<List<SongClassificationEntity>> = _classifications.asStateFlow()
    
    // Christian classifications
    private val _christianClassifications = MutableStateFlow<List<SongClassificationEntity>>(emptyList())
    val christianClassifications: StateFlow<List<SongClassificationEntity>> = _christianClassifications.asStateFlow()
    
    // Secular classifications
    private val _secularClassifications = MutableStateFlow<List<SongClassificationEntity>>(emptyList())
    val secularClassifications: StateFlow<List<SongClassificationEntity>> = _secularClassifications.asStateFlow()
    
    init {
        loadStats()
        loadAllClassifications()
    }
    
    /**
     * Load classification statistics
     */
    private fun loadStats() {
        viewModelScope.launch {
            try {
                val stats = classificationRepository.getClassificationStats()
                _stats.value = stats
            } catch (e: Exception) {
                updateUiState { copy(error = e.message) }
            }
        }
    }
    
    /**
     * Load all classifications
     */
    private fun loadAllClassifications() {
        viewModelScope.launch {
            try {
                val classifications = classificationRepository.getAllClassifications()
                _classifications.value = classifications
                
                // Separate by type
                _christianClassifications.value = classifications.filter { it.classificationType == "Christian" }
                _secularClassifications.value = classifications.filter { it.classificationType == "Secular" }
            } catch (e: Exception) {
                updateUiState { copy(error = e.message) }
            }
        }
    }
    
    /**
     * Classify a single song
     */
    fun classifySong(song: Song) {
        viewModelScope.launch {
            updateUiState { copy(isLoading = true, error = null) }
            
            try {
                val result = classificationRepository.classifySong(song)
                if (result.isSuccess) {
                    val classification = result.getOrThrow()
                    updateUiState { copy(isLoading = false, lastClassification = classification) }
                    loadStats() // Refresh stats
                    loadAllClassifications() // Refresh classifications
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Classification failed"
                    updateUiState { copy(isLoading = false, error = error) }
                }
            } catch (e: Exception) {
                updateUiState { copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    /**
     * Classify multiple songs in batch
     */
    fun classifySongsBatch(songs: List<Song>) {
        viewModelScope.launch {
            updateUiState { copy(isBatchLoading = true, error = null) }
            
            try {
                val result = classificationRepository.classifySongsBatch(songs)
                updateUiState { 
                    copy(
                        isBatchLoading = false, 
                        batchResult = result,
                        error = result.error
                    ) 
                }
                loadStats() // Refresh stats
                loadAllClassifications() // Refresh classifications
            } catch (e: Exception) {
                updateUiState { copy(isBatchLoading = false, error = e.message) }
            }
        }
    }
    
    /**
     * Update classification manually
     */
    fun updateClassificationManually(songId: Long, newClassification: String) {
        viewModelScope.launch {
            try {
                val result = classificationRepository.updateClassificationManually(songId, newClassification)
                if (result.isSuccess) {
                    loadStats() // Refresh stats
                    loadAllClassifications() // Refresh classifications
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Update failed"
                    updateUiState { copy(error = error) }
                }
            } catch (e: Exception) {
                updateUiState { copy(error = e.message) }
            }
        }
    }
    
    /**
     * Delete classification
     */
    fun deleteClassification(songId: Long) {
        viewModelScope.launch {
            try {
                val result = classificationRepository.deleteClassification(songId)
                if (result.isSuccess) {
                    loadStats() // Refresh stats
                    loadAllClassifications() // Refresh classifications
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Delete failed"
                    updateUiState { copy(error = error) }
                }
            } catch (e: Exception) {
                updateUiState { copy(error = e.message) }
            }
        }
    }
    
    /**
     * Check cloud service health
     */
    fun checkCloudServiceHealth() {
        viewModelScope.launch {
            updateUiState { copy(isCheckingHealth = true) }
            
            try {
                val result = classificationRepository.checkCloudServiceHealth()
                val isHealthy = result.isSuccess && result.getOrNull() == true
                updateUiState { 
                    copy(
                        isCheckingHealth = false,
                        isCloudServiceHealthy = isHealthy,
                        cloudServiceError = if (isHealthy) null else result.exceptionOrNull()?.message
                    ) 
                }
            } catch (e: Exception) {
                updateUiState { 
                    copy(
                        isCheckingHealth = false,
                        isCloudServiceHealthy = false,
                        cloudServiceError = e.message
                    ) 
                }
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        updateUiState { copy(error = null) }
    }
    
    /**
     * Clear batch result
     */
    fun clearBatchResult() {
        updateUiState { copy(batchResult = null) }
    }
    
    /**
     * Generate playlists based on classifications
     */
    fun generatePlaylists() {
        viewModelScope.launch {
            updateUiState { copy(isGeneratingPlaylists = true, error = null) }
            
            try {
                val result = playlistGenerationService.generateClassificationPlaylists()
                updateUiState { 
                    copy(
                        isGeneratingPlaylists = false,
                        playlistGenerationResult = result,
                        error = result.error
                    ) 
                }
            } catch (e: Exception) {
                updateUiState { copy(isGeneratingPlaylists = false, error = e.message) }
            }
        }
    }
    
    /**
     * Clear playlist generation result
     */
    fun clearPlaylistGenerationResult() {
        updateUiState { copy(playlistGenerationResult = null) }
    }
    
    /**
     * Helper function to update UI state
     */
    private fun updateUiState(update: ClassificationUiState.() -> ClassificationUiState) {
        _uiState.value = _uiState.value.update()
    }
}

/**
 * UI State for music classification
 */
data class ClassificationUiState(
    val isLoading: Boolean = false,
    val isBatchLoading: Boolean = false,
    val isCheckingHealth: Boolean = false,
    val isGeneratingPlaylists: Boolean = false,
    val error: String? = null,
    val lastClassification: SongClassificationEntity? = null,
    val batchResult: ClassificationBatchResult? = null,
    val playlistGenerationResult: PlaylistGenerationResult? = null,
    val isCloudServiceHealthy: Boolean? = null,
    val cloudServiceError: String? = null
)
