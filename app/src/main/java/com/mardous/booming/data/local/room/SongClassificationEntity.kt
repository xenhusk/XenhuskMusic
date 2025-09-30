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

package com.mardous.booming.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for storing music classification results
 */
@Entity(
    tableName = "song_classifications",
    indices = [Index(value = ["song_id"], unique = true)]
)
data class SongClassificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    
    @ColumnInfo(name = "song_id")
    val songId: Long,
    
    @ColumnInfo(name = "song_title")
    val songTitle: String,
    
    @ColumnInfo(name = "artist_name")
    val artistName: String,
    
    @ColumnInfo(name = "classification_type")
    val classificationType: String, // "Christian" or "Secular"
    
    @ColumnInfo(name = "confidence")
    val confidence: Float,
    
    @ColumnInfo(name = "christian_probability")
    val christianProbability: Float,
    
    @ColumnInfo(name = "secular_probability")
    val secularProbability: Float,
    
    @ColumnInfo(name = "classification_timestamp")
    val classificationTimestamp: Long,
    
    @ColumnInfo(name = "model_version")
    val modelVersion: String = "1.0.0",
    
    @ColumnInfo(name = "is_manual_override")
    val isManualOverride: Boolean = false,
    
    @ColumnInfo(name = "manual_override_timestamp")
    val manualOverrideTimestamp: Long? = null
)
