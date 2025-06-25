package com.mardous.booming.viewmodels.yeardetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.model.ReleaseYear
import com.mardous.booming.repository.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class YearDetailViewModel(private val repository: Repository, private val year: Int) : ViewModel() {

    private val _year = MutableLiveData<ReleaseYear>()
    fun getYear(): LiveData<ReleaseYear> = _year

    init {
        loadDetail()
    }

    fun loadDetail() = viewModelScope.launch(Dispatchers.IO) {
        _year.postValue(repository.yearById(year))
    }
}