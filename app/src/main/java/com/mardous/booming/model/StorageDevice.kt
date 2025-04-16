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

package com.mardous.booming.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

/**
 * Simplified wrapper for [android.os.storage.StorageVolume].
 */
@Parcelize
class StorageDevice(
    val path: String,
    val name: String,
    val iconRes: Int
) : Parcelable {

    val file: File
        get() = File(path)

    override fun toString(): String {
        return "StorageDevice{path='$path', name='$name'}"
    }
}
