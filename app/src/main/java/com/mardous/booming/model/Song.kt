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

import android.annotation.SuppressLint
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Parcelable
import android.provider.MediaStore
import com.mardous.booming.extensions.utilities.getStringSafe
import kotlinx.parcelize.Parcelize
import java.util.Date
import java.util.Objects

@Parcelize
open class Song(
    open val id: Long,
    open val data: String,
    open val title: String,
    open val trackNumber: Int,
    open val year: Int,
    open val size: Long,
    open val duration: Long,
    open val dateAdded: Long,
    open val dateModified: Long,
    open val albumId: Long,
    open val albumName: String,
    open val artistId: Long,
    open val artistName: String,
    open val albumArtistName: String?,
    open val genreName: String?
) : Parcelable {

    val mediaStoreUri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    @SuppressLint("InlinedApi")
    constructor(cursor: Cursor) : this(
        cursor.getLong(0),
        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)),
        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)),
        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)),
        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)),
        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)),
        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)),
        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)),
        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)),
        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)),
        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)),
        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)),
        cursor.getStringSafe(MediaStore.Audio.Media.ALBUM_ARTIST),
        cursor.getStringSafe(MediaStore.Audio.Media.GENRE)
    )

    protected constructor(song: Song) : this(
        song.id,
        song.data,
        song.title,
        song.trackNumber,
        song.year,
        song.size,
        song.duration,
        song.dateAdded,
        song.dateModified,
        song.albumId,
        song.albumName,
        song.artistId,
        song.artistName,
        song.albumArtistName,
        song.genreName
    )

    fun getModifiedDate() = Date(dateModified * 1000)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val song = other as Song
        if (id != song.id) return false
        if (trackNumber != song.trackNumber) return false
        if (year != song.year) return false
        if (size != song.size) return false
        if (duration != song.duration) return false
        if (dateAdded != song.dateAdded) return false
        if (dateModified != song.dateModified) return false
        if (albumId != song.albumId) return false
        if (albumName != song.albumName) return false
        if (artistId != song.artistId) return false
        if (artistName != song.artistName) return false
        if (data != song.data) return false
        if (title != song.title) return false
        if (albumArtistName != song.albumArtistName) return false
        return genreName == song.genreName
    }

    override fun hashCode(): Int {
        return Objects.hash(
            id,
            data,
            title,
            trackNumber,
            year,
            size,
            duration,
            dateAdded,
            dateModified,
            albumId,
            albumName,
            artistId,
            artistName,
            albumArtistName,
            genreName
        )
    }

    override fun toString(): String {
        return "Song{" +
                "id=" + id +
                ", data='" + data + '\'' +
                ", title='" + title + '\'' +
                ", trackNumber=" + trackNumber +
                ", year=" + year +
                ", size=" + size +
                ", duration=" + duration +
                ", dateAdded=" + dateAdded +
                ", dateModified=" + dateModified +
                ", albumId=" + albumId +
                ", albumName='" + albumName + '\'' +
                ", artistId=" + artistId +
                ", artistName='" + artistName + '\'' +
                ", albumArtistName='" + albumArtistName + '\'' +
                '}'
    }

    companion object {
        val emptySong = Song(-1, "", "", -1, -1, -1, -1, -1, -1, -1, "", -1, "", "", "")
    }
}