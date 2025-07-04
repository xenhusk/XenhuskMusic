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

package com.mardous.booming.viewmodels.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.extensions.media.indexOfSong
import com.mardous.booming.model.Song
import com.mardous.booming.repository.Repository
import com.mardous.booming.search.SearchFilter
import com.mardous.booming.search.SearchQuery
import com.mardous.booming.util.Preferences
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: Repository) : ViewModel() {

    private val searchQuery = MutableStateFlow(SearchQuery())

    private val _searchFilter = MutableStateFlow<SearchFilter?>(null)
    val searchFilter: StateFlow<SearchFilter?> = _searchFilter.asStateFlow()

    private val _searchResult = MutableStateFlow<List<Any>>(emptyList())
    val searchResult: StateFlow<List<Any>> = _searchResult.asStateFlow()

    private val _queueFlow = MutableSharedFlow<Pair<List<Song>, Int>>(replay = 0)
    val queueFlow: SharedFlow<Pair<List<Song>, Int>> = _queueFlow

    init {
        @OptIn(FlowPreview::class)
        combine(searchQuery, searchFilter) { query, filter -> query to filter }
            .debounce(300)
            .distinctUntilChanged()
            .onEach { (query, filter) ->
                val result = repository.search(query, filter)
                _searchResult.value = result
            }
            .flowOn(IO)
            .launchIn(viewModelScope)
    }

    fun updateFilter(filter: SearchFilter?) {
        _searchFilter.value = filter
    }

    fun updateQuery(
        mode: SearchQuery.FilterMode? = searchQuery.value.filterMode,
        query: String? = searchQuery.value.searched
    ) {
        searchQuery.value = searchQuery.value.copy(filterMode = mode, searched = query)
    }

    fun songClick(song: Song, results: List<Any>) = viewModelScope.launch(IO) {
        val songs = results.filterIsInstance<Song>()
        val queue = if (Preferences.searchAutoQueue) songs else listOf(song)
        val startPos = if (Preferences.searchAutoQueue) {
            songs.indexOfSong(song.id).coerceAtLeast(0)
        } else 0
        _queueFlow.emit(queue to startPos)
    }

    fun refresh() {
        searchQuery.value = searchQuery.value.copy(timestamp = System.currentTimeMillis())
    }
}