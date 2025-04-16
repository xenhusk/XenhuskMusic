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

package com.mardous.booming.util.sort

import android.view.Menu
import android.view.MenuItem
import com.mardous.booming.R
import com.mardous.booming.extensions.media.albumArtistName
import com.mardous.booming.model.*
import java.text.Collator

fun List<Song>.sortedSongs(sortOrder: SortOrder): List<Song> {
    return sortedSongs(sortOrder.value, sortOrder.isDescending)
}

fun List<Song>.sortedSongs(sortOrder: String, descending: Boolean): List<Song> {
    val collator = Collator.getInstance()
    val songs = when (sortOrder) {
        SortKeys.AZ -> sortedWith { a: Song, b: Song -> collator.compare(a.title, b.title) }
        SortKeys.ARTIST -> sortedWith { a: Song, b: Song -> collator.compare(a.artistName, b.artistName) }
        SortKeys.ALBUM -> sortedWith { a: Song, b: Song -> collator.compare(a.albumName, b.albumName) }
        SortKeys.TRACK_NUMBER -> sortedWith { a: Song, b: Song -> a.trackNumber.compareTo(b.trackNumber) }
        SortKeys.DURATION -> sortedWith { a: Song, b: Song -> a.duration.compareTo(b.duration) }
        SortKeys.YEAR -> sortedWith { a: Song, b: Song -> a.year.compareTo(b.year) }
        SortKeys.DATE_ADDED -> sortedWith { a: Song, b: Song -> a.dateAdded.compareTo(b.dateAdded) }
        else -> this
    }
    if (descending) {
        return songs.reversed()
    }
    return songs
}

fun List<Artist>.sortedArtists(sortOrder: SortOrder): List<Artist> {
    val collator = Collator.getInstance()
    val artists = when (sortOrder.value) {
        SortKeys.AZ -> sortedWith { a: Artist, b: Artist -> collator.compare(a.name, b.name) }
        SortKeys.NUMBER_OF_SONGS -> sortedWith { a: Artist, b: Artist -> a.songCount.compareTo(b.songCount) }
        SortKeys.NUMBER_OF_ALBUMS -> sortedWith { a: Artist, b: Artist -> a.albumCount.compareTo(b.albumCount) }
        else -> this
    }
    if (sortOrder.isDescending) {
        return artists.reversed()
    }
    return artists
}

fun List<Album>.sortedAlbums(sortOrder: SortOrder): List<Album> {
    val collator = Collator.getInstance()
    val albums = when (sortOrder.value) {
        SortKeys.AZ -> sortedWith { a: Album, b: Album -> collator.compare(a.name, b.name) }
        SortKeys.ARTIST -> sortedWith { a: Album, b: Album ->
            collator.compare(a.albumArtistName(), b.albumArtistName())
        }

        SortKeys.YEAR -> sortedWith { a: Album, b: Album -> a.year.compareTo(b.year) }
        SortKeys.NUMBER_OF_SONGS -> sortedWith { a: Album, b: Album -> a.songCount.compareTo(b.songCount) }
        else -> this
    }
    if (sortOrder.isDescending) {
        return albums.reversed()
    }
    return albums
}

fun List<Genre>.sortedGenres(sortOrder: SortOrder): List<Genre> {
    val collator = Collator.getInstance()
    val genres = when (sortOrder.value) {
        SortKeys.AZ -> sortedWith { a: Genre, b: Genre -> collator.compare(a.name, b.name) }
        SortKeys.NUMBER_OF_SONGS -> sortedWith { a: Genre, b: Genre -> a.songCount.compareTo(b.songCount) }
        else -> this
    }
    if (sortOrder.isDescending) {
        return genres.reversed()
    }
    return genres
}

fun List<ReleaseYear>.sortedYears(sortOrder: SortOrder): List<ReleaseYear> {
    val years = when (sortOrder.value) {
        SortKeys.YEAR -> sortedWith { a: ReleaseYear, b: ReleaseYear -> a.year.compareTo(b.year) }
        SortKeys.NUMBER_OF_SONGS -> sortedWith { a: ReleaseYear, b: ReleaseYear -> a.songCount.compareTo(b.songCount) }
        else -> this
    }
    if (sortOrder.isDescending) {
        return years.reversed()
    }
    return years
}

fun Menu.prepareSortOrder(sortOrder: SortOrder) {
    val menu = findItem(R.id.action_sort_order)?.subMenu ?: this
    menu.findItem(R.id.action_sort_order_descending)?.apply {
        isCheckable = true
        isChecked = sortOrder.isDescending
    }
    when (sortOrder.value) {
        SortKeys.AZ -> menu.findItem(R.id.action_sort_order_az)?.isChecked = true
        SortKeys.ARTIST -> menu.findItem(R.id.action_sort_order_artist)?.isChecked = true
        SortKeys.ALBUM -> menu.findItem(R.id.action_sort_order_album)?.isChecked = true
        SortKeys.TRACK_NUMBER -> menu.findItem(R.id.action_sort_order_track_list)?.isChecked = true
        SortKeys.DURATION -> menu.findItem(R.id.action_sort_order_duration)?.isChecked = true
        SortKeys.YEAR -> menu.findItem(R.id.action_sort_order_year)?.isChecked = true
        SortKeys.DATE_ADDED -> menu.findItem(R.id.action_sort_order_date_added)?.isChecked = true
        SortKeys.NUMBER_OF_SONGS -> menu.findItem(R.id.action_sort_order_number_of_songs)?.isChecked = true
        SortKeys.NUMBER_OF_ALBUMS -> menu.findItem(R.id.action_sort_order_number_of_albums)?.isChecked = true
    }
}

fun MenuItem.selectedSortOrder(sortOrder: SortOrder): Boolean {
    val handled = when (this.itemId) {
        R.id.action_sort_order_az -> {
            sortOrder.value = SortKeys.AZ
            isChecked = true
            true
        }

        R.id.action_sort_order_artist -> {
            sortOrder.value = SortKeys.ARTIST
            isChecked = true
            true
        }

        R.id.action_sort_order_album -> {
            sortOrder.value = SortKeys.ALBUM
            isChecked = true
            true
        }

        R.id.action_sort_order_track_list -> {
            sortOrder.value = SortKeys.TRACK_NUMBER
            isChecked = true
            true
        }

        R.id.action_sort_order_duration -> {
            sortOrder.value = SortKeys.DURATION
            isChecked = true
            true
        }

        R.id.action_sort_order_year -> {
            sortOrder.value = SortKeys.YEAR
            isChecked = true
            true
        }

        R.id.action_sort_order_date_added -> {
            sortOrder.value = SortKeys.DATE_ADDED
            isChecked = true
            true
        }

        R.id.action_sort_order_number_of_songs -> {
            sortOrder.value = SortKeys.NUMBER_OF_SONGS
            isChecked = true
            true
        }

        R.id.action_sort_order_number_of_albums -> {
            sortOrder.value = SortKeys.NUMBER_OF_ALBUMS
            isChecked = true
            true
        }

        R.id.action_sort_order_descending -> {
            sortOrder.isDescending = !isChecked
            isChecked = !isChecked
            true
        }

        else -> false
    }
    return handled
}