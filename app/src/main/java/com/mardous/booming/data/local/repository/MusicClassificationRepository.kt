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

package com.mardous.booming.data.local.repository

import android.content.Context
import android.util.Log
import com.mardous.booming.data.local.audio.AndroidAudioFeatureExtractor
import com.mardous.booming.data.local.audio.LocalAudioFeatureExtractor
import com.mardous.booming.data.local.audio.SimpleAudioDataExtractor
import com.mardous.booming.data.local.audio.RealAudioDataExtractor
import com.mardous.booming.data.local.room.SongClassificationDao
import com.mardous.booming.data.local.room.SongClassificationEntity
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.remote.classification.CloudClassificationService
import com.mardous.booming.data.remote.classification.SongWithAudioData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import java.io.IOException

/**
 * Repository for managing music classifications
 */
class MusicClassificationRepository(
    private val context: Context,
    private val classificationDao: SongClassificationDao,
    private val cloudService: CloudClassificationService,
    private val audioFeatureExtractor: AndroidAudioFeatureExtractor,
    private val localAudioFeatureExtractor: LocalAudioFeatureExtractor,
    private val simpleAudioExtractor: SimpleAudioDataExtractor,
    private val realAudioDataExtractor: RealAudioDataExtractor,
    private val playlistGenerationService: PlaylistGenerationService
) {
    
    companion object {
        private const val TAG = "MusicClassificationRepository"
        private const val BATCH_SIZE = 10 // Process songs in batches
    }
    
    /**
     * Get classification for a specific song
     */
    suspend fun getClassification(songId: Long): SongClassificationEntity? = withContext(Dispatchers.IO) {
        try {
            classificationDao.getClassificationBySongId(songId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting classification for song $songId", e)
            null
        }
    }
    
    /**
     * Get classification for a specific song as Flow
     */
    fun getClassificationFlow(songId: Long): Flow<SongClassificationEntity?> {
        return classificationDao.getClassificationBySongIdFlow(songId)
    }
    
    /**
     * Get all classifications by type
     */
    suspend fun getClassificationsByType(type: String): List<SongClassificationEntity> = withContext(Dispatchers.IO) {
        try {
            classificationDao.getClassificationsByType(type)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting classifications by type $type", e)
            emptyList()
        }
    }
    
    /**
     * Get all classifications by type as Flow
     */
    fun getClassificationsByTypeFlow(type: String): Flow<List<SongClassificationEntity>> {
        return classificationDao.getClassificationsByTypeFlow(type)
    }
    
    /**
     * Get all classifications
     */
    suspend fun getAllClassifications(): List<SongClassificationEntity> = withContext(Dispatchers.IO) {
        try {
            classificationDao.getAllClassifications()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all classifications", e)
            emptyList()
        }
    }
    
    /**
     * Get all classifications as Flow
     */
    fun getAllClassificationsFlow(): Flow<List<SongClassificationEntity>> {
        return classificationDao.getAllClassificationsFlow()
    }
    
    /**
     * Get classification statistics
     */
    suspend fun getClassificationStats(): ClassificationStats = withContext(Dispatchers.IO) {
        try {
            val christianCount = classificationDao.getClassificationCount("Christian")
            val secularCount = classificationDao.getClassificationCount("Secular")
            val totalCount = classificationDao.getTotalClassificationCount()
            
            ClassificationStats(
                christianCount = christianCount,
                secularCount = secularCount,
                totalCount = totalCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting classification stats", e)
            ClassificationStats(0, 0, 0)
        }
    }
    
    /**
     * Fast classification using local feature extraction
     */
    suspend fun classifySongFast(song: Song): Result<SongClassificationEntity> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fast classifying song: ${song.title}")
            
            // Extract features locally (the 65 features the model expects)
            val featuresResult = localAudioFeatureExtractor.extractFeatures(song)
            if (featuresResult.isFailure) {
                return@withContext Result.failure(featuresResult.exceptionOrNull() ?: IOException("Feature extraction failed"))
            }
            
            val features: FloatArray = featuresResult.getOrThrow()
            
            // Send features to cloud service for classification
            val classificationResult = cloudService.classifySongWithFeatures(song, features)
            if (classificationResult.isFailure) {
                return@withContext Result.failure(classificationResult.exceptionOrNull() ?: IOException("Classification failed"))
            }
            
            val response = classificationResult.getOrThrow()
            
            // Create entity
            val entity = SongClassificationEntity(
                songId = song.id,
                songTitle = song.title,
                artistName = song.artistName,
                classificationType = response.prediction,
                confidence = response.confidence,
                christianProbability = response.probabilities.christian,
                secularProbability = response.probabilities.secular,
                classificationTimestamp = System.currentTimeMillis()
            )
            
            // Save to database
            classificationDao.insertClassification(entity)
            
            Log.d(TAG, "Fast classification successful: ${song.title} -> ${response.prediction}")
            Result.success(entity)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in fast classification: ${song.title}", e)
            Result.failure(IOException("Fast classification failed: ${e.message}", e))
        }
    }
    
    /**
     * Real classification using actual audio data with server-side feature extraction
     * This is the most accurate method as it uses real audio data and proper feature extraction
     */
    suspend fun classifySongReal(song: Song): Result<SongClassificationEntity> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "File upload classifying song: ${song.title}")
            
            // Upload audio file directly to server for classification
            val classificationResult = cloudService.classifySongWithFile(song)
            if (classificationResult.isFailure) {
                Log.w(TAG, "File upload classification failed for ${song.title}, falling back to synthetic data")
                return@withContext classifySongFast(song) // Fallback to fast classification
            }
            
            val response = classificationResult.getOrThrow()
            
            // Create entity
            val entity = SongClassificationEntity(
                songId = song.id,
                songTitle = song.title,
                artistName = song.artistName,
                classificationType = response.prediction,
                confidence = response.confidence,
                christianProbability = response.probabilities.christian,
                secularProbability = response.probabilities.secular,
                classificationTimestamp = System.currentTimeMillis()
            )
            
            // Save to database
            classificationDao.insertClassification(entity)
            
            Log.d(TAG, "File upload classification successful: ${song.title} -> ${response.prediction}")
            Result.success(entity)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in file upload classification: ${song.title}", e)
            Result.failure(IOException("File upload classification failed: ${e.message}", e))
        }
    }
    
    /**
     * Classify multiple songs in parallel for faster processing
     */
    suspend fun classifySongsParallel(songs: List<Song>, maxConcurrency: Int = 8, onProgress: ((Int, Int) -> Unit)? = null): ClassificationBatchResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting parallel classification of ${songs.size} songs with max concurrency: $maxConcurrency")
            
            val successfulClassifications = mutableListOf<SongClassificationEntity>()
            val failedSongs = mutableListOf<Song>()
            var processedCount = 0
            
            // Filter out songs that are already classified
            val unclassifiedSongs = songs.filter { song ->
                val existingClassification = classificationDao.getClassificationBySongId(song.id)
                if (existingClassification != null) {
                    Log.d(TAG, "Skipping already classified song: ${song.title} (${existingClassification.classificationType})")
                    processedCount++
                    onProgress?.invoke(processedCount, songs.size)
                }
                existingClassification == null
            }
            
            Log.d(TAG, "Skipped ${songs.size - unclassifiedSongs.size} already classified songs. Processing ${unclassifiedSongs.size} songs.")
            
            if (unclassifiedSongs.isEmpty()) {
                Log.d(TAG, "All songs are already classified")
                return@withContext ClassificationBatchResult(
                    successfulClassifications = successfulClassifications,
                    failedSongs = failedSongs,
                    totalProcessed = songs.size,
                    skippedCount = songs.size
                )
            }
            
            // Use Semaphore to limit concurrent uploads
            val semaphore = Semaphore(maxConcurrency)
            
            // Create coroutines for each unclassified song
            val jobs = unclassifiedSongs.map { song ->
                async {
                    try {
                        semaphore.acquire()
                        Log.d(TAG, "Processing unclassified song: ${song.title}")
                        
                        val result = classifySongReal(song)
                        if (result.isSuccess) {
                            successfulClassifications.add(result.getOrThrow())
                        } else {
                            failedSongs.add(song)
                        }
                        
                        // Update progress
                        processedCount++
                        onProgress?.invoke(processedCount, songs.size)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing song: ${song.title}", e)
                        failedSongs.add(song)
                        
                        // Update progress even on error
                        processedCount++
                        onProgress?.invoke(processedCount, songs.size)
                    } finally {
                        semaphore.release()
                    }
                }
            }
            
            // Wait for all jobs to complete
            jobs.awaitAll()
            
            Log.d(TAG, "Parallel classification complete: ${successfulClassifications.size} successful, ${failedSongs.size} failed")
            
            ClassificationBatchResult(
                successfulClassifications = successfulClassifications,
                failedSongs = failedSongs,
                totalProcessed = songs.size,
                skippedCount = songs.size - unclassifiedSongs.size,
                error = if (successfulClassifications.isEmpty()) "All classifications failed" else null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in parallel classification", e)
            ClassificationBatchResult(
                successfulClassifications = emptyList(),
                failedSongs = songs,
                totalProcessed = songs.size,
                skippedCount = 0,
                error = e.message
            )
        }
    }
    
    /**
     * Classify a single song (legacy method)
     */
    suspend fun classifySong(song: Song): Result<SongClassificationEntity> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Classifying song: ${song.title}")
            
            // Extract audio features
            val featuresResult = audioFeatureExtractor.extractFeatures(song)
            if (featuresResult.isFailure) {
                return@withContext Result.failure(featuresResult.exceptionOrNull() ?: IOException("Feature extraction failed"))
            }
            
            val audioData = featuresResult.getOrThrow()
            
            // Send to cloud service
            val classificationResult = cloudService.classifySong(song, audioData)
            if (classificationResult.isFailure) {
                return@withContext Result.failure(classificationResult.exceptionOrNull() ?: IOException("Classification failed"))
            }
            
            val response = classificationResult.getOrThrow()
            
            // Create entity
            val entity = SongClassificationEntity(
                songId = song.id,
                songTitle = song.title,
                artistName = song.artistName,
                classificationType = response.prediction,
                confidence = response.confidence,
                christianProbability = response.probabilities.christian,
                secularProbability = response.probabilities.secular,
                classificationTimestamp = System.currentTimeMillis()
            )
            
            // Save to database
            classificationDao.insertClassification(entity)
            
            // Update playlists with new classification
            try {
                playlistGenerationService.updatePlaylistsWithNewClassifications()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update playlists after classification", e)
            }
            
            Log.d(TAG, "Successfully classified song: ${song.title} as ${response.prediction}")
            Result.success(entity)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error classifying song: ${song.title}", e)
            Result.failure(IOException("Failed to classify song: ${e.message}", e))
        }
    }
    
    /**
     * Classify multiple songs in batch
     */
    suspend fun classifySongsBatch(songs: List<Song>): ClassificationBatchResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Batch classifying ${songs.size} songs")
            
            val successfulClassifications = mutableListOf<SongClassificationEntity>()
            val failedSongs = mutableListOf<Song>()
            
            // Process songs in batches
            songs.chunked(BATCH_SIZE).forEach { batch ->
                val songsWithAudioData = mutableListOf<SongWithAudioData>()
                
                // Extract features for batch
                batch.forEach { song ->
                    try {
                        val featuresResult = audioFeatureExtractor.extractFeatures(song)
                        if (featuresResult.isSuccess) {
                            songsWithAudioData.add(
                                SongWithAudioData(
                                    song = song,
                                    audioData = featuresResult.getOrThrow()
                                )
                            )
                        } else {
                            failedSongs.add(song)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extracting features for: ${song.title}", e)
                        failedSongs.add(song)
                    }
                }
                
                // Send batch to cloud service
                if (songsWithAudioData.isNotEmpty()) {
                    val batchResult = cloudService.classifySongsBatch(songsWithAudioData)
                    if (batchResult.isSuccess) {
                        val response = batchResult.getOrThrow()
                        
                        // Process results
                        response.results.forEach { result ->
                            if (result.success) {
                                val song = songsWithAudioData.find { it.song.title == result.song_id?.substringAfter("_") }?.song
                                if (song != null) {
                                    val entity = SongClassificationEntity(
                                        songId = song.id,
                                        songTitle = song.title,
                                        artistName = song.artistName,
                                        classificationType = result.prediction,
                                        confidence = result.confidence,
                                        christianProbability = result.probabilities.christian,
                                        secularProbability = result.probabilities.secular,
                                        classificationTimestamp = System.currentTimeMillis()
                                    )
                                    successfulClassifications.add(entity)
                                }
                            } else {
                                // Find the song that failed
                                val song = songsWithAudioData.find { it.song.title == result.song_id?.substringAfter("_") }?.song
                                if (song != null) {
                                    failedSongs.add(song)
                                }
                            }
                        }
                    } else {
                        // Entire batch failed
                        songsWithAudioData.forEach { failedSongs.add(it.song) }
                    }
                }
            }
            
            // Save successful classifications to database
            if (successfulClassifications.isNotEmpty()) {
                classificationDao.insertClassifications(successfulClassifications)
                
                // Update playlists with new classifications
                try {
                    playlistGenerationService.updatePlaylistsWithNewClassifications()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update playlists after batch classification", e)
                }
            }
            
            Log.d(TAG, "Batch classification complete: ${successfulClassifications.size} successful, ${failedSongs.size} failed")
            
            ClassificationBatchResult(
                successfulClassifications = successfulClassifications,
                failedSongs = failedSongs,
                totalProcessed = songs.size,
                skippedCount = 0
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in batch classification", e)
            ClassificationBatchResult(
                successfulClassifications = emptyList(),
                failedSongs = songs,
                totalProcessed = songs.size,
                skippedCount = 0,
                error = e.message
            )
        }
    }
    
    /**
     * Update classification manually (user override)
     */
    suspend fun updateClassificationManually(
        songId: Long,
        newClassification: String,
        confidence: Float = 1.0f
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val existing = classificationDao.getClassificationBySongId(songId)
            if (existing != null) {
                val updated = existing.copy(
                    classificationType = newClassification,
                    confidence = confidence,
                    isManualOverride = true,
                    manualOverrideTimestamp = System.currentTimeMillis()
                )
                classificationDao.updateClassification(updated)
                Log.d(TAG, "Updated classification for song $songId to $newClassification")
                Result.success(Unit)
            } else {
                Result.failure(IOException("No existing classification found for song $songId"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating classification manually", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete classification for a song
     */
    suspend fun deleteClassification(songId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            classificationDao.deleteClassificationBySongId(songId)
            Log.d(TAG, "Deleted classification for song $songId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting classification", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if cloud service is available
     */
    suspend fun checkCloudServiceHealth(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val healthResult = cloudService.checkHealth()
            if (healthResult.isSuccess) {
                val health = healthResult.getOrThrow()
                Result.success(health.status == "healthy" && health.model_loaded)
            } else {
                Result.failure(healthResult.exceptionOrNull() ?: IOException("Health check failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking cloud service health", e)
            Result.failure(e)
        }
    }
    
    /**
     * Clear all classifications and statistics
     */
    suspend fun clearAllClassifications(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            classificationDao.deleteAllClassifications()
            Log.d(TAG, "Cleared all classifications")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all classifications", e)
            Result.failure(e)
        }
    }
}

/**
 * Data classes for classification results
 */
data class ClassificationStats(
    val christianCount: Int,
    val secularCount: Int,
    val totalCount: Int
)

data class ClassificationBatchResult(
    val successfulClassifications: List<SongClassificationEntity>,
    val failedSongs: List<Song>,
    val totalProcessed: Int,
    val skippedCount: Int = 0,
    val error: String? = null
) {
    val successCount: Int get() = successfulClassifications.size
    val failureCount: Int get() = failedSongs.size
    val successRate: Float get() = if (totalProcessed > 0) successCount.toFloat() / totalProcessed else 0f
}
