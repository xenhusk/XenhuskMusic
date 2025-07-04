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

import com.mardous.booming.extensions.media.displayArtistName
import com.mardous.booming.model.Song
import com.mardous.booming.model.SongProvider
import com.mardous.booming.repository.Repository
import com.mardous.booming.util.sort.sortedSongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.random.Random

class ShuffleManager() : KoinComponent {

    private val repository: Repository by inject()

    suspend fun applySmartShuffle(songs: List<Song>, mode: SpecialShuffleMode): List<Song> {
        val now = System.currentTimeMillis()
        val expandedSongs = songs.expandSongs()
        return when (mode) {
            SpecialShuffleMode.PureRandom -> songs.shuffled()

            SpecialShuffleMode.MostPlayed -> weightedShuffle(expandedSongs) { 1.0 + it.playCount }

            SpecialShuffleMode.MostPlayedArtists -> {
                val artistWeights = expandedSongs
                    .groupBy { it.displayArtistName() }
                    .mapValues { entry ->
                        entry.value.sumOf { it.playCount }.toDouble()
                    }

                weightedShuffle(songs) { song ->
                    1.0 + (artistWeights[song.displayArtistName()] ?: 1.0)
                }
            }

            SpecialShuffleMode.MostPlayedAlbums -> {
                val albumWeights = expandedSongs
                    .groupBy { it.albumId }
                    .mapValues { entry ->
                        entry.value.sumOf { it.playCount }.toDouble()
                    }

                weightedShuffle(songs) { song -> 1.0 + (albumWeights[song.albumId] ?: 1.0) }
            }

            SpecialShuffleMode.FavoriteSongs -> weightedShuffle(expandedSongs) {
                if (it.isFavorite) 10.0 else 1.0
            }

            SpecialShuffleMode.ForgottenSongs -> weightedShuffle(expandedSongs) {
                val days = (now - it.lastPlayedAt).coerceAtLeast(1L) / 86400000.0
                1.0 + days
            }

            SpecialShuffleMode.RecentlyAdded -> weightedShuffle(expandedSongs) {
                val age = (now - it.dateAdded).coerceAtLeast(1L) / 86400000.0
                1.0 / age
            }

            SpecialShuffleMode.Combined -> weightedShuffle(expandedSongs) {
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