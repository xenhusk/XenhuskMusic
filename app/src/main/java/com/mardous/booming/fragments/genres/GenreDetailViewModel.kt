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

package com.mardous.booming.fragments.genres

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.model.Genre
import com.mardous.booming.model.Song
import com.mardous.booming.repository.Repository
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

class GenreDetailViewModel(private val repository: Repository, private val genre: Genre) : ViewModel() {

    private val _genreSongs = MutableLiveData<List<Song>>()

    fun getSongs(): LiveData<List<Song>> = _genreSongs

    init {
        loadGenreSongs()
    }

    fun loadGenreSongs() = viewModelScope.launch(IO) {
        val songs = repository.songsByGenre(genre.id)
        _genreSongs.postValue(songs)
    }
}