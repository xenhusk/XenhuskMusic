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

package com.mardous.booming.service.queue

import com.mardous.booming.R
import com.mardous.booming.extensions.media.displayArtistName
import com.mardous.booming.model.Song
import com.mardous.booming.model.SongProvider
import com.mardous.booming.repository.Repository
import com.mardous.booming.util.sort.sortedSongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

class ShuffleManager(private val repository: Repository) {

    enum class ShuffleMode(val iconRes: Int, val titleRes: Int, val descriptionRes: Int) {
        PureRandom(
            R.drawable.ic_shuffle_24dp,
            R.string.shuffle_mode_pure_random,
            R.string.shuffle_mode_pure_random_description
        ),
        MostPlayed(
            R.drawable.ic_trending_up_24dp,
            R.string.shuffle_mode_most_played,
            R.string.shuffle_mode_most_played_description
        ),
        MostPlayedArtists(
            R.drawable.ic_artist_24dp,
            R.string.shuffle_mode_most_played_artists,
            R.string.shuffle_mode_most_played_artists_description
        ),
        MostPlayedAlbums(
            R.drawable.ic_album_24dp,
            R.string.shuffle_mode_most_played_albums,
            R.string.shuffle_mode_most_played_albums_description
        ),
        FavoriteSongs(
            R.drawable.ic_favorite_outline_24dp,
            R.string.shuffle_mode_favorite_songs,
            R.string.shuffle_mode_favorite_songs_description
        ),
        ForgottenSongs(
            R.drawable.ic_trending_down_24dp,
            R.string.shuffle_mode_forgotten_songs,
            R.string.shuffle_mode_forgotten_songs_description
        ),
        RecentlyAdded(
            R.drawable.ic_library_add_24dp,
            R.string.shuffle_mode_recently_added,
            R.string.shuffle_mode_recently_added_description
        ),
        Combined(
            R.drawable.ic_shuffle_on_24dp,
            R.string.shuffle_mode_combined,
            R.string.shuffle_mode_combined_description
        )
    }

    enum class GroupShuffleMode {
        ByGroup,
        BySong,
        FullRandom
    }

    suspend fun applySmartShuffle(songs: List<Song>, mode: ShuffleMode): List<Song> {
        val now = System.currentTimeMillis()
        val expandedSongs = songs.expandSongs()
        return when (mode) {
            ShuffleMode.PureRandom -> songs.shuffled()

            ShuffleMode.MostPlayed -> weightedShuffle(expandedSongs) { 1.0 + it.playCount }

            ShuffleMode.MostPlayedArtists -> {
                val artistWeights = expandedSongs
                    .groupBy { it.displayArtistName() }
                    .mapValues { entry ->
                        entry.value.sumOf { it.playCount }.toDouble()
                    }

                weightedShuffle(songs) { song ->
                    1.0 + (artistWeights[song.displayArtistName()] ?: 1.0)
                }
            }

            ShuffleMode.MostPlayedAlbums -> {
                val albumWeights = expandedSongs
                    .groupBy { it.albumId }
                    .mapValues { entry ->
                        entry.value.sumOf { it.playCount }.toDouble()
                    }

                weightedShuffle(songs) { song -> 1.0 + (albumWeights[song.albumId] ?: 1.0) }
            }

            ShuffleMode.FavoriteSongs -> weightedShuffle(expandedSongs) {
                if (it.isFavorite) 10.0 else 1.0
            }

            ShuffleMode.ForgottenSongs -> weightedShuffle(expandedSongs) {
                val days = (now - it.lastPlayedAt).coerceAtLeast(1L) / 86400000.0
                1.0 + days
            }

            ShuffleMode.RecentlyAdded -> weightedShuffle(expandedSongs) {
                val age = (now - it.dateAdded).coerceAtLeast(1L) / 86400000.0
                1.0 / age
            }

            ShuffleMode.Combined -> weightedShuffle(expandedSongs) {
                val daysSincePlayed = (now - it.lastPlayedAt).coerceAtLeast(1L) / 86400000.0
                val recencyWeight = 1.0 / ((now - it.dateAdded).coerceAtLeast(1L) / 86400000.0)
                val favWeight = if (it.isFavorite) 2.0 else 1.0
                0.4 * it.playCount + 0.3 * daysSincePlayed + 0.3 * recencyWeight * favWeight
            }
        }
    }

    suspend fun <T : SongProvider> shuffleByProvider(
        providers: List<T>?,
        mode: GroupShuffleMode,
        sortKey: String? = null
    ): List<Song> = withContext(Dispatchers.IO) {
        if (providers.isNullOrEmpty()) {
            emptyList()
        } else {
            val mutableProviders = providers.toMutableList()
            when (mode) {
                GroupShuffleMode.ByGroup -> {
                    requireNotNull(sortKey) {
                        "sortKey must not be null when using GroupShuffleMode.ByGroup"
                    }
                    mutableProviders.shuffle()
                    mutableProviders.flatMap { group ->
                        group.songs.sortedSongs(sortKey, descending = false, ignoreArticles = true)
                    }
                }

                GroupShuffleMode.BySong -> {
                    mutableProviders.flatMap { it.songs.shuffled() }
                }

                GroupShuffleMode.FullRandom -> {
                    mutableProviders.shuffle()
                    mutableProviders.flatMap { it.songs.shuffled() }
                }
            }
        }
    }

    private fun <T> weightedShuffle(items: List<T>, weightFunc: (T) -> Double): List<T> {
        val rng = Random(System.nanoTime())
        return items
            .map { it to rng.nextDouble() * weightFunc(it).coerceAtLeast(1.0) }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private suspend fun List<Song>.expandSongs(): List<ExpandedSong> {
        return map { song ->
            val playCountEntity = repository.findSongInPlayCount(song.id)
            val isFavorite = repository.isSongFavorite(song.id)
            ExpandedSong(
                song,
                playCountEntity?.playCount ?: 0,
                playCountEntity?.skipCount ?: 0,
                playCountEntity?.timePlayed ?: 0,
                isFavorite
            )
        }
    }
}