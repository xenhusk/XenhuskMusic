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

package com.mardous.booming.mvvm

import android.content.Context
import com.mardous.booming.R
import com.mardous.booming.model.Song

data class AddToPlaylistResult(
    val playlistName: String,
    val isWorking: Boolean = false,
    val playlistCreated: Boolean = false,
    val insertedSongs: Int = 0
)

class ImportablePlaylistResult(val playlistName: String, val songs: List<Song>)

class ImportResult(val resultMessage: String) {
    companion object {
        fun success(ctx: Context, res: ImportablePlaylistResult): ImportResult {
            return ImportResult(ctx.getString(R.string.imported_playlist_x, res.playlistName))
        }

        fun error(ctx: Context, res: ImportablePlaylistResult): ImportResult {
            return ImportResult(ctx.getString(R.string.could_not_import_the_playlist_x, res.playlistName))
        }
    }
}