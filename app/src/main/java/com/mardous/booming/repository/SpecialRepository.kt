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

package com.mardous.booming.repository

import android.provider.MediaStore.Audio.AudioColumns
import com.mardous.booming.model.ReleaseYear
import com.mardous.booming.model.Song
import com.mardous.booming.util.sort.SortOrder
import com.mardous.booming.util.sort.sortedSongs
import com.mardous.booming.util.sort.sortedYears

interface SpecialRepository {
    suspend fun releaseYears(): List<ReleaseYear>
    suspend fun releaseYear(year: Int): ReleaseYear
    suspend fun songs(year: Int, query: String): List<Song>
}

class RealSpecialRepository(private val songRepository: RealSongRepository) : SpecialRepository {

    override suspend fun releaseYears(): List<ReleaseYear> {
        val songs = songRepository.songs(
            songRepository.makeSongCursor("${AudioColumns.YEAR} > 0", null)
        )
        val grouped = songs.groupBy { it.year }
        return grouped.map { ReleaseYear(it.key, it.value) }.sortedYears(SortOrder.yearSortOrder)
    }

    override suspend fun releaseYear(year: Int): ReleaseYear {
        val songs = songRepository.songs(
            songRepository.makeSongCursor(
                selection = "${AudioColumns.YEAR}=?",
                selectionValues = arrayOf(year.toString())
            )
        )
        return ReleaseYear(year, songs.sortedSongs(SortOrder.yearSongSortOrder))
    }

    override suspend fun songs(year: Int, query: String): List<Song> {
        return songRepository.songs(
            songRepository.makeSongCursor(
                selection = "${AudioColumns.YEAR}=? AND ${AudioColumns.TITLE} LIKE ?",
                selectionValues = arrayOf(year.toString(), "%$query%")
            )
        )
    }
}