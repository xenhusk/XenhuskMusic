package com.mardous.booming.viewmodels.player.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.mardous.booming.extensions.resources.toJPG
import com.mardous.booming.model.Song
import com.mardous.booming.providers.MediaStoreWriter
import com.mardous.booming.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * @author Christians M. A. (mardous)
 */
class SaveCoverWorker(
    private val context: Context,
    private val mediaStoreWriter: MediaStoreWriter
) {

    private val artworkFileName: String
        get() = "Artwork_${System.currentTimeMillis()}.jpg"

    suspend fun saveArtwork(song: Song) = withContext(Dispatchers.IO) {
        val bitmap = extractBitmap(song)
        if (bitmap == null) {
            null
        } else {
            saveBitmap(bitmap) ?: if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val savedFile = saveBitmapAsFile(bitmap)
                if (savedFile != null) {
                    val scannedUri = runCatching {
                        suspendCancellableCoroutine { continuation ->
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(savedFile.absolutePath),
                                arrayOf(ARTWORK_MIME_TYPE)
                            ) { _, uri ->
                                continuation.resume(uri)
                            }
                        }
                    }
                    scannedUri.getOrNull()
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    private fun extractBitmap(song: Song): Bitmap? {
        if (song.id == Song.emptySong.id)
            return null

        val bytes: ByteArray?
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, song.mediaStoreUri)
            bytes = retriever.embeddedPicture
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't extract bitmap from ${song.mediaStoreUri}", e)
            return null
        } finally {
            retriever.release()
        }
        return bytes?.let {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    private fun saveBitmap(bitmap: Bitmap): Uri? {
        val request = MediaStoreWriter.Request.forImage(
            displayName = artworkFileName,
            subFolder = FileUtil.BOOMING_ARTWORK_DIRECTORY_NAME,
            imageMimeType = ARTWORK_MIME_TYPE
        )
        val result = mediaStoreWriter.toMediaStore(
            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            request = request
        ) { outputStream ->
            bitmap.toJPG(stream = outputStream)
        }
        if (result.resultCode == MediaStoreWriter.Result.Code.SUCCESS) {
            return result.uri
        }
        return null
    }

    private fun saveBitmapAsFile(bitmap: Bitmap): File? {
        return mediaStoreWriter.toFile(
            directory = FileUtil.imagesDirectory(FileUtil.BOOMING_ARTWORK_DIRECTORY_NAME),
            fileName = artworkFileName
        ) { outputStream ->
            bitmap.toJPG(stream = outputStream)
        }
    }

    companion object {
        private const val TAG = "SaveCoverWorker"
        private const val ARTWORK_MIME_TYPE = "image/jpeg"
    }
}