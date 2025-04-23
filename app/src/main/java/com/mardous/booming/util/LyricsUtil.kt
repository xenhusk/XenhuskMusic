/*
 * Copyright (c) 2019 Hemanth Savarala.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by
 *  the Free Software Foundation either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */
package com.mardous.booming.util

import android.os.Environment
import com.mardous.booming.model.Song
import java.io.File
import java.io.FileWriter
import java.io.IOException

/**
 * Created by hefuyi on 2016/11/8.
 */
object LyricsUtil {
    private val lrcRootPath = Environment.getExternalStorageDirectory().toString() + "/BoomingMusic/lyrics/"

    fun writeLrc(song: Song, lrcContext: String) {
        var writer: FileWriter? = null
        val location: File?
        try {
            if (isLrcOriginalFileExist(song.data)) {
                location = getLocalLyricOriginalFile(song.data)
            } else if (isLrcFileExist(song.title, song.artistName)) {
                location = getLocalLyricFile(song.title, song.artistName)
            } else {
                location = File(getLrcPath(song.title, song.artistName))
                if (location.parentFile?.exists() != true) {
                    location.parentFile?.mkdirs()
                }
            }
            writer = FileWriter(location)
            writer.write(lrcContext)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                writer?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun isLrcFileExist(title: String, artist: String): Boolean {
        val file = File(getLrcPath(title, artist))
        return file.exists()
    }

    private fun isLrcOriginalFileExist(path: String): Boolean {
        val file = File(getLrcOriginalPath(path))
        return file.exists()
    }

    private fun getLocalLyricFile(title: String, artist: String): File? {
        return File(getLrcPath(title, artist)).takeIf { it.exists() }
    }

    private fun getLocalLyricOriginalFile(path: String): File? {
        return File(getLrcOriginalPath(path)).takeIf { it.exists() }
    }

    private fun getLrcPath(title: String, artist: String): String {
        return "$lrcRootPath$title - $artist.lrc"
    }

    private fun getLrcOriginalPath(filePath: String): String {
        return filePath.replace(filePath.substring(filePath.lastIndexOf(".") + 1), "lrc")
    }

    fun getSyncedLyricsFile(song: Song): File? {
        return when {
            isLrcOriginalFileExist(song.data) -> {
                getLocalLyricOriginalFile(song.data)
            }
            isLrcFileExist(song.title, song.artistName) -> {
                getLocalLyricFile(song.title, song.artistName)
            }
            else -> {
                null
            }
        }
    }
}