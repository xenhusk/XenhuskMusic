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

import android.content.SharedPreferences
import androidx.core.content.edit
import com.mardous.booming.util.Preferences.requireString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SortOrder(
    private val sharedPreferences: SharedPreferences,
    private val id: String,
    private val defaultSortOrder: String,
    private val defaultDescending: Boolean
) {

    var value: String
        get() = sharedPreferences.requireString("${id}_sort_order", defaultSortOrder)
        set(value) = sharedPreferences.edit { putString("${id}_sort_order", value) }

    var isDescending: Boolean
        get() = sharedPreferences.getBoolean("${id}_descending", defaultDescending)
        set(value) = sharedPreferences.edit { putBoolean("${id}_descending", value) }

    var ignoreArticles: Boolean
        get() = sharedPreferences.getBoolean("${id}_sort_order_ignore_articles", true)
        set(value) = sharedPreferences.edit { putBoolean("${id}_sort_order_ignore_articles", value) }

    companion object : KoinComponent {
        private val preferences: SharedPreferences by inject()
        private val sortOrderMap = hashMapOf<String, SortOrder>()

        val songSortOrder get() = sortOrder("song", SortKeys.AZ, false)

        val albumSortOrder get() = sortOrder("album", SortKeys.AZ, false)

        val albumSongSortOrder get() = sortOrder("album_song", SortKeys.TRACK_NUMBER, false)

        val similarAlbumSortOrder get() = sortOrder("similar_album", SortKeys.AZ, false)

        val artistSortOrder get() = sortOrder("artist", SortKeys.AZ, false)

        val artistAlbumSortOrder get() = sortOrder("artist_album", SortKeys.YEAR, true)

        val artistSongSortOrder get() = sortOrder("artist_song", SortKeys.AZ, false)

        val genreSortOrder get() = sortOrder("genre", SortKeys.AZ, false)

        val genreSongSortOrder get() = sortOrder("genre_song", SortKeys.AZ, false)

        val yearSortOrder get() = sortOrder("year", SortKeys.YEAR, false)

        val yearSongSortOrder get() = sortOrder("year_song", SortKeys.AZ, false)

        val folderSortOrder get() = sortOrder("folder", SortKeys.AZ, false)

        val folderSongSortOrder get() = sortOrder("folder_song", SortKeys.DATE_ADDED, true)

        private fun sortOrder(key: String, defOrder: String, defDescending: Boolean) =
            sortOrderMap.getOrPut(key) { SortOrder(preferences, key, defOrder, defDescending) }
    }
}