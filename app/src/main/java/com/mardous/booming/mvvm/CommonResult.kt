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

package com.mardous.booming.mvvm

import com.mardous.booming.database.PlayCountEntity
import com.mardous.booming.http.github.GitHubRelease
import com.mardous.booming.model.Suggestion

data class HandleIntentResult(val handled: Boolean, val failed: Boolean = false)

data class PlayInfoResult(
    val playCount: Int,
    val skipCount: Int,
    val lastPlayDate: Long,
    val mostPlayedTracks: List<PlayCountEntity>
)

data class SongDetailResult(
    val playCount: String? = null,
    val skipCount: String? = null,
    val lastPlayedDate: String? = null,
    val filePath: String? = null,
    val fileSize: String? = null,
    val trackLength: String? = null,
    val dateModified: String? = null,
    val audioHeader: String? = null,
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val albumYear: String? = null,
    val trackNumber: String? = null,
    val discNumber: String? = null,
    val composer: String? = null,
    val conductor: String? = null,
    val publisher: String? = null,
    val genre: String? = null,
    val replayGain: String? = null,
    val comment: String? = null
) {
    companion object {
        val Empty = SongDetailResult()
    }
}

data class SuggestedResult(val state: State = State.Idle, val data: List<Suggestion> = arrayListOf()) {

    val isLoading: Boolean
        get() = state == State.Loading

    enum class State {
        Ready,
        Loading,
        Idle
    }

    companion object {
        val Idle = SuggestedResult(State.Idle)
    }
}

data class UpdateSearchResult(
    val state: State = State.Idle,
    val data: GitHubRelease? = null,
    val executedAtMillis: Long = -1,
    val wasFromUser: Boolean = false,
    val wasExperimentalQuery: Boolean = true,
) {
    fun shouldStartNewSearchFor(fromUser: Boolean, allowExperimental: Boolean): Boolean {
        return when (state) {
            State.Idle -> true
            State.Completed, State.Failed -> fromUser || wasExperimentalQuery != allowExperimental
            State.Searching -> false
        }
    }

    enum class State {
        Idle, Searching, Completed, Failed
    }
}