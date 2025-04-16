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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import com.mardous.booming.R
import com.mardous.booming.extensions.resources.toJPG
import com.mardous.booming.model.Song
import com.mardous.booming.providers.MediaStoreWriter
import com.mardous.booming.util.FileUtil.BOOMING_ARTWORK_DIRECTORY_NAME
import com.mardous.booming.util.FileUtil.imagesDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.Locale
import kotlin.coroutines.CoroutineContext

/**
 * @author Christians M. A. (mardous)
 */
class CoverSaverCoroutine(val context: Context, private val uiScope: CoroutineScope, private val ioContext: CoroutineContext)
    : KoinComponent {

    private val mediaStoreWriter: MediaStoreWriter by inject()
    private val artworkFileName: String
        get() = String.format(Locale.ENGLISH, "Artwork_%d.jpg", System.currentTimeMillis())

    fun saveArtwork(
        song: Song?,
        onPreExecute: () -> Unit,
        onSuccess: (Uri, String) -> Unit,
        onError: (String?) -> Unit
    ) {
        uiScope.launch {
            onPreExecute()

            val resultInfo = withContext(ioContext) {
                doHeavyWork(song)
            }

            if (resultInfo.resultCode == ResultInfo.RESULT_SUCCESS) {
                // There will be a file only if the device
                // is not running Android 10 or up
                if (resultInfo.file != null) {
                    MediaScannerConnection.scanFile(context,
                        arrayOf(resultInfo.file.absolutePath),
                        arrayOf(ARTWORK_MIME_TYPE)) { _, uri -> onSuccess(uri, ARTWORK_MIME_TYPE) }
                } else if (resultInfo.uri != null) {
                    onSuccess(resultInfo.uri, ARTWORK_MIME_TYPE)
                }
            } else {
                val errorMessage = if (resultInfo.resultCode == ResultInfo.RESULT_NO_ARTWORK)
                    context.getString(R.string.save_artwork_no_artwork) else context.getString(R.string.save_artwork_error)
                onError(errorMessage)
            }
        }
    }

    private fun getArtworkBitmap(song: Song?): Bitmap? {
        if (song == null || song.id == -1L) return null
        val bytes: ByteArray?
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, song.mediaStoreUri)
            bytes = retriever.embeddedPicture
        } finally {
            retriever.release()
        }

        return if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
    }

    private fun doHeavyWork(song: Song?): ResultInfo {
        if (song != null) {
            val bitmap = getArtworkBitmap(song)
                ?: return ResultInfo(null, null, ResultInfo.RESULT_NO_ARTWORK)

            val request = MediaStoreWriter.Request.forImage(artworkFileName, BOOMING_ARTWORK_DIRECTORY_NAME, ARTWORK_MIME_TYPE)
            val result = mediaStoreWriter.toMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, request) { outputStream ->
                bitmap.toJPG(stream = outputStream)
            }

            when (result.resultCode) {
                MediaStoreWriter.Result.Code.SUCCESS -> return ResultInfo(null,
                    result.uri,
                    ResultInfo.RESULT_SUCCESS)
                MediaStoreWriter.Result.Code.NO_SCOPED_STORAGE -> return doHeavyWorkLegacy(bitmap)
            }
        }
        return ResultInfo(null, null, ResultInfo.RESULT_ERROR)
    }

    private fun doHeavyWorkLegacy(bitmap: Bitmap): ResultInfo {
        val artworkFile = mediaStoreWriter.toFile(imagesDirectory(BOOMING_ARTWORK_DIRECTORY_NAME), artworkFileName) { outputStream ->
            bitmap.toJPG(stream = outputStream)
        }
        if (artworkFile != null) {
            return ResultInfo(artworkFile, null, ResultInfo.RESULT_SUCCESS)
        }
        return ResultInfo(null, null, ResultInfo.RESULT_ERROR)
    }

    private class ResultInfo(val file: File?, val uri: Uri?, val resultCode: Int) {
        companion object {
            const val RESULT_SUCCESS = 0
            const val RESULT_ERROR = 1
            const val RESULT_NO_ARTWORK = 2
        }
    }

    companion object {
        private const val ARTWORK_MIME_TYPE = "image/jpeg"
    }
}