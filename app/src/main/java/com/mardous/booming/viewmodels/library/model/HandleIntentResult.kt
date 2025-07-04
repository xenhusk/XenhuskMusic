package com.mardous.booming.viewmodels.library.model

import com.mardous.booming.model.Song

data class HandleIntentResult(
    val handled: Boolean,
    val songs: List<Song> = emptyList(),
    val position: Int = 0,
    val failed: Boolean = false
)