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

package com.mardous.booming.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

/**
 * @author Christians M. A. (mardous)
 */
@Parcelize
class Folder(val path: String, val songs: List<Song>) : Parcelable {

    companion object {
        val empty = Folder("", emptyList())
    }

    val id: Long
        get() = path.hashCode().toLong()

    val name: String
        get() = path.substringAfterLast("/")

    val file: File
        get() = File(path)

    val songCount: Int
        get() = songs.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Folder) return false

        if (path != other.path) return false
        if (songs != other.songs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + songs.hashCode()
        return result
    }

    override fun toString(): String {
        return "Folder(path='$path', songs=$songs)"
    }
}
