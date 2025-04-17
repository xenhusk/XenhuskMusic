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
import com.mardous.booming.databinding.TagEditorSongFieldBinding
import com.mardous.booming.extensions.files.toBitmap
import com.mardous.booming.extensions.glide.DEFAULT_SONG_IMAGE
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
class SongTagEditorActivity : AbsTagEditorActivity() {

    override val viewModel by viewModel<TagEditorViewModel> {
        parametersOf(getExtraId(), null)
    }

    private var _songBinding: TagEditorSongFieldBinding? = null
    private val songBinding get() = _songBinding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.getArtwork().observe(this) { artwork ->
            setImageBitmap(artwork?.toBitmap()?.getResized(1080))
        }
        viewModel.getTags().observe(this) { tagResult ->
            songBinding.title.setText(tagResult.title)
            songBinding.album.setText(tagResult.album)
            songBinding.artist.setText(tagResult.artist)
            songBinding.albumArtist.setText(tagResult.albumArtist)
            songBinding.composer.setText(tagResult.composer)
            songBinding.conductor.setText(tagResult.conductor)
            songBinding.publisher.setText(tagResult.publisher)
            songBinding.genre.setText(tagResult.genre)
            songBinding.year.setText(tagResult.year)
            songBinding.trackNumber.setText(tagResult.trackNumber)
            songBinding.trackTotal.setText(tagResult.trackTotal)
            songBinding.discNumber.setText(tagResult.discNumber)
            songBinding.discTotal.setText(tagResult.discTotal)
            songBinding.lyrics.setText(tagResult.lyrics)
            songBinding.lyricist.setText(tagResult.lyricist)
            songBinding.comment.setText(tagResult.comment)
        }
        viewModel.loadSongTags()
    }

    override fun onWrapFieldViews(inflater: LayoutInflater, parent: ViewGroup) {
        _songBinding = TagEditorSongFieldBinding.inflate(inflater, parent, true).apply {
            genreTextInputLayout.setEndIconOnClickListener {
                selectDefaultGenre()
            }
        }
    }

    override fun onDefaultGenreSelection(genre: String) {
        val genreContent = songBinding.genre.text?.toString()
        if (!genreContent.isNullOrBlank()) {
            songBinding.genre.setText(String.format("%s;%s", genreContent, genre))
        } else songBinding.genre.setText(genre)
    }

    override fun getDefaultPlaceholder(): Drawable = getDrawableCompat(DEFAULT_SONG_IMAGE)!!

    override fun searchLastFMImage() {
        val songTitleStr = songBinding.title.text?.toString()
        var albumArtistNameStr = songBinding.albumArtist.text?.toString()
        if (albumArtistNameStr.isNullOrBlank()) {
            albumArtistNameStr = songBinding.artist.text?.toString()
        }
        if (songTitleStr.isNullOrBlank() || albumArtistNameStr.isNullOrBlank()) {
            showToast(resources.getString(R.string.album_or_artist_empty))
            return
        }
        viewModel.getTrackInfo(albumArtistNameStr, songTitleStr).observe(this) { result ->
            if (result is Result.Success && result.data.track != null) {
                val url = result.data.track.album?.image?.getLargestImageUrl()
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
        webSearch(songBinding.title.text.toString(), songBinding.artist.text.toString())
    }

    override fun restoreImage() {
        viewModel.loadArtwork()
    }

    override val fieldKeyValueMap: EnumMap<FieldKey, String?>
        get() = EnumMap<FieldKey, String?>(FieldKey::class.java).apply {
            put(FieldKey.TITLE, songBinding.title.text?.toString())
            put(FieldKey.ALBUM, songBinding.album.text?.toString())
            put(FieldKey.ARTIST, songBinding.artist.text?.toString())
            put(FieldKey.ALBUM_ARTIST, songBinding.albumArtist.text?.toString())
            put(FieldKey.COMPOSER, songBinding.composer.text?.toString())
            put(FieldKey.CONDUCTOR, songBinding.conductor.text?.toString())
            put(FieldKey.RECORD_LABEL, songBinding.publisher.text?.toString())
            put(FieldKey.GENRE, songBinding.genre.text?.toString())
            put(FieldKey.YEAR, songBinding.year.text?.toString())
            put(FieldKey.TRACK, songBinding.trackNumber.text?.toString())
            put(FieldKey.TRACK_TOTAL, songBinding.trackTotal.text?.toString())
            put(FieldKey.DISC_NO, songBinding.discNumber.text?.toString())
            put(FieldKey.DISC_TOTAL, songBinding.discTotal.text?.toString())
            put(FieldKey.LYRICS, songBinding.lyrics.text?.toString())
            put(FieldKey.LYRICIST, songBinding.lyricist.text?.toString())
            put(FieldKey.COMMENT, songBinding.comment.text?.toString())
        }

    override fun getSongPaths(): List<String> = viewModel.getPaths()

    override fun getSongUris(): List<Uri> = viewModel.getUris()

    override fun getArtworkId(): Long = viewModel.getArtworkId()

    override fun onDestroy() {
        super.onDestroy()
        _songBinding = null
    }
}