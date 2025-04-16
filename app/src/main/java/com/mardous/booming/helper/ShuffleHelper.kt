/*
 * Copyright (c) 2024 Christians Martínez Alvarado
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

package com.mardous.booming.helper

import androidx.annotation.WorkerThread
import com.mardous.booming.model.Album
import com.mardous.booming.model.Artist
import com.mardous.booming.model.Song
import com.mardous.booming.util.Preferences
import com.mardous.booming.util.SelectedShuffleMode
import com.mardous.booming.util.sort.SortKeys
import com.mardous.booming.util.sort.sortedSongs

/**
 * @author Christians M. A. (mardous)
 */
object ShuffleHelper {

    @JvmStatic
    fun <T : Song> makeShuffleList(listToShuffle: MutableList<T>, current: Int) {
        if (listToShuffle.isEmpty()) return
        if (current >= 0) {
            val song = listToShuffle.removeAt(current)
            listToShuffle.shuffle()
            listToShuffle.add(0, song)
        } else {
            listToShuffle.shuffle()
        }
    }

    @WorkerThread
    fun shuffleAlbums(fromAlbums: List<Album>?): List<Song> {
        val songs = ArrayList<Song>()
        if (fromAlbums.isNullOrEmpty()) return songs

        val albums = ArrayList(fromAlbums)
        if (albums.isNotEmpty()) {
            when (Preferences.albumShuffleMode) {
                SelectedShuffleMode.SHUFFLE_ALBUMS -> {
                    albums.shuffle()
                    for (album in albums) {
                        addSongsToList(album.songs, songs, SortKeys.TRACK_NUMBER)
                    }
                }

                SelectedShuffleMode.SHUFFLE_SONGS -> {
                    //albums.sort()
                    for (album in albums) {
                        addSongsToList(album.songs, songs, null)
                    }
                }

                SelectedShuffleMode.SHUFFLE_ALL -> {
                    albums.shuffle()
                    for (album in albums) {
                        addSongsToList(album.songs, songs, null)
                    }
                }
            }
        }
        return songs
    }

    @WorkerThread
    fun shuffleArtists(fromArtists: List<Artist>?): List<Song> {
        val songs = ArrayList<Song>()
        if (fromArtists.isNullOrEmpty()) return songs

        val artists = ArrayList(fromArtists)
        if (artists.isNotEmpty()) {
            when (Preferences.artistShuffleMode) {
                SelectedShuffleMode.SHUFFLE_ARTISTS -> {
                    artists.shuffle()
                    for (artist in artists) {
                        addSongsToList(artist.songs, songs, SortKeys.AZ)
                    }
                }

                SelectedShuffleMode.SHUFFLE_SONGS -> {
                    //artists.sort()
                    for (artist in artists) {
                        addSongsToList(artist.songs, songs, null)
                    }
                }

                SelectedShuffleMode.SHUFFLE_ALL -> {
                    artists.shuffle()
                    for (artist in artists) {
                        addSongsToList(artist.songs, songs, null)
                    }
                }
            }
        }
        return songs
    }

    private fun addSongsToList(songs: List<Song>, destination: MutableList<Song>, sortKey: String?) {
        val songList = when {
            sortKey != null -> songs.sortedSongs(sortKey, false)
            else -> songs.shuffled()
        }
        destination.addAll(songList)
    }
}