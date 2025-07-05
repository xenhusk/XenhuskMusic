package com.mardous.booming.service.queue

import com.mardous.booming.model.Song
import kotlinx.parcelize.Parcelize

data class QueuePosition(val value: Int, val passive: Boolean, val play: Boolean) {
    companion object {
        val Unspecified = QueuePosition(-1, passive = true, play = false)

        fun initial(position: Int, play: Boolean) = QueuePosition(position, passive = !play, play = play)

        fun passive(position: Int) = QueuePosition(position, passive = true, play = false)

        fun prepare(position: Int) = QueuePosition(position, passive = false, play = false)

        fun play(position: Int = 0) = QueuePosition(position, passive = false, play = true)
    }
}

class StopPosition(val value: Int, val fromUser: Boolean) {
    companion object {
        const val INFINITE = -1
        val Unspecified = StopPosition(INFINITE, false)
    }
}

@Parcelize
class QueueSong(
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
    var isUpcoming: Boolean
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

    constructor(song: Song, isUpcoming: Boolean) : this(
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
        isUpcoming
    )
}

fun Song.toQueueSong(upcoming: Boolean = false) = QueueSong(this, upcoming)

fun List<Song>.toQueueSongs(upcoming: Boolean = false) = map { QueueSong(it, upcoming) }