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
import java.util.regex.Pattern

/**
 * Created by hefuyi on 2016/11/8.
 */
object LyricsUtil {
    private val lrcRootPath = Environment.getExternalStorageDirectory().toString() + "/BoomingMusic/lyrics/"

    fun writeLrc(song: Song, lrcContext: String) {
        var writer: FileWriter? = null
        try {
            var location = getLocalLyricOriginalFile(song)
                ?: getLocalLyricFile(song)
                ?: File("$lrcRootPath${song.artistName} - ${song.title}.lrc")

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

    private fun getLocalLyricFile(song: Song): File? {
        val file = File(song.data)
        val dir = File(lrcRootPath)
        return getLrcFileFromDir(dir, file, song)
    }

    private fun getLocalLyricOriginalFile(song: Song): File? {
        val file = File(song.data)
        val dir = file.absoluteFile.parentFile
        return getLrcFileFromDir(dir, file, song)
    }

    private fun getLrcFileFromDir(dir: File?, file: File, song: Song): File? {
        if (dir != null && dir.exists() && dir.isDirectory) {
            val format = ".*%s.*\\.(lrc|txt)"

            val filename = Pattern.quote(file.nameWithoutExtension)
            val songinfo = Pattern.quote("${song.artistName} - ${song.title}")
            val songtitle = Pattern.quote(song.title)

            val patterns = mutableListOf<Pattern>().apply {
                add(
                    Pattern.compile(
                        String.format(format, filename),
                        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
                    )
                )
                add(
                    Pattern.compile(
                        String.format(format, songinfo),
                        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
                    )
                )
                add(
                    Pattern.compile(
                        String.format(format, songtitle),
                        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
                    )
                )
            }

            val files = dir.listFiles { f: File ->
                if (f.isDirectory) {
                    false
                } else {
                    for (pattern in patterns) {
                        if (pattern.matcher(f.name).matches()) return@listFiles true
                    }
                    false
                }
            }

            return files?.firstOrNull()
        }
        return null
    }

    fun getSyncedLyricsFile(song: Song): File? {
        var lrcFile = getLocalLyricOriginalFile(song)
        if (lrcFile == null || !lrcFile.exists()) {
            lrcFile = getLocalLyricFile(song)
        }
        return lrcFile
    }
}