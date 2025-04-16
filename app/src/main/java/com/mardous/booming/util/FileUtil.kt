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

package com.mardous.booming.util

import android.os.Environment
import android.provider.MediaStore.Audio.AudioColumns
import com.mardous.booming.appContext
import com.mardous.booming.extensions.files.getCanonicalPathSafe
import com.mardous.booming.model.Song
import com.mardous.booming.repository.RealSongRepository
import com.mardous.booming.util.cursor.SortedCursor
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File

object FileUtil : KoinComponent {

    // Publicly accessible directories
    const val BOOMING_ARTWORK_DIRECTORY_NAME = "Booming Artwork"
    const val PLAYLISTS_DIRECTORY_NAME = "Playlists"

    // Directories that are accessible only for Booming
    private const val CUSTOM_ARTIST_IMAGES_DIRECTORY_NAME = "/custom_artist_images/"
    private const val THUMBS_DIRECTORY_NAME = "Thumbs"

    fun externalStorageDirectory(dirType: String? = null): File {
        return if (dirType == null) {
            Environment.getExternalStorageDirectory()
        } else {
            Environment.getExternalStoragePublicDirectory(dirType)
        }
    }

    fun imagesDirectory(dirName: String) =
        externalStorageDirectory(Environment.DIRECTORY_PICTURES).resolve(dirName).ensureDirectory()

    fun playlistsDirectory() =
        externalStorageDirectory().resolve(PLAYLISTS_DIRECTORY_NAME).ensureDirectory()

    fun customArtistImagesDirectory() =
        appContext().filesDir.resolve(CUSTOM_ARTIST_IMAGES_DIRECTORY_NAME).ensureDirectory()

    fun thumbsDirectory() =
        appContext().externalCacheDir?.resolve(THUMBS_DIRECTORY_NAME).ensureDirectory()

    fun getDefaultStartDirectory(): File {
        val musicDir = externalStorageDirectory(Environment.DIRECTORY_MUSIC)
        return if (musicDir.exists() && musicDir.isDirectory) {
            musicDir
        } else {
            val externalStorage = externalStorageDirectory()
            if (externalStorage.exists() && externalStorage.isDirectory) {
                externalStorage
            } else {
                File("/") // root
            }
        }
    }

    fun matchFilesWithMediaStore(files: List<File>): List<Song> {
        var selection: String? = null
        val paths: Array<String> = Array(files.size) {
            files[it].getCanonicalPathSafe()
        }
        if (files.size in 1..998) { // 999 is the max amount Androids SQL implementation can handle.
            selection = AudioColumns.DATA + " IN (" + makePlaceholders(files.size) + ")"
        }
        val songRepository = get<RealSongRepository>()
        val songCursor = songRepository.makeSongCursor(selection, if (selection == null) null else paths)
        return when {
            songCursor != null -> songRepository.songs(
                SortedCursor(
                    songCursor,
                    paths,
                    AudioColumns.DATA
                )
            )
            else -> ArrayList()
        }
    }

    private fun makePlaceholders(len: Int): String {
        val sb = StringBuilder(len * 2 - 1)
        sb.append("?")
        for (i in 1 until len) {
            sb.append(",?")
        }
        return sb.toString()
    }

    private fun File?.ensureDirectory() = takeIf { it != null && (it.exists() || it.mkdirs()) }
}