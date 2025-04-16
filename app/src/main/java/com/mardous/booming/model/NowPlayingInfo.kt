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
import com.mardous.booming.R
import com.mardous.booming.extensions.files.getGenre
import kotlinx.parcelize.Parcelize
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey

@Parcelize
class NowPlayingInfo(val info: Info, var isEnabled: Boolean) : Parcelable {

    enum class Info(val displayNameRes: Int, val key: FieldKey?) {
        Album(R.string.album, FieldKey.ALBUM),
        AlbumArtist(R.string.album_artist, FieldKey.ALBUM_ARTIST),
        Genre(R.string.genre, FieldKey.GENRE),
        Year(R.string.year, FieldKey.YEAR),
        Composer(R.string.composer, FieldKey.COMPOSER),
        Conductor(R.string.conductor, FieldKey.CONDUCTOR),
        Publisher(R.string.publisher, FieldKey.RECORD_LABEL),
        Format(R.string.label_file_format, null),
        Bitrate(R.string.label_bit_rate, null),
        SampleRate(R.string.label_sampling_rate, null);

        fun toReadableFormat(tag: org.jaudiotagger.tag.Tag, header: AudioHeader): String? {
            if (key == null) {
                return when (this) {
                    Format -> String.format("%s %s", header.encodingType.lowercase(), header.channels)
                    Bitrate -> String.format("%s KB/s", header.bitRate)
                    SampleRate -> String.format("%.1f KHz", header.sampleRateAsNumber.toFloat() / 1000)
                    else -> null
                }
            }
            return if (key == FieldKey.GENRE) {
                tag.getGenre()
            } else tag.getFirst(key)
        }
    }
}