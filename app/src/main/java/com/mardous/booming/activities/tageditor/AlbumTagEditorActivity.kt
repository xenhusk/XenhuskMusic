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

package com.mardous.booming.activities.tageditor

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.mardous.booming.R
import com.mardous.booming.databinding.TagEditorAlbumFieldBinding
import com.mardous.booming.extensions.files.toBitmap
import com.mardous.booming.extensions.glide.DEFAULT_ALBUM_IMAGE
import com.mardous.booming.extensions.resources.getDrawableCompat
import com.mardous.booming.extensions.resources.getResized
import com.mardous.booming.extensions.showToast
import com.mardous.booming.extensions.webSearch
import com.mardous.booming.http.Result
import com.mardous.booming.http.lastfm.getLargestImageUrl
import org.jaudiotagger.tag.FieldKey
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.util.EnumMap

/**
 * @author Christians M. A. (mardous)
 */
class AlbumTagEditorActivity : AbsTagEditorActivity() {

    override val viewModel by viewModel<TagEditorViewModel> {
        parametersOf(getExtraId(), null)
    }

    private lateinit var albumBinding: TagEditorAlbumFieldBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.getArtwork().observe(this) { artwork ->
            setImageBitmap(artwork?.toBitmap()?.getResized(1080))
        }
        viewModel.getTags().observe(this) { tagResult ->
            albumBinding.album.setText(tagResult.album)
            albumBinding.albumArtist.setText(tagResult.albumArtist)
            albumBinding.conductor.setText(tagResult.conductor)
            albumBinding.publisher.setText(tagResult.publisher)
            albumBinding.genre.setText(tagResult.genre)
            albumBinding.year.setText(tagResult.year)
            albumBinding.trackTotal.setText(tagResult.trackTotal)
            albumBinding.discNumber.setText(tagResult.discNumber)
            albumBinding.discTotal.setText(tagResult.discTotal)
        }
        viewModel.loadAlbumTags()
    }

    override fun onWrapFieldViews(inflater: LayoutInflater, parent: ViewGroup) {
        albumBinding = TagEditorAlbumFieldBinding.inflate(inflater, parent, true).apply {
            genreTextInputLayout.setEndIconOnClickListener {
                selectDefaultGenre()
            }
        }
    }

    override fun onDefaultGenreSelection(genre: String) {
        val genreContent = albumBinding.genre.text?.toString()
        if (!genreContent.isNullOrBlank()) {
            albumBinding.genre.setText(String.format("%s;%s", genreContent, genre))
        } else albumBinding.genre.setText(genre)
    }

    override fun getDefaultPlaceholder(): Drawable = getDrawableCompat(DEFAULT_ALBUM_IMAGE)!!

    override fun searchLastFMImage() {
        val albumTitleStr = albumBinding.album.text?.toString()
        val albumArtistNameStr = albumBinding.albumArtist.text?.toString()
        if (albumArtistNameStr.isNullOrBlank() || albumTitleStr.isNullOrBlank()) {
            showToast(resources.getString(R.string.album_or_artist_empty))
            return
        }
        viewModel.getAlbumInfo(albumArtistNameStr, albumTitleStr).observe(this) { result ->
            if (result is Result.Success && result.data.album != null) {
                val url = result.data.album.image.getLargestImageUrl()
                if (!url.isNullOrEmpty()) {
                    loadImageFromUrl(url)
                } else {
                    showToast(R.string.could_not_download_album_cover)
                }
            } else {
                showToast(R.string.could_not_download_album_cover)
            }
        }
    }

    override fun searchOnlineImage() {
        webSearch(albumBinding.album.text.toString(), albumBinding.albumArtist.text.toString())
    }

    override fun restoreImage() {
        viewModel.loadArtwork()
    }

    override val fieldKeyValueMap: EnumMap<FieldKey, String?>
        get() = EnumMap<FieldKey, String?>(FieldKey::class.java).apply {
            put(FieldKey.ALBUM, albumBinding.album.text?.toString())
            put(FieldKey.ALBUM_ARTIST, albumBinding.albumArtist.text?.toString())
            put(FieldKey.CONDUCTOR, albumBinding.conductor.text?.toString())
            put(FieldKey.RECORD_LABEL, albumBinding.publisher.text?.toString())
            put(FieldKey.GENRE, albumBinding.genre.text?.toString())
            put(FieldKey.YEAR, albumBinding.year.text?.toString())
            put(FieldKey.TRACK_TOTAL, albumBinding.trackTotal.text?.toString())
            put(FieldKey.DISC_NO, albumBinding.discNumber.text?.toString())
            put(FieldKey.DISC_TOTAL, albumBinding.discTotal.text?.toString())
        }

    override fun getSongPaths(): List<String> = viewModel.getPaths()

    override fun getSongUris(): List<Uri> = viewModel.getUris()

    override fun getArtworkId(): Long = viewModel.getArtworkId()

}