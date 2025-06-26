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

package com.mardous.booming.service

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.mardous.booming.androidauto.AutoMediaIDHelper
import com.mardous.booming.database.fromHistoryToSongs
import com.mardous.booming.database.toSongs
import com.mardous.booming.extensions.media.indexOfSong
import com.mardous.booming.helper.UriSongResolver
import com.mardous.booming.model.Song
import com.mardous.booming.providers.databases.PlaybackQueueStore
import com.mardous.booming.repository.Repository
import com.mardous.booming.service.MusicService.Companion.FAST_FORWARD_THRESHOLD
import com.mardous.booming.service.MusicService.Companion.REWIND_THRESHOLD
import com.mardous.booming.service.constants.SessionCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MediaSessionCallback(private val musicService: MusicService, private val coroutineScope: CoroutineScope) :
    MediaSessionCompat.Callback(), KoinComponent {

    private val repository by inject<Repository>()
    private val uriSongResolver by inject<UriSongResolver>()

    override fun onPrepare() {
        super.onPrepare()
        if (musicService.getCurrentSong() != Song.emptySong) {
            musicService.restoreState(::onPlay)
        }
    }

    override fun onPlay() {
        super.onPlay()
        if (musicService.getCurrentSong() != Song.emptySong) {
            musicService.play()
        }
    }

    override fun onPause() {
        super.onPause()
        musicService.pause()
    }

    override fun onSkipToNext() {
        super.onSkipToNext()
        musicService.playNextSong(true)
    }

    override fun onFastForward() {
        super.onFastForward()
        val currentPosition = musicService.getSongProgressMillis()
        val songDuration = musicService.getSongDurationMillis()
        musicService.seek((currentPosition + FAST_FORWARD_THRESHOLD).coerceAtMost(songDuration))
    }

    override fun onRewind() {
        super.onRewind()
        val currentPosition = musicService.getSongProgressMillis()
        musicService.seek((currentPosition - REWIND_THRESHOLD).coerceAtLeast(0))
    }

    override fun onSkipToPrevious() {
        super.onSkipToPrevious()
        musicService.back(true)
    }

    override fun onStop() {
        musicService.quit()
    }

    override fun onSeekTo(pos: Long) {
        musicService.seek(pos.toInt())
    }

    override fun onCustomAction(action: String, extras: Bundle) {
        when (action) {
            SessionCommand.CYCLE_REPEAT -> {
                musicService.cycleRepeatMode()
                musicService.updateMediaSessionPlaybackState()
            }

            SessionCommand.TOGGLE_SHUFFLE -> {
                musicService.toggleShuffle()
                musicService.updateMediaSessionPlaybackState()
            }

            SessionCommand.TOGGLE_FAVORITE -> {
                musicService.toggleFavorite()
            }

            else -> Log.d("MediaSession", "Unsupported action: $action")
        }
    }

    override fun onPlayFromMediaId(mediaId: String, extras: Bundle) {
        super.onPlayFromMediaId(mediaId, extras)

        val musicId = AutoMediaIDHelper.extractMusicID(mediaId)
        val itemId = musicId?.toLong() ?: -1

        val songs = ArrayList<Song>()

        coroutineScope.launch(IO) {
            when (val category = AutoMediaIDHelper.extractCategory(mediaId)) {
                AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM -> {
                    val album = repository.albumById(itemId)
                    songs.addAll(album.songs)
                    musicService.openQueue(songs, 0, true)
                }

                AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_ARTIST -> {
                    val artist = repository.artistById(itemId)
                    songs.addAll(artist.songs)
                    musicService.openQueue(songs, 0, true)
                }

                AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_PLAYLIST -> {
                    val playlist = repository.devicePlaylist(itemId)
                    songs.addAll(playlist.getSongs())
                    musicService.openQueue(songs, 0, true)
                }

                AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_HISTORY,
                AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_TOP_TRACKS,
                AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_QUEUE -> {
                    val tracks: List<Song> = when (category) {
                        AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_HISTORY -> {
                            repository.historySongs().fromHistoryToSongs()
                        }

                        AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_TOP_TRACKS -> {
                            repository.topPlayedSongs()
                        }

                        else -> {
                            PlaybackQueueStore.getInstance(musicService).savedOriginalPlayingQueue
                        }
                    }
                    songs.addAll(tracks)
                    var songIndex = tracks.indexOfSong(itemId)
                    if (songIndex == -1) {
                        songIndex = 0
                    }
                    musicService.openQueue(songs, songIndex, true)
                }

                AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_SHUFFLE -> {
                    val allSongs = repository.allSongs().shuffled()
                    musicService.openQueue(allSongs, 0, true)
                }
            }
            musicService.play()
        }
    }

    /**
     * Inspired by https://developer.android.com/guide/topics/media-apps/interacting-with-assistant
     */
    @Suppress("DEPRECATION")
    override fun onPlayFromSearch(query: String, extras: Bundle) {
        coroutineScope.launch(IO) {
            val songs = ArrayList<Song>()
            if (query.isEmpty()) {
                songs.addAll(repository.allSongs())
            } else {
                // Build a queue based on songs that match "query" or "extras" param
                val mediaFocus = extras.getString(MediaStore.EXTRA_MEDIA_FOCUS)
                when (mediaFocus) {
                    MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> {
                        val artistQuery = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)
                        if (artistQuery != null) {
                            val artists = repository.searchArtists(artistQuery)
                            if (artists.isNotEmpty()) {
                                songs.addAll(artists.first().songs)
                            }
                        }
                    }
                    MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> {
                        val albumQuery = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM)
                        if (albumQuery != null) {
                            val albums = repository.searchAlbums(albumQuery)
                            if (albums.isNotEmpty()) {
                                songs.addAll(albums.first().songs)
                            }
                        }
                    }
                    MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE -> {
                        val playlistQuery = extras.getString(MediaStore.EXTRA_MEDIA_PLAYLIST)
                        if (playlistQuery != null) {
                            val playlists = repository.searchPlaylists(playlistQuery)
                            if (playlists.isNotEmpty()) {
                                songs.addAll(playlists.first().songs.toSongs())
                            }
                        }
                    }
                    MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> {
                        val genresQuery = extras.getString(MediaStore.EXTRA_MEDIA_GENRE)
                        if (genresQuery != null) {
                            val genres = repository.searchGenres(genresQuery)
                            if (genres.isNotEmpty()) {
                                songs.addAll(
                                    genres.flatMap { repository.songsByGenre(it.id) }
                                )
                            }
                        }
                    }
                }
            }

            // Search by title
            if (songs.isEmpty()) {
                songs.addAll(repository.searchSongs(query))
            }

            musicService.openQueue(songs, 0, true)
            musicService.play()
        }
    }

    override fun onPlayFromUri(uri: Uri, extras: Bundle) {
        coroutineScope.launch(IO) {
            val songs = uriSongResolver.resolve(uri)
            if (songs.isNotEmpty()) {
                musicService.openQueue(songs, 0, true)
                musicService.play()
            }
        }
    }
}