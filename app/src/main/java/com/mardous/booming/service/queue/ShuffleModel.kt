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

package com.mardous.booming.service.queue

import com.mardous.booming.R
import com.mardous.booming.model.Song
import kotlinx.parcelize.Parcelize

enum class GroupShuffleMode {
    ByGroup,
    BySong,
    FullRandom
}

enum class SpecialShuffleMode(val iconRes: Int, val titleRes: Int, val descriptionRes: Int) {
    PureRandom(
        R.drawable.ic_shuffle_24dp,
        R.string.shuffle_mode_pure_random,
        R.string.shuffle_mode_pure_random_description
    ),
    MostPlayed(
        R.drawable.ic_trending_up_24dp,
        R.string.shuffle_mode_most_played,
        R.string.shuffle_mode_most_played_description
    ),
    MostPlayedArtists(
        R.drawable.ic_artist_24dp,
        R.string.shuffle_mode_most_played_artists,
        R.string.shuffle_mode_most_played_artists_description
    ),
    MostPlayedAlbums(
        R.drawable.ic_album_24dp,
        R.string.shuffle_mode_most_played_albums,
        R.string.shuffle_mode_most_played_albums_description
    ),
    FavoriteSongs(
        R.drawable.ic_favorite_outline_24dp,
        R.string.shuffle_mode_favorite_songs,
        R.string.shuffle_mode_favorite_songs_description
    ),
    ForgottenSongs(
        R.drawable.ic_trending_down_24dp,
        R.string.shuffle_mode_forgotten_songs,
        R.string.shuffle_mode_forgotten_songs_description
    ),
    RecentlyAdded(
        R.drawable.ic_library_add_24dp,
        R.string.shuffle_mode_recently_added,
        R.string.shuffle_mode_recently_added_description
    ),
    Combined(
        R.drawable.ic_shuffle_on_24dp,
        R.string.shuffle_mode_combined,
        R.string.shuffle_mode_combined_description
    )
}

@Parcelize
class ExpandedSong(
    override val id: Long,
    override val data: String,
    override val title: String,
    override val trackNumber: Int,
    override val year: Int,
    override val size: Long,
    override val duration: Long,
    override val dateAdded: Long,
    override val dateModified: Long,
    override val albumId: Long,
    override val albumName: String,
    override val artistId: Long,
    override val artistName: String,
    override val albumArtistName: String?,
    override val genreName: String?,
    val playCount: Int,
    val skipCount: Int,
    val lastPlayedAt: Long,
    val isFavorite: Boolean
) : Song(
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
) {

    constructor(
        song: Song,
        playCount: Int,
        skipCount: Int,
        lastPlayedAt: Long,
        isFavorite: Boolean
    ) : this(
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
        song.genreName,
        playCount,
        skipCount,
        lastPlayedAt,
        isFavorite
    )
}