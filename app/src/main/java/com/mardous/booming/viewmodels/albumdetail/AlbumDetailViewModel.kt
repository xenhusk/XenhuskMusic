package com.mardous.booming.viewmodels.albumdetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.mardous.booming.http.Result
import com.mardous.booming.http.lastfm.LastFmAlbum
import com.mardous.booming.model.Album
import com.mardous.booming.repository.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlbumDetailViewModel(private val repository: Repository, private val albumId: Long) : ViewModel() {

    private val _albumDetail = MutableLiveData<Album>()

    fun getAlbumDetail(): LiveData<Album> = _albumDetail

    fun getAlbum() = getAlbumDetail().value ?: Album.Companion.empty

    fun loadAlbumDetail() = viewModelScope.launch(Dispatchers.IO) {
        _albumDetail.postValue(repository.albumById(albumId))
    }

    fun getSimilarAlbums(album: Album): LiveData<List<Album>> = liveData(Dispatchers.IO) {
        repository.similarAlbums(album).let {
            if (it.isNotEmpty()) emit(it)
        }
    }

    fun getAlbumWiki(album: Album, lang: String?): LiveData<Result<LastFmAlbum>> =
        liveData(Dispatchers.IO) {
            emit(Result.Loading)
            emit(repository.albumInfo(album.albumArtistName ?: album.artistName, album.name, lang))
        }
}