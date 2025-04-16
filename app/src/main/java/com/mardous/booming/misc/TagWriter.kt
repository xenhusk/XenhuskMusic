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

package com.mardous.booming.misc

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import androidx.annotation.RequiresApi
import com.mardous.booming.extensions.files.toAudioFile
import com.mardous.booming.extensions.files.writeUsingSAF
import com.mardous.booming.extensions.media.createAlbumArtThumbFile
import com.mardous.booming.extensions.media.deleteAlbumArt
import com.mardous.booming.extensions.media.insertAlbumArt
import com.mardous.booming.extensions.resources.toJPG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagException
import org.jaudiotagger.tag.images.AndroidArtwork
import org.jaudiotagger.tag.images.Artwork
import java.io.File
import java.io.IOException

/**
 * @author Christians M. A. (mardous)
 */
object TagWriter {

    /**
     * Write tags using the specified [WriteInfo].
     *
     * This method run synchronously, therefore you should call it only from a
     * worker thread in order to avoid UI blocks.
     */
    suspend fun scan(context: Context, toBeScanned: List<String>) {
        MediaScannerConnection.scanFile(
            context,
            toBeScanned.toTypedArray(),
            null,
            withContext(Dispatchers.Main) {
                if (context is Activity)
                    UpdateToastMediaScannerCompletionListener(context, toBeScanned)
                else null
            }
        )
    }

    fun writeTagsToFiles(context: Context, info: WriteInfo): Array<String> {
        val albumArtFile = createAlbumArtThumbFile().canonicalFile
        val artwork = createArtwork(albumArtFile, info.artworkInfo)

        var wroteArtwork = false
        var deletedArtwork = false
        for (filePath in info.paths) {
            val audioFile =
                createAudioFile(File(filePath), artwork, info) { w: Boolean, d: Boolean ->
                    wroteArtwork = w
                    deletedArtwork = d
                } ?: continue

            context.writeUsingSAF(audioFile)
        }
        updateMediaStore(context, albumArtFile, info.artworkInfo, wroteArtwork, deletedArtwork)

        return info.paths.toTypedArray()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun writeTagsToFilesR(context: Context, info: WriteInfo): List<File> {
        val cacheFiles = mutableListOf<File>()

        val albumArtFile = createAlbumArtThumbFile().canonicalFile
        val artwork = createArtwork(albumArtFile, info.artworkInfo)

        var wroteArtwork = false
        var deletedArtwork = false
        for (filePath in info.paths) {
            val originFile = File(filePath)
            val cacheFile = File(context.cacheDir, originFile.name)
            cacheFiles.add(cacheFile)
            originFile.copyTo(cacheFile, true, 2048)

            val audioFile = createAudioFile(
                cacheFile,
                artwork,
                info
            ) { wrote: Boolean, deleted: Boolean ->
                wroteArtwork = wrote
                deletedArtwork = deleted
            } ?: continue

            audioFile.commit()
        }
        updateMediaStore(context, albumArtFile, info.artworkInfo, wroteArtwork, deletedArtwork)
        return cacheFiles
    }

    @Throws(IOException::class)
    private fun createArtwork(albumArtFile: File, artworkInfo: ArtworkInfo?): Artwork? {
        var artwork: Artwork? = null
        if (artworkInfo?.artwork != null) {
            artworkInfo.artwork.toJPG(100, albumArtFile.outputStream())
            artwork = AndroidArtwork.createArtworkFromFile(albumArtFile)
        }
        return artwork
    }

    @Throws(IOException::class, TagException::class)
    private fun createAudioFile(
        source: File,
        artwork: Artwork?,
        info: WriteInfo,
        artworkResult: (Boolean, Boolean) -> Unit
    ): AudioFile? {
        val audioFile = source.toAudioFile()
            ?: return null

        val tag = audioFile.tagOrCreateAndSetDefault
        if (info.values != null) {
            for ((key, newValue) in info.values) {
                val currentValue = tag.getFirst(key)
                if (currentValue != newValue) {
                    if (newValue.isNullOrEmpty()) {
                        tag.deleteField(key)
                    } else {
                        tag.setField(key, newValue)
                    }
                }
            }
        }
        if (info.artworkInfo != null) {
            if (info.artworkInfo.artwork == null) {
                tag.deleteArtworkField()
                artworkResult(false, true)
            } else if (artwork != null) {
                tag.deleteArtworkField()
                tag.setField(artwork)
                artworkResult(true, false)
            }
        }
        return audioFile
    }

    private fun updateMediaStore(
        context: Context,
        albumArtFile: File,
        artworkInfo: ArtworkInfo?,
        wroteArtwork: Boolean,
        deletedArtwork: Boolean
    ) {
        if (artworkInfo != null) {
            if (wroteArtwork) {
                context.contentResolver.insertAlbumArt(artworkInfo.albumId, albumArtFile.path)
            } else if (deletedArtwork) {
                context.contentResolver.deleteAlbumArt(artworkInfo.albumId)
            }
        }
    }

    class WriteInfo(
        val paths: List<String>,
        val values: Map<FieldKey, String?>?,
        val artworkInfo: ArtworkInfo?
    )

    class ArtworkInfo(val albumId: Long, val artwork: Bitmap?)
}