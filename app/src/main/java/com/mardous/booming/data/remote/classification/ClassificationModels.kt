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

import kotlinx.serialization.Serializable

/**
 * Data classes for music classification API communication
 */

@Serializable
data class ClassificationRequest(
    val audio_data: List<Float>,
    val sample_rate: Int = 22050,
    val song_id: String
)

@Serializable
data class ClassificationResponse(
    val prediction: String,
    val confidence: Float,
    val probabilities: ClassProbabilities,
    val success: Boolean,
    val song_id: String? = null,
    val error: String? = null
)

@Serializable
data class ClassProbabilities(
    val christian: Float,
    val secular: Float
)

@Serializable
data class BatchClassificationRequest(
    val songs: List<BatchSongRequest>
)

@Serializable
data class BatchSongRequest(
    val song_id: String,
    val audio_data: List<Float>,
    val sample_rate: Int = 22050
)

@Serializable
data class BatchClassificationResponse(
    val results: List<ClassificationResponse>,
    val summary: BatchSummary
)

@Serializable
data class BatchSummary(
    val total: Int,
    val successful: Int,
    val failed: Int
)

@Serializable
data class HealthCheckResponse(
    val status: String,
    val model_loaded: Boolean,
    val service: String,
    val version: String
)

@Serializable
data class ModelInfoResponse(
    val model_type: String,
    val total_features: Int,
    val selected_features: Int,
    val label_map: Map<String, String>,
    val class_weights: Map<String, Float>,
    val feature_names: List<String>,
    val selected_feature_names: List<String>
)
