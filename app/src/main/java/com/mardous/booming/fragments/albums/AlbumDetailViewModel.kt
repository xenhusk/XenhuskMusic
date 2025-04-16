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

package com.mardous.booming.fragments.albums

import androidx.lifecycle.*
import com.mardous.booming.http.Result
import com.mardous.booming.http.lastfm.LastFmAlbum
import com.mardous.booming.model.Album
import com.mardous.booming.repository.Repository
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

class AlbumDetailViewModel(private val repository: Repository, private val albumId: Long) : ViewModel() {

    private val _albumDetail = MutableLiveData<Album>()

    fun getAlbumDetail(): LiveData<Album> = _albumDetail

    fun getAlbum() = getAlbumDetail().value ?: Album.empty

    fun loadAlbumDetail() = viewModelScope.launch(IO) {
        _albumDetail.postValue(repository.albumById(albumId))
    }

    fun getSimilarAlbums(album: Album): LiveData<List<Album>> = liveData(IO) {
        repository.similarAlbums(album).let {
            if (it.isNotEmpty()) emit(it)
        }
    }

    fun getAlbumWiki(album: Album, lang: String?): LiveData<Result<LastFmAlbum>> = liveData(IO) {
        emit(Result.Loading)
        emit(repository.albumInfo(album.albumArtistName ?: album.artistName, album.name, lang))
    }
}