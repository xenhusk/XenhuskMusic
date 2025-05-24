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

package com.mardous.booming.helper

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.mardous.booming.extensions.hasQ
import com.mardous.booming.model.Song
import com.mardous.booming.repository.RealSongRepository

class UriSongResolver(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val songRepository: RealSongRepository
) {
    companion object {
        private const val TAG = "UriSongResolver"
    }

    fun resolve(uri: Uri): List<Song> {
        var songs: List<Song> = emptyList()
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val authority = uri.authority ?: ""
            when (authority) {
                MediaStore.AUTHORITY -> {
                    val songId = uri.lastPathSegment
                    if (songId != null) {
                        songs = songRepository.songs(songId)
                    }
                }

                else -> {
                    try {
                        if (hasQ()) {
                            val id = MediaStore.getMediaUri(context, uri)?.lastPathSegment
                            if (id != null) {
                                songs = songRepository.songs(id)
                            }
                        } else {
                            if (authority == "com.android.providers.media.documents") {
                                val id = getSongIdFromMediaProvider(uri)
                                if (id != null) {
                                    songs = songRepository.songs(id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to retrieve song info from Uri: $uri", e)
                    }
                }
            }
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val path = uri.path
            if (path != null) {
                songs = songRepository.songsByFilePath(path, true)
            }
        }

        if (songs.isEmpty() && uri.scheme == ContentResolver.SCHEME_CONTENT) {
            songs = listOf(findSongFromFileProviderUri(uri))
        }

        if (songs.isEmpty()) {
            Log.e(TAG, "Couldn't resolve songs from Uri: $uri")
        }

        return songs
    }

    private fun getSongIdFromMediaProvider(uri: Uri): String? {
        val docId = DocumentsContract.getDocumentId(uri)
        val parts = docId.split(":")
        return if (parts.size == 2) parts[1] else null
    }

    private fun getDisplayNameAndSize(uri: Uri): Pair<String, Long>? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                return name to size
            }
        }
        return null
    }

    private fun findSongFromFileProviderUri(uri: Uri): Song {
        val (name, size) = getDisplayNameAndSize(uri)
            ?: return Song.emptySong

        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.SIZE} = ?"
        val selectionArgs = arrayOf(name, size.toString())

        val cursor = songRepository.makeSongCursor(selection, selectionArgs, ignoreBlacklist = true)
        return songRepository.song(cursor)
    }
}
