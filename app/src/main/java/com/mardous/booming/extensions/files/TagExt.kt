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

package com.mardous.booming.extensions.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mardous.booming.extensions.utilities.appendWithDelimiter
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.KeyNotFoundException
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.reference.GenreTypes

fun String.tryToNormalizeGenreName(): String {
    val genreAsNumber = toIntOrNull()
    if (genreAsNumber != null) {
        val normalizedGenreName = GenreTypes.getInstanceOf().getValueForId(genreAsNumber)
        if (!normalizedGenreName.isNullOrEmpty()) {
            return normalizedGenreName
        }
    }
    return this
}

fun List<String>.merge(separator: String? = null, trim: Boolean = true): String? {
    return if (size == 1) {
        first()
    } else if (!isNullOrEmpty()) {
        val sb = StringBuilder()
        for (string in this) {
            if (string.isEmpty()) continue
            sb.appendWithDelimiter(if (trim) string.trim() else string, separator ?: "; ")
        }
        sb.toString()
    } else null
}

fun List<String>.safeMerge(separator: String? = null, trim: Boolean = true): String? {
    return splitIfNeeded().merge(separator, trim)
}

fun List<String>.splitIfNeeded(): List<String> {
    return when {
        isNullOrEmpty() -> emptyList()
        size > 1 -> this
        else -> first().split(";").map { it.trim() }
    }
}

fun Tag.getGenres(limit: Int = -1, lowercase: Boolean = false): List<String>? {
    val genres = readListTag(FieldKey.GENRE)
        .splitIfNeeded()
        .let { if (limit > 0) it.take(limit) else it }
    if (genres.isNotEmpty()) {
        return genres.map { (if (lowercase) it.lowercase() else it).tryToNormalizeGenreName() }
    }
    return null
}

fun Tag.getGenre(): String? {
    return getGenres(1)?.firstOrNull()
}

fun Tag.readListTag(key: FieldKey): List<String> {
    return try {
        getAll(key)
    } catch (ignored: KeyNotFoundException) {
        ArrayList()
    }
}

fun AudioFile.getBestTag(isV1Allowed: Boolean = true): Tag? {
    val tag = tagOrCreateAndSetDefault
    if (tag.isEmpty && isV1Allowed) {
        if (this is MP3File && this.hasID3v1Tag()) {
            return iD3v1Tag
        }
        return null
    }
    return tag
}

fun Artwork.toBitmap(): Bitmap = BitmapFactory.decodeByteArray(binaryData, 0, binaryData.size)