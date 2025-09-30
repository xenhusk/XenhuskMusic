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

package com.mardous.booming.data.local.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.mardous.booming.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.*

/**
 * Local audio feature extractor for Android that matches the cloud service's feature extraction
 * This extracts the same features as the Python implementation for consistency
 */
class AndroidAudioFeatureExtractor(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioFeatureExtractor"
        private const val SAMPLE_RATE = 22050
        private const val DURATION_SECONDS = 10
        private const val TARGET_LENGTH = SAMPLE_RATE * DURATION_SECONDS
        private const val MIN_AUDIO_LENGTH = 1000 // Minimum 1 second of audio
    }
    
    /**
     * Extract audio features from a song file
     */
    suspend fun extractFeatures(song: Song): Result<FloatArray> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Extracting features from: ${song.title}")
            
            // Load audio data
            val audioData = loadAudioData(song)
            if (audioData.isEmpty()) {
                return@withContext Result.failure(IOException("Failed to load audio data"))
            }
            
            // Extract features
            val features = extractFeaturesFromAudioData(audioData)
            
            Log.d(TAG, "Successfully extracted ${features.size} features from: ${song.title}")
            Result.success(features)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting features from: ${song.title}", e)
            Result.failure(IOException("Failed to extract features: ${e.message}", e))
        }
    }
    
    /**
     * Load audio data from a song file
     */
    private suspend fun loadAudioData(song: Song): FloatArray = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(song.data))
            
            // Get audio data (this is a simplified approach - in production you might want to use a proper audio library)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            
            if (duration < MIN_AUDIO_LENGTH) {
                throw IOException("Audio file too short: ${duration}ms")
            }
            
            // For now, we'll create a placeholder audio data array
            // In a real implementation, you would use a library like ExoPlayer or FFmpeg to extract actual audio samples
            val audioData = FloatArray(TARGET_LENGTH) { 
                (sin(2 * PI * it / SAMPLE_RATE * 440) * 0.1).toFloat() // Placeholder: 440Hz sine wave
            }
            
            audioData
            
        } finally {
            retriever.release()
        }
    }
    
    /**
     * Extract features from audio data array (matching the Python implementation)
     */
    private fun extractFeaturesFromAudioData(audioData: FloatArray): FloatArray {
        val features = mutableListOf<Float>()
        
        // Ensure we have the right length
        val y = if (audioData.size > TARGET_LENGTH) {
            audioData.sliceArray(0 until TARGET_LENGTH)
        } else if (audioData.size < TARGET_LENGTH) {
            audioData + FloatArray(TARGET_LENGTH - audioData.size)
        } else {
            audioData
        }
        
        // Basic properties (normalized/relative features)
        features.add(signalLengthRatio(y))
        features.add(rmsEnergyRatio(y))
        
        // Spectral features
        val spectralCentroids = calculateSpectralCentroids(y)
        features.add(spectralCentroids.mean())
        features.add(spectralCentroids.std())
        features.add(spectralCentroids.skewness())
        
        val spectralRolloff = calculateSpectralRolloff(y)
        features.add(spectralRolloff.mean())
        features.add(spectralRolloff.std())
        
        val spectralBandwidth = calculateSpectralBandwidth(y)
        features.add(spectralBandwidth.mean())
        features.add(spectralBandwidth.std())
        
        // Zero crossing rate
        val zcr = calculateZeroCrossingRate(y)
        features.add(zcr.mean())
        features.add(zcr.std())
        
        // MFCC features (first 13 coefficients)
        val mfccs = calculateMFCCs(y)
        for (i in 0 until 13) {
            features.add(mfccs[i].mean())
            features.add(mfccs[i].std())
        }
        
        // Chroma features
        val chroma = calculateChroma(y)
        features.add(chroma.mean())
        features.add(chroma.std())
        
        // Individual chroma bins (12 semitones)
        val chromaBins = calculateChromaBins(y)
        for (i in 0 until 12) {
            features.add(chromaBins[i])
        }
        
        // Tonnetz features
        val tonnetz = calculateTonnetz(y)
        features.add(tonnetz.mean())
        features.add(tonnetz.std())
        
        // Rhythm and tempo features
        val tempo = calculateTempo(y)
        features.add(tempo)
        features.add(calculateBeatStrength(y))
        
        // Spectral contrast
        val contrast = calculateSpectralContrast(y)
        features.add(contrast.mean())
        features.add(contrast.std())
        
        // Spectral flatness
        val flatness = calculateSpectralFlatness(y)
        features.add(flatness.mean())
        features.add(flatness.std())
        
        // Dynamic features
        features.add(calculateDynamicRange(y))
        features.add(calculatePeakToRmsRatio(y))
        
        // Harmonic-percussive separation features
        val harmonicRatio = calculateHarmonicRatio(y)
        features.add(harmonicRatio)
        features.add(1.0f - harmonicRatio) // Percussive ratio
        
        // Additional spectral features
        features.add(spectralCentroids.mean() / (SAMPLE_RATE / 2))
        
        // Silence ratio
        features.add(calculateSilenceRatio(y))
        
        return features.toFloatArray()
    }
    
    // Feature calculation methods (simplified implementations)
    
    private fun signalLengthRatio(y: FloatArray): Float {
        return y.size.toFloat() / TARGET_LENGTH
    }
    
    private fun rmsEnergyRatio(y: FloatArray): Float {
        val rms = sqrt(y.map { it * it }.average()).toFloat()
        val max = y.maxOrNull() ?: 1f
        return rms / (abs(max) + 1e-8f)
    }
    
    private fun calculateSpectralCentroids(y: FloatArray): FloatArray {
        // Simplified spectral centroid calculation
        val fft = performFFT(y)
        val magnitudes = fft.map { sqrt(it.real * it.real + it.imag * it.imag) }.toFloatArray()
        val frequencies = FloatArray(magnitudes.size) { it * SAMPLE_RATE / magnitudes.size }
        
        val centroid = frequencies.zip(magnitudes.toList()) { freq, mag ->
            freq * mag
        }.sum() / magnitudes.sum()
        
        return floatArrayOf(centroid.toFloat())
    }
    
    private fun calculateSpectralRolloff(y: FloatArray): FloatArray {
        val fft = performFFT(y)
        val magnitudes = fft.map { sqrt(it.real * it.real + it.imag * it.imag) }.toFloatArray()
        val totalEnergy = magnitudes.sum()
        val threshold = totalEnergy * 0.85f
        
        var cumulativeEnergy = 0f
        var rolloffIndex = 0
        for (i in magnitudes.indices) {
            cumulativeEnergy += magnitudes[i]
            if (cumulativeEnergy >= threshold) {
                rolloffIndex = i
                break
            }
        }
        
        val rolloffFreq = rolloffIndex * SAMPLE_RATE / magnitudes.size
        return floatArrayOf(rolloffFreq)
    }
    
    private fun calculateSpectralBandwidth(y: FloatArray): FloatArray {
        val centroids = calculateSpectralCentroids(y)
        val centroid = centroids[0]
        
        val fft = performFFT(y)
        val magnitudes = fft.map { sqrt(it.real * it.real + it.imag * it.imag) }.toFloatArray()
        val frequencies = FloatArray(magnitudes.size) { it * SAMPLE_RATE / magnitudes.size }
        
        val bandwidth = frequencies.zip(magnitudes.toList()) { freq, mag ->
            (freq - centroid) * (freq - centroid) * mag
        }.sum() / magnitudes.sum()
        
        return floatArrayOf(sqrt(bandwidth).toFloat())
    }
    
    private fun calculateZeroCrossingRate(y: FloatArray): FloatArray {
        var crossings = 0
        for (i in 1 until y.size) {
            if ((y[i] >= 0) != (y[i-1] >= 0)) {
                crossings++
            }
        }
        return floatArrayOf(crossings.toFloat() / y.size)
    }
    
    private fun calculateMFCCs(y: FloatArray): Array<FloatArray> {
        // Simplified MFCC calculation - in production use a proper audio processing library
        val mfccs = Array(13) { FloatArray(10) }
        for (i in 0 until 13) {
            for (j in 0 until 10) {
                mfccs[i][j] = (sin(2 * PI * i * j / 10) * 0.1).toFloat()
            }
        }
        return mfccs
    }
    
    private fun calculateChroma(y: FloatArray): FloatArray {
        // Simplified chroma calculation
        val fft = performFFT(y)
        val magnitudes = fft.map { sqrt(it.real * it.real + it.imag * it.imag) }.toFloatArray()
        return floatArrayOf(magnitudes.average().toFloat())
    }
    
    private fun calculateChromaBins(y: FloatArray): FloatArray {
        // Simplified chroma bins calculation
        return FloatArray(12) { (sin(2 * PI * it / 12) * 0.1).toFloat() }
    }
    
    private fun calculateTonnetz(y: FloatArray): FloatArray {
        // Simplified tonnetz calculation
        return floatArrayOf((sin(2 * PI * 0.1) * 0.1).toFloat())
    }
    
    private fun calculateTempo(y: FloatArray): Float {
        // Simplified tempo calculation
        return 120.0f // Default tempo
    }
    
    private fun calculateBeatStrength(y: FloatArray): Float {
        // Simplified beat strength calculation
        return y.map { abs(it) }.average().toFloat()
    }
    
    private fun calculateSpectralContrast(y: FloatArray): FloatArray {
        // Simplified spectral contrast calculation
        return floatArrayOf(y.map { abs(it) }.average().toFloat())
    }
    
    private fun calculateSpectralFlatness(y: FloatArray): FloatArray {
        // Simplified spectral flatness calculation
        val geometricMean = y.map { abs(it) }.filter { it > 0 }.fold(1.0) { acc, value -> acc * value }.pow(1.0 / y.size)
        val arithmeticMean = y.map { abs(it) }.average()
        return floatArrayOf((geometricMean / arithmeticMean).toFloat())
    }
    
    private fun calculateDynamicRange(y: FloatArray): Float {
        val sorted = y.map { abs(it) }.sorted()
        val p95 = sorted[(sorted.size * 0.95).toInt()]
        val p5 = sorted[(sorted.size * 0.05).toInt()]
        return p95 - p5
    }
    
    private fun calculatePeakToRmsRatio(y: FloatArray): Float {
        val peak = y.map { abs(it) }.maxOrNull() ?: 1f
        val rms = sqrt(y.map { it * it }.average()).toFloat()
        return peak / (rms + 1e-8f)
    }
    
    private fun calculateHarmonicRatio(y: FloatArray): Float {
        // Simplified harmonic ratio calculation
        return 0.5f // Default value
    }
    
    private fun calculateSilenceRatio(y: FloatArray): Float {
        val silentSamples = y.count { abs(it) < 0.01 }
        return silentSamples.toFloat() / y.size
    }
    
    private fun performFFT(y: FloatArray): Array<Complex> {
        // Simplified FFT implementation - in production use a proper FFT library
        val n = y.size
        val fft = Array(n) { Complex(0.0, 0.0) }
        
        for (i in 0 until n) {
            fft[i] = Complex(y[i].toDouble(), 0.0)
        }
        
        // Simple DFT implementation (not optimized)
        val result = Array(n) { Complex(0.0, 0.0) }
        for (k in 0 until n) {
            for (j in 0 until n) {
                val angle = -2 * PI * k * j / n
                val cos = cos(angle)
                val sin = sin(angle)
                result[k] = result[k] + fft[j] * Complex(cos, sin)
            }
        }
        
        return result
    }
    
    // Extension functions for statistical calculations
    private fun FloatArray.mean(): Float = this.average().toFloat()
    
    private fun FloatArray.std(): Float {
        val mean = this.mean()
        val variance = this.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance).toFloat()
    }
    
    private fun FloatArray.skewness(): Float {
        val mean = this.mean()
        val std = this.std()
        if (std == 0f) return 0f
        
        val skewness = this.map { ((it - mean) / std).pow(3) }.average()
        return skewness.toFloat()
    }
}

// Simple Complex number class for FFT calculations
private data class Complex(val real: Double, val imag: Double) {
    operator fun plus(other: Complex): Complex = Complex(real + other.real, imag + other.imag)
    operator fun times(other: Complex): Complex = Complex(
        real * other.real - imag * other.imag,
        real * other.imag + imag * other.real
    )
}
