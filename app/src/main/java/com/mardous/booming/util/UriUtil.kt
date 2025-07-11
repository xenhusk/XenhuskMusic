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

package com.mardous.booming.util

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import androidx.annotation.RequiresApi

object UriUtil {
    @RequiresApi(Build.VERSION_CODES.Q)
    fun getUriFromPath(contentResolver: ContentResolver, path: String): Uri {
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val proj = arrayOf(MediaStore.Files.FileColumns._ID)
        contentResolver.query(uri, proj, MediaColumns.DATA + "=?", arrayOf(path), null)
            ?.use { cursor ->
                if (cursor.count > 0) {
                    cursor.moveToFirst()
                    return ContentUris.withAppendedId(uri, cursor.getLong(0))
                }
            }
        return Uri.EMPTY
    }
}