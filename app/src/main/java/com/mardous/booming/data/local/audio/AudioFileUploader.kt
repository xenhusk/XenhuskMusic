package com.mardous.booming.data.local.audio

import android.content.Context
import android.util.Log
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.remote.classification.ClassificationResponse
import com.mardous.booming.data.remote.classification.ClassProbabilities
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.streams.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Uploads audio files to the server for classification
 * This is the most reliable approach as it uses actual audio files
 */
class AudioFileUploader(
    private val context: Context,
    private val client: HttpClient,
    private val baseUrl: String
) {

    companion object {
        private const val TAG = "AudioFileUploader"
    }

    suspend fun uploadAndClassifyFile(song: Song): Result<ClassificationResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Uploading audio file for classification: ${song.title}")

            // Check if the audio file exists
            val audioFile = File(song.data)
            Log.d(TAG, "Checking file: ${audioFile.absolutePath}")
            
            if (!audioFile.exists()) {
                Log.w(TAG, "Audio file does not exist: ${song.data}")
                return@withContext Result.failure(IOException("Audio file not found: ${song.data}"))
            }

            if (!audioFile.canRead()) {
                Log.w(TAG, "Cannot read audio file: ${song.data}")
                return@withContext Result.failure(IOException("Cannot read audio file: ${song.data}"))
            }

            val fileSize = audioFile.length()
            Log.d(TAG, "Audio file found: ${audioFile.absolutePath} (${fileSize} bytes)")
            
            if (fileSize == 0L) {
                Log.w(TAG, "Audio file is empty: ${song.data}")
                return@withContext Result.failure(IOException("Audio file is empty: ${song.data}"))
            }

            // Read file bytes
            val fileBytes = audioFile.readBytes()
            Log.d(TAG, "Read ${fileBytes.size} bytes from file")

            // Upload the file to the server using raw binary data
            val response = client.post("$baseUrl/classify_audio_file") {
                setBody(fileBytes)
                headers {
                    append(HttpHeaders.ContentType, "audio/ogg")
                    append("X-Song-ID", "${song.id}_${song.title}")
                    append("X-File-Name", audioFile.name)
                }
            }

            val responseText = response.bodyAsText()
            Log.d(TAG, "Server response (${response.status}): $responseText")

            if (response.status.isSuccess()) {
                val classificationResponse = parseClassificationResponse(responseText)
                if (classificationResponse.success) {
                    Log.d(TAG, "Classification successful: ${classificationResponse.prediction} (confidence: ${classificationResponse.confidence})")
                    Result.success(classificationResponse)
                } else {
                    Log.w(TAG, "Classification failed: ${classificationResponse.error}")
                    Result.failure(IOException("Classification failed: ${classificationResponse.error}"))
                }
            } else {
                Log.e(TAG, "Server error: ${response.status} - $responseText")
                Result.failure(IOException("Server error: ${response.status} - $responseText"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file for classification: ${song.title}", e)
            Result.failure(IOException("Failed to upload and classify file: ${e.message}", e))
        }
    }

    private fun parseClassificationResponse(responseText: String): ClassificationResponse {
        return try {
            // Simple JSON parsing - in a real app you'd use a proper JSON library
            val json = responseText.trim()
            if (json.startsWith("{") && json.endsWith("}")) {
                // Extract basic fields
                val success = json.contains("\"success\":true")
                val prediction = extractJsonField(json, "prediction") ?: "unknown"
                val confidence = extractJsonField(json, "confidence")?.toDoubleOrNull() ?: 0.0
                val christianProb = extractJsonField(json, "christian")?.toDoubleOrNull() ?: 0.0
                val secularProb = extractJsonField(json, "secular")?.toDoubleOrNull() ?: 0.0
                val error = extractJsonField(json, "error")

                ClassificationResponse(
                    success = success,
                    prediction = prediction,
                    confidence = confidence.toFloat(),
                    probabilities = ClassProbabilities(
                        christian = christianProb.toFloat(),
                        secular = secularProb.toFloat()
                    ),
                    error = error
                )
            } else {
                ClassificationResponse(
                    success = false,
                    prediction = "unknown",
                    confidence = 0.0f,
                    probabilities = ClassProbabilities(0.0f, 0.0f),
                    error = "Invalid JSON response"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing classification response", e)
                ClassificationResponse(
                    success = false,
                    prediction = "unknown",
                    confidence = 0.0f,
                    probabilities = ClassProbabilities(0.0f, 0.0f),
                    error = "Failed to parse response: ${e.message}"
                )
        }
    }

    private fun extractJsonField(json: String, fieldName: String): String? {
        return try {
            val pattern = "\"$fieldName\"\\s*:\\s*\"?([^,\"}\\]]+)\"?".toRegex()
            val match = pattern.find(json)
            match?.groupValues?.get(1)?.trim('"', ' ')
        } catch (e: Exception) {
            null
        }
    }
}
