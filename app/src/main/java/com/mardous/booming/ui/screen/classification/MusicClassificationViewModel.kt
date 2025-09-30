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

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.data.local.repository.MusicClassificationRepository
import com.mardous.booming.data.local.repository.ClassificationStats
import com.mardous.booming.data.local.repository.ClassificationBatchResult
import com.mardous.booming.data.local.repository.PlaylistGenerationService
import com.mardous.booming.data.local.repository.PlaylistGenerationResult
import com.mardous.booming.data.local.repository.SongRepository
import com.mardous.booming.data.local.room.SongClassificationEntity
import com.mardous.booming.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * ViewModel for music classification management
 */
class MusicClassificationViewModel(
    private val classificationRepository: MusicClassificationRepository,
    private val playlistGenerationService: PlaylistGenerationService,
    private val songRepository: SongRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "MusicClassificationViewModel"
    }
    
    private var currentClassificationJob: Job? = null
    private val classificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
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
    fun loadAllClassifications() {
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
     * Classify all songs using parallel processing for faster results
     */
    fun classifyAllSongsParallel() {
        currentClassificationJob?.cancel()
        currentClassificationJob = classificationScope.launch {
            updateUiState { 
                copy(
                    isBatchLoading = true, 
                    error = null,
                    progressMessage = "Loading songs from library...",
                    currentProgress = 0,
                    totalProgress = 0
                ) 
            }
            
            try {
                // Get all songs from the repository on IO thread
                val allSongs = withContext(Dispatchers.IO) {
                    songRepository.songs()
                }
                
                if (allSongs.isEmpty()) {
                    updateUiState { 
                        copy(
                            isBatchLoading = false, 
                            error = "No songs found in library",
                            progressMessage = null
                        ) 
                    }
                    return@launch
                }
                
                updateUiState { 
                    copy(
                        progressMessage = "Classifying ${allSongs.size} songs in parallel (up to 8 concurrent)...",
                        totalProgress = allSongs.size,
                        currentProgress = 0
                    ) 
                }
                
                // Use parallel classification with progress updates
                val result = classificationRepository.classifySongsParallel(
                    songs = allSongs, 
                    maxConcurrency = 8,
                    onProgress = { current, total ->
                        updateUiState { 
                            copy(
                                progressMessage = "Classifying all songs: ${current} of ${total} songs completed...",
                                currentProgress = current
                            ) 
                        }
                    }
                )
                
                // Final update
                updateUiState { 
                    copy(
                        isBatchLoading = false,
                        batchResult = result,
                        progressMessage = "Classification complete! Processed ${result.totalProcessed} songs with ${result.successCount} successful classifications.",
                        currentProgress = result.totalProcessed,
                        error = result.error
                    ) 
                }
                
                // Refresh data
                loadStats()
                loadAllClassifications()
                
                Log.d(TAG, "Parallel classification complete: ${result.successCount}/${result.totalProcessed} successful")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in parallel classification", e)
                updateUiState { 
                    copy(
                        isBatchLoading = false, 
                        error = "Classification failed: ${e.message}",
                        progressMessage = null
                    ) 
                }
            }
        }
    }
    
    /**
     * Classify a random sample of songs for testing
     */
    fun classifyRandomSongs(sampleSize: Int = 30) {
        currentClassificationJob?.cancel()
        currentClassificationJob = classificationScope.launch {
            updateUiState { 
                copy(
                    isBatchLoading = true, 
                    error = null,
                    progressMessage = "Loading songs from library...",
                    currentProgress = 0,
                    totalProgress = sampleSize
                ) 
            }
            
            try {
                // Get all songs from the repository on IO thread
                val allSongs = withContext(Dispatchers.IO) {
                    songRepository.songs()
                }
                
                if (allSongs.isEmpty()) {
                    updateUiState { 
                        copy(
                            isBatchLoading = false, 
                            error = "No songs found in library",
                            progressMessage = null
                        ) 
                    }
                    return@launch
                }
                
                // Select random songs
                val randomSongs = allSongs.shuffled().take(sampleSize)
                
                updateUiState { 
                    copy(
                        progressMessage = "Testing classification on ${randomSongs.size} random songs...",
                        totalProgress = randomSongs.size,
                        currentProgress = 0
                    ) 
                }
                
                // Use parallel processing with progress updates
                updateUiState { 
                    copy(
                        progressMessage = "Testing classification on ${randomSongs.size} random songs using parallel processing...",
                        currentProgress = 0
                    ) 
                }
                
                // Use parallel processing with progress callback
                val result = classificationRepository.classifySongsParallel(
                    songs = randomSongs, 
                    maxConcurrency = 8,
                    onProgress = { current, total ->
                        updateUiState { 
                            copy(
                                progressMessage = "Testing classification: ${current} of ${total} songs completed...",
                                currentProgress = current
                            ) 
                        }
                    }
                )
                
                updateUiState { 
                    copy(
                        progressMessage = "Test classification complete! Processed ${result.totalProcessed} songs with ${result.successCount} successful classifications.",
                        currentProgress = result.totalProcessed
                    ) 
                }
                
                // Final update
                updateUiState { 
                    copy(
                        isBatchLoading = false,
                        batchResult = result
                    ) 
                }
                
                // Refresh data
                loadStats()
                loadAllClassifications()
                
                Log.d(TAG, "Random classification test complete: ${result.successCount}/${result.totalProcessed} successful")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in random classification test", e)
                updateUiState { 
                    copy(
                        isBatchLoading = false, 
                        error = "Test classification failed: ${e.message}",
                        progressMessage = null
                    ) 
                }
            }
        }
    }
    
    /**
     * Classify all songs in the library
     */
    fun classifyAllSongs() {
        viewModelScope.launch {
            updateUiState { 
                copy(
                    isBatchLoading = true, 
                    error = null,
                    progressMessage = "Loading songs from library...",
                    currentProgress = 0,
                    totalProgress = 0
                ) 
            }
            
            try {
                // Get all songs from the repository on IO thread
                val allSongs = withContext(Dispatchers.IO) {
                    songRepository.songs()
                }
                
                if (allSongs.isEmpty()) {
                    updateUiState { 
                        copy(
                            isBatchLoading = false, 
                            error = "No songs found in library",
                            progressMessage = null
                        ) 
                    }
                    return@launch
                }
                
                updateUiState { 
                    copy(
                        progressMessage = "Found ${allSongs.size} songs. Starting classification...",
                        totalProgress = allSongs.size,
                        currentProgress = 0
                    ) 
                }
                
                // Classify songs in smaller batches with progress updates
                val batchSize = 10 // Process 10 songs at a time for better progress feedback
                var processedCount = 0
                val allResults = mutableListOf<SongClassificationEntity>()
                
                for (i in allSongs.indices step batchSize) {
                    val batchEnd = minOf(i + batchSize, allSongs.size)
                    val batch = allSongs.subList(i, batchEnd)
                    
                    updateUiState { 
                        copy(
                            progressMessage = "Classifying songs ${processedCount + 1} to ${processedCount + batch.size} of ${allSongs.size}...",
                            currentProgress = processedCount
                        ) 
                    }
                    
                    // Classify this batch using real audio data method
                    val batchResults = mutableListOf<SongClassificationEntity>()
                    val failedSongs = mutableListOf<Song>()
                    
                    for (song in batch) {
                        val result = classificationRepository.classifySongReal(song)
                        if (result.isSuccess) {
                            batchResults.add(result.getOrThrow())
                        } else {
                            failedSongs.add(song)
                        }
                    }
                    
                    val batchResult = ClassificationBatchResult(
                        successfulClassifications = batchResults,
                        failedSongs = failedSongs,
                        totalProcessed = batch.size,
                        error = null
                    )
                    if (batchResult.successCount > 0) {
                        allResults.addAll(batchResult.successfulClassifications ?: emptyList())
                    }
                    
                    processedCount += batch.size
                    
                    updateUiState { 
                        copy(
                            currentProgress = processedCount,
                            progressMessage = "Processed $processedCount of ${allSongs.size} songs..."
                        ) 
                    }
                    
                    // Small delay to allow UI updates
                    kotlinx.coroutines.delay(100)
                }
                
                updateUiState { 
                    copy(
                        isBatchLoading = false,
                        progressMessage = "Classification complete!",
                        currentProgress = allSongs.size,
                        batchResult = ClassificationBatchResult(
                            successfulClassifications = allResults,
                            failedSongs = allSongs.filter { song -> 
                                allResults.none { it.songId == song.id } 
                            },
                            totalProcessed = allSongs.size,
                            error = null
                        ),
                        error = null
                    ) 
                }
                
                loadStats() // Refresh stats
                loadAllClassifications() // Refresh classifications
                
                // Clear progress message after a delay
                kotlinx.coroutines.delay(2000)
                updateUiState { copy(progressMessage = null) }
                
            } catch (e: Exception) {
                updateUiState { 
                    copy(
                        isBatchLoading = false, 
                        error = e.message,
                        progressMessage = null
                    ) 
                }
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
     * Clear all classifications and statistics
     */
    fun clearAllClassifications() {
        viewModelScope.launch {
            try {
                val result = classificationRepository.clearAllClassifications()
                if (result.isSuccess) {
                    loadStats() // Refresh stats
                    loadAllClassifications() // Refresh classifications
                    // Success will be indicated by the UI state update
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Clear failed"
                    updateUiState { copy(error = error) }
                }
            } catch (e: Exception) {
                updateUiState { copy(error = e.message) }
            }
        }
    }
    
    /**
     * Cancel the current classification process
     */
    fun cancelClassification() {
        currentClassificationJob?.cancel()
        currentClassificationJob = null
        
        updateUiState { 
            copy(
                isBatchLoading = false,
                progressMessage = "Classification cancelled",
                currentProgress = 0,
                totalProgress = 0
            ) 
        }
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
    
    override fun onCleared() {
        super.onCleared()
        currentClassificationJob?.cancel()
        classificationScope.cancel()
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
    val cloudServiceError: String? = null,
    val progressMessage: String? = null,
    val currentProgress: Int = 0,
    val totalProgress: Int = 0
)
