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

package com.mardous.booming.http.github

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class GitHubService(private val client: HttpClient) {

    suspend fun latestRelease(user: String = DEFAULT_USER, repo: String = DEFAULT_REPO) =
        client.get("https://api.github.com/repos/$user/$repo/releases/latest")
            .body<GitHubRelease>()

    companion object {
        const val DEFAULT_USER = "mardous"
        const val DEFAULT_REPO = "BoomingMusic"
    }
}