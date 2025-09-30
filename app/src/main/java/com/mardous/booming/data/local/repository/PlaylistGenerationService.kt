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
import com.mardous.booming.data.local.room.PlaylistDao
import com.mardous.booming.data.local.room.PlaylistEntity
import com.mardous.booming.data.local.room.SongClassificationDao
import com.mardous.booming.data.local.room.SongEntity
import com.mardous.booming.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for generating playlists based on music classifications
 */
class PlaylistGenerationService(
    private val context: Context,
    private val playlistDao: PlaylistDao,
    private val classificationDao: SongClassificationDao,
    private val songRepository: SongRepository
) {
    
    companion object {
        private const val TAG = "PlaylistGenerationService"
        private const val CHRISTIAN_PLAYLIST_NAME = "Christian Music"
        private const val SECULAR_PLAYLIST_NAME = "Secular Music"
        private const val MIN_CONFIDENCE_THRESHOLD = 0.5f // Minimum confidence for inclusion
    }
    
    /**
     * Generate Christian and Secular playlists based on classifications
     */
    suspend fun generateClassificationPlaylists(): PlaylistGenerationResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating classification playlists...")
            
            // Get all classifications
            val classifications = classificationDao.getAllClassifications()
            if (classifications.isEmpty()) {
                return@withContext PlaylistGenerationResult(
                    christianPlaylistId = null,
                    secularPlaylistId = null,
                    christianSongCount = 0,
                    secularSongCount = 0,
                    error = "No classifications found. Please classify some songs first."
                )
            }
            
            // Separate songs by classification type
            val christianClassifications = classifications.filter { 
                it.classificationType == "Christian" && it.confidence >= MIN_CONFIDENCE_THRESHOLD 
            }
            val secularClassifications = classifications.filter { 
                it.classificationType == "Secular" && it.confidence >= MIN_CONFIDENCE_THRESHOLD 
            }
            
            Log.d(TAG, "Found ${christianClassifications.size} Christian and ${secularClassifications.size} Secular songs")
            
            // Create or update Christian playlist
            val christianPlaylistId = createOrUpdatePlaylist(
                playlistName = CHRISTIAN_PLAYLIST_NAME,
                classifications = christianClassifications,
                description = "Automatically generated playlist of Christian music based on AI classification"
            )
            
            // Create or update Secular playlist
            val secularPlaylistId = createOrUpdatePlaylist(
                playlistName = SECULAR_PLAYLIST_NAME,
                classifications = secularClassifications,
                description = "Automatically generated playlist of secular music based on AI classification"
            )
            
            Log.d(TAG, "Playlist generation complete")
            
            PlaylistGenerationResult(
                christianPlaylistId = christianPlaylistId,
                secularPlaylistId = secularPlaylistId,
                christianSongCount = christianClassifications.size,
                secularSongCount = secularClassifications.size
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating playlists", e)
            PlaylistGenerationResult(
                christianPlaylistId = null,
                secularPlaylistId = null,
                christianSongCount = 0,
                secularSongCount = 0,
                error = e.message
            )
        }
    }
    
    /**
     * Create or update a playlist with classified songs
     */
    private suspend fun createOrUpdatePlaylist(
        playlistName: String,
        classifications: List<com.mardous.booming.data.local.room.SongClassificationEntity>,
        description: String
    ): Long? {
        try {
            // Check if playlist already exists
            val existingPlaylists = playlistDao.playlist(playlistName)
            
            val playlistId = if (existingPlaylists.isEmpty()) {
                // Create new playlist
                Log.d(TAG, "Creating new playlist: $playlistName")
                val playlistEntity = PlaylistEntity(
                    playlistName = playlistName,
                    description = description
                )
                playlistDao.createPlaylist(playlistEntity)
            } else {
                // Use existing playlist
                Log.d(TAG, "Updating existing playlist: $playlistName")
                val existingPlaylist = existingPlaylists.first()
                existingPlaylist.playListId
            }
            
            if (playlistId == -1L) {
                Log.e(TAG, "Failed to create/update playlist: $playlistName")
                return null
            }
            
            // Clear existing songs from playlist
            playlistDao.deletePlaylistSongs(playlistId)
            
            // Add songs to playlist - get actual song data from repository and prevent duplicates
            val uniqueSongIds = classifications.map { it.songId }.distinct()
            val songEntities = uniqueSongIds.mapNotNull { songId ->
                try {
                    // Get the actual song data from the repository
                    val song = songRepository.song(songId)
                    
                    // Convert Song to SongEntity with proper metadata
                    SongEntity(
                        playlistCreatorId = playlistId,
                        id = song.id,
                        data = song.data,
                        title = song.title,
                        trackNumber = song.trackNumber,
                        year = song.year,
                        size = song.size,
                        duration = song.duration,
                        dateAdded = song.dateAdded,
                        dateModified = song.dateModified,
                        albumId = song.albumId,
                        albumName = song.albumName,
                        artistId = song.artistId,
                        artistName = song.artistName,
                        albumArtist = song.albumArtistName,
                        genreName = song.genreName
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not find song with ID $songId", e)
                    null // Skip songs that can't be found
                }
            }
            
            // Insert songs into playlist
            if (songEntities.isNotEmpty()) {
                playlistDao.insertSongsToPlaylist(songEntities)
                Log.d(TAG, "Added ${songEntities.size} songs to playlist: $playlistName")
            }
            
            return playlistId
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating/updating playlist: $playlistName", e)
            return null
        }
    }
    
    /**
     * Update playlists when new classifications are added
     */
    suspend fun updatePlaylistsWithNewClassifications(): PlaylistUpdateResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating playlists with new classifications...")
            
            val result = generateClassificationPlaylists()
            
            PlaylistUpdateResult(
                success = result.error == null,
                christianSongCount = result.christianSongCount,
                secularSongCount = result.secularSongCount,
                error = result.error
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating playlists", e)
            PlaylistUpdateResult(
                success = false,
                christianSongCount = 0,
                secularSongCount = 0,
                error = e.message
            )
        }
    }
    
    /**
     * Get playlist statistics
     */
    suspend fun getPlaylistStats(): PlaylistStats = withContext(Dispatchers.IO) {
        try {
            val christianPlaylist = playlistDao.playlist(CHRISTIAN_PLAYLIST_NAME).firstOrNull()
            val secularPlaylist = playlistDao.playlist(SECULAR_PLAYLIST_NAME).firstOrNull()
            
            val christianSongCount = christianPlaylist?.let { 
                // Get song count from playlist
                0 // Placeholder - would need to query actual song count
            } ?: 0
            
            val secularSongCount = secularPlaylist?.let {
                // Get song count from playlist
                0 // Placeholder - would need to query actual song count
            } ?: 0
            
            PlaylistStats(
                christianPlaylistExists = christianPlaylist != null,
                secularPlaylistExists = secularPlaylist != null,
                christianSongCount = christianSongCount,
                secularSongCount = secularSongCount,
                christianPlaylistId = christianPlaylist?.playListId,
                secularPlaylistId = secularPlaylist?.playListId
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting playlist stats", e)
            PlaylistStats(
                christianPlaylistExists = false,
                secularPlaylistExists = false,
                christianSongCount = 0,
                secularSongCount = 0
            )
        }
    }
    
    /**
     * Delete classification playlists
     */
    suspend fun deleteClassificationPlaylists(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val christianPlaylist = playlistDao.playlist(CHRISTIAN_PLAYLIST_NAME).firstOrNull()
            val secularPlaylist = playlistDao.playlist(SECULAR_PLAYLIST_NAME).firstOrNull()
            
            christianPlaylist?.let { playlist ->
                playlistDao.deletePlaylistSongs(playlist.playListId)
                Log.d(TAG, "Deleted Christian playlist songs")
            }
            
            secularPlaylist?.let { playlist ->
                playlistDao.deletePlaylistSongs(playlist.playListId)
                Log.d(TAG, "Deleted Secular playlist songs")
            }
            
            Log.d(TAG, "Classification playlists deleted")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting classification playlists", e)
            Result.failure(e)
        }
    }
}

/**
 * Data classes for playlist generation results
 */
data class PlaylistGenerationResult(
    val christianPlaylistId: Long?,
    val secularPlaylistId: Long?,
    val christianSongCount: Int,
    val secularSongCount: Int,
    val error: String? = null
)

data class PlaylistUpdateResult(
    val success: Boolean,
    val christianSongCount: Int,
    val secularSongCount: Int,
    val error: String? = null
)

data class PlaylistStats(
    val christianPlaylistExists: Boolean,
    val secularPlaylistExists: Boolean,
    val christianSongCount: Int,
    val secularSongCount: Int,
    val christianPlaylistId: Long? = null,
    val secularPlaylistId: Long? = null
)
