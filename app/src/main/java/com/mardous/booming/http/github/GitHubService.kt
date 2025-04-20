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
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse

class GitHubService(private val client: HttpClient, private val authToken: String? = null) {

    private suspend fun get(url: String): HttpResponse {
        return client.get(url) {
            authToken?.let {
                headers { append("Authorization", "token $it") }
            }
        }
    }

    private suspend fun fetchStableRelease(user: String, repo: String): GitHubRelease =
        get("https://api.github.com/repos/$user/$repo/releases/latest").body()

    private suspend fun fetchAllReleases(user: String, repo: String): List<GitHubRelease> =
        get("https://api.github.com/repos/$user/$repo/releases").body()

    suspend fun latestRelease(user: String = DEFAULT_USER, repo: String = DEFAULT_REPO, isStable: Boolean = true): GitHubRelease {
        return if (isStable) {
            fetchStableRelease(user, repo)
        } else {
            val allReleases = fetchAllReleases(user, repo)
                .filter { it.isPrerelease }
                .sortedByDescending { it.publishedAt }
            allReleases.firstOrNull()
                ?: throw ReleaseNotFoundException("No prerelease found for $user/$repo")
        }
    }

    class ReleaseNotFoundException(message: String) : Exception(message)

    companion object {
        const val DEFAULT_USER = "mardous"
        const val DEFAULT_REPO = "BoomingMusic"
    }
}