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

package com.mardous.booming.dialogs.playlists

import android.content.DialogInterface
import com.mardous.booming.R
import com.mardous.booming.dialogs.InputDialog
import com.mardous.booming.extensions.EXTRA_SONGS
import com.mardous.booming.extensions.extraNotNull
import com.mardous.booming.extensions.showToast
import com.mardous.booming.extensions.withArgs
import com.mardous.booming.fragments.LibraryViewModel
import com.mardous.booming.model.Song
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class CreatePlaylistDialog : InputDialog() {

    private val libraryViewModel: LibraryViewModel by activityViewModel()
    private val songs: List<Song> by extraNotNull(EXTRA_SONGS, arrayListOf())

    private var callback: PlaylistCreatedCallback? = null

    override fun inputConfig(): InputConfig {
        val message = if (songs.isEmpty()) {
            getString(R.string.create_a_playlist)
        } else if (songs.size == 1) {
            getString(R.string.create_a_playlist_with_one_song)
        } else {
            getString(R.string.create_a_playlist_with_x_songs, songs.size)
        }
        return Builder(requireContext())
            .title(R.string.new_playlist_title)
            .message(message)
            .hint(R.string.playlist_name_empty)
            .positiveText(R.string.create_action)
            .createConfig()
    }

    override fun processInput(
        dialog: DialogInterface,
        text: String?,
        isChecked: Boolean
    ): Boolean {
        val playlistName = text?.trim()
        if (!playlistName.isNullOrBlank()) {
            libraryViewModel.addToPlaylist(playlistName, songs).observe(this) {
                if (it.isWorking)
                    return@observe

                if (it.playlistCreated) {
                    showToast(getString(R.string.created_playlist_x, it.playlistName))
                    callback?.playlistCreated()
                } else {
                    showToast(R.string.could_not_create_playlist)
                }
                dialog.dismiss()
            }
        } else {
            inputLayout().error = getString(R.string.playlist_name_cannot_be_empty)
        }
        return false
    }

    fun callback(playlistCreatedCallback: PlaylistCreatedCallback) =
        apply { this.callback = playlistCreatedCallback }

    interface PlaylistCreatedCallback {
        fun playlistCreated()
    }

    companion object {
        fun create(song: Song) = create(listOf(song))

        fun create(songs: List<Song>? = null): CreatePlaylistDialog {
            if (songs.isNullOrEmpty()) {
                return CreatePlaylistDialog()
            }
            return CreatePlaylistDialog().withArgs {
                putParcelableArrayList(EXTRA_SONGS, ArrayList(songs))
            }
        }
    }
}