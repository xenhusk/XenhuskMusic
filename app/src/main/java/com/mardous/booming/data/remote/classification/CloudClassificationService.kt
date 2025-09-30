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

package com.mardous.booming.data.remote.classification

import android.util.Log
import com.mardous.booming.data.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Service for communicating with the cloud music classification API
 */
class CloudClassificationService(
    private val client: HttpClient,
    private val baseUrl: String = "https://your-render-app.onrender.com" // Replace with actual Render URL
) {
    
    companion object {
        private const val TAG = "CloudClassificationService"
        private const val TIMEOUT_MS = 30000L // 30 seconds
    }
    
    /**
     * Check if the cloud service is healthy and model is loaded
     */
    suspend fun checkHealth(): Result<HealthCheckResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking cloud service health...")
            val response = client.get("$baseUrl/health") {
                contentType(ContentType.Application.Json)
            }.body<HealthCheckResponse>()
            
            Log.d(TAG, "Health check successful: ${response.status}, model loaded: ${response.model_loaded}")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed", e)
            Result.failure(IOException("Failed to check service health: ${e.message}", e))
        }
    }
    
    /**
     * Classify a single song's audio features
     */
    suspend fun classifySong(
        song: Song,
        audioData: FloatArray,
        sampleRate: Int = 22050
    ): Result<ClassificationResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Classifying song: ${song.title} by ${song.artistName}")
            
            val request = ClassificationRequest(
                audio_data = audioData.toList(),
                sample_rate = sampleRate,
                song_id = "${song.id}_${song.title}"
            )
            
            val response = client.post("$baseUrl/classify") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<ClassificationResponse>()
            
            if (response.success) {
                Log.d(TAG, "Classification successful: ${response.prediction} (confidence: ${response.confidence})")
                Result.success(response)
            } else {
                Log.w(TAG, "Classification failed: ${response.error}")
                Result.failure(IOException("Classification failed: ${response.error}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error classifying song: ${song.title}", e)
            Result.failure(IOException("Failed to classify song: ${e.message}", e))
        }
    }
    
    /**
     * Classify multiple songs in batch
     */
    suspend fun classifySongsBatch(
        songsWithAudioData: List<SongWithAudioData>
    ): Result<BatchClassificationResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Batch classifying ${songsWithAudioData.size} songs")
            
            val batchSongs = songsWithAudioData.map { songWithData ->
                BatchSongRequest(
                    song_id = "${songWithData.song.id}_${songWithData.song.title}",
                    audio_data = songWithData.audioData.toList(),
                    sample_rate = songWithData.sampleRate
                )
            }
            
            val request = BatchClassificationRequest(songs = batchSongs)
            
            val response = client.post("$baseUrl/batch_classify") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<BatchClassificationResponse>()
            
            Log.d(TAG, "Batch classification complete: ${response.summary.successful}/${response.summary.total} successful")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error in batch classification", e)
            Result.failure(IOException("Failed to classify songs in batch: ${e.message}", e))
        }
    }
    
    /**
     * Get information about the loaded model
     */
    suspend fun getModelInfo(): Result<ModelInfoResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting model information...")
            val response = client.get("$baseUrl/model_info") {
                contentType(ContentType.Application.Json)
            }.body<ModelInfoResponse>()
            
            Log.d(TAG, "Model info retrieved: ${response.model_type}, ${response.selected_features} features")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting model info", e)
            Result.failure(IOException("Failed to get model info: ${e.message}", e))
        }
    }
}

/**
 * Data class for songs with their audio data
 */
data class SongWithAudioData(
    val song: Song,
    val audioData: FloatArray,
    val sampleRate: Int = 22050
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as SongWithAudioData
        
        if (song != other.song) return false
        if (!audioData.contentEquals(other.audioData)) return false
        if (sampleRate != other.sampleRate) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = song.hashCode()
        result = 31 * result + audioData.contentHashCode()
        result = 31 * result + sampleRate
        return result
    }
}
