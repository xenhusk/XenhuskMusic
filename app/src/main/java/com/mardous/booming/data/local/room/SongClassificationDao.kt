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

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for song classification operations
 */
@Dao
interface SongClassificationDao {
    
    @Query("SELECT * FROM song_classifications WHERE song_id = :songId")
    suspend fun getClassificationBySongId(songId: Long): SongClassificationEntity?
    
    @Query("SELECT * FROM song_classifications WHERE song_id = :songId")
    fun getClassificationBySongIdFlow(songId: Long): Flow<SongClassificationEntity?>
    
    @Query("SELECT * FROM song_classifications WHERE classification_type = :type ORDER BY confidence DESC")
    suspend fun getClassificationsByType(type: String): List<SongClassificationEntity>
    
    @Query("SELECT * FROM song_classifications WHERE classification_type = :type ORDER BY confidence DESC")
    fun getClassificationsByTypeFlow(type: String): Flow<List<SongClassificationEntity>>
    
    @Query("SELECT * FROM song_classifications ORDER BY classification_timestamp DESC")
    suspend fun getAllClassifications(): List<SongClassificationEntity>
    
    @Query("SELECT * FROM song_classifications ORDER BY classification_timestamp DESC")
    fun getAllClassificationsFlow(): Flow<List<SongClassificationEntity>>
    
    @Query("SELECT COUNT(*) FROM song_classifications WHERE classification_type = :type")
    suspend fun getClassificationCount(type: String): Int
    
    @Query("SELECT COUNT(*) FROM song_classifications WHERE classification_type = :type")
    fun getClassificationCountFlow(type: String): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM song_classifications")
    suspend fun getTotalClassificationCount(): Int
    
    @Query("SELECT COUNT(*) FROM song_classifications")
    fun getTotalClassificationCountFlow(): Flow<Int>
    
    @Query("SELECT * FROM song_classifications WHERE is_manual_override = 1")
    suspend fun getManualOverrides(): List<SongClassificationEntity>
    
    @Query("SELECT * FROM song_classifications WHERE is_manual_override = 1")
    fun getManualOverridesFlow(): Flow<List<SongClassificationEntity>>
    
    @Query("SELECT DISTINCT song_id FROM song_classifications")
    suspend fun getAllClassifiedSongIds(): List<Long>
    
    @Query("SELECT DISTINCT song_id FROM song_classifications WHERE classification_type = :type")
    suspend fun getClassifiedSongIdsByType(type: String): List<Long>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassification(classification: SongClassificationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassifications(classifications: List<SongClassificationEntity>)
    
    @Update
    suspend fun updateClassification(classification: SongClassificationEntity)
    
    @Delete
    suspend fun deleteClassification(classification: SongClassificationEntity)
    
    @Query("DELETE FROM song_classifications WHERE song_id = :songId")
    suspend fun deleteClassificationBySongId(songId: Long)
    
    @Query("DELETE FROM song_classifications")
    suspend fun deleteAllClassifications()
    
    @Query("DELETE FROM song_classifications WHERE classification_timestamp < :timestamp")
    suspend fun deleteOldClassifications(timestamp: Long)
}
