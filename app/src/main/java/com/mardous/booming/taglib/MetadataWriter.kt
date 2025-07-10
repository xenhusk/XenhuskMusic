package com.mardous.booming.taglib

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.util.Log
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.mardous.booming.extensions.media.createAlbumArtThumbFile
import com.mardous.booming.extensions.media.deleteAlbumArt
import com.mardous.booming.extensions.media.insertAlbumArt
import com.mardous.booming.model.Album
import com.mardous.booming.model.Artist
import com.mardous.booming.model.Song
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayOutputStream
import java.io.File

class MetadataWriter : KoinComponent {

    private val contentResolver: ContentResolver by inject()

    private var properties = mapOf<String, String?>()
    private var pictureBitmap: Bitmap? = null
    private var pictureDeleted = false

    fun picture(pictureBitmap: Bitmap?) {
        this.pictureBitmap = pictureBitmap
        this.pictureDeleted = false
    }

    fun pictureDeleted(pictureDeleted: Boolean) {
        this.pictureDeleted = pictureDeleted
        this.pictureBitmap = if (pictureDeleted) null else pictureBitmap
    }

    fun propertyMap(propertyMap: Map<String, String?>) {
        this.properties = propertyMap
    }

    suspend fun write(context: Context, target: EditTarget) = withContext(IO) {
        val results = mutableListOf<WriteResult>()
        val picture = createPicture(target)
        val pictureThumbFile = createPictureThumbFile(picture)
        for (content in target.contents) {
            val result = runCatching {
                contentResolver.openFileDescriptor(content.uri, "rw")?.use { fd ->
                    WriteResult(
                        content = content,
                        pictureResult = writePicture(picture, fd),
                        propertiesResult = writePropertyMap(fd)
                    )
                } ?: WriteResult(content)
            }
            if (result.isSuccess) {
                results.add(result.getOrThrow())
                contentResolver.notifyChange(content.uri, null)
            } else {
                Log.e("MetadataWriter", "Failed to write metadata for ${content.uri}", result.exceptionOrNull())
            }
        }
        if (target.hasArtwork) {
            val wrotePicture = results.any { it.pictureResult == Result.Wrote }
            val deletedPicture = results.any { it.pictureResult == Result.Deleted }
            if (wrotePicture) {
                if (pictureThumbFile != null) {
                    contentResolver.insertAlbumArt(target.artworkId, pictureThumbFile.path)
                }
            } else if (deletedPicture) {
                contentResolver.deleteAlbumArt(target.artworkId)
            }
        }
        results.filter { it.isSuccess }
            .map { it -> it.content }
            .also {
                val paths = it.map { content -> content.path }.toTypedArray()
                if (paths.isNotEmpty()) {
                    MediaScannerConnection.scanFile(context, paths, null, null)
                }
            }
    }

    private fun createPicture(target: EditTarget): Picture? {
        val pictureBitmap = this.pictureBitmap
        if (target.artworkId == -1L || pictureBitmap == null) {
            return null
        }
        val byteArray = ByteArrayOutputStream(pictureBitmap.byteCount).use {
            pictureBitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            it.toByteArray()
        }
        return Picture(
            data = byteArray,
            description = "Embedded Front Cover - Booming Music",
            pictureType = "Front Cover",
            mimeType = "image/jpeg"
        )
    }

    private fun createPictureThumbFile(picture: Picture?): File? {
        if (picture != null) {
            val result = createAlbumArtThumbFile().canonicalFile
            try {
                return result.also { it.writeBytes(picture.data) }
            } catch (e: IOException) {
                Log.e("MetadataWriter", "Failed to create album thumb file", e)
                try {
                    if (result.delete()) {
                        Log.v("MetadataWriter", "Deleted empty album thumb file")
                    }
                } catch (e: Exception) {
                    Log.e("MetadataWriter", "Failed to delete empty album thumb file", e)
                }
            }
        }
        return null
    }

    private fun writePicture(picture: Picture?, fd: ParcelFileDescriptor): Result {
        if (picture == null) {
            if (pictureDeleted) {
                if (TagLib.savePictures(fd.dup().detachFd(), arrayOf())) {
                    return Result.Deleted
                }
            } else {
                return Result.None
            }
        } else {
            if (TagLib.savePictures(fd.dup().detachFd(), arrayOf(picture))) {
                return Result.Wrote
            }
        }
        return Result.Failed
    }

    private fun writePropertyMap(fd: ParcelFileDescriptor): Result {
        val propertyMap = properties
            .filterValues { !it.isNullOrBlank() }
            .mapValuesTo(hashMapOf()) { arrayOf(it.value!!) }
        if (TagLib.savePropertyMap(fd.dup().detachFd(), propertyMap)) {
            return Result.Wrote
        }
        return Result.Failed
    }

    private class WriteResult(
        val content: EditTarget.Content,
        val pictureResult: Result = Result.Failed,
        val propertiesResult: Result = Result.Failed
    ) {
        val isSuccess: Boolean
            get() = pictureResult != Result.Failed && propertiesResult != Result.Failed
    }

    private enum class Result {
        None, Wrote, Deleted, Failed
    }
}

@Parcelize
data class EditTarget(
    val type: Type,
    val name: String,
    val id: Long,
    val artworkId: Long,
    val contents: List<Content>
) : Parcelable {

    val hasContent get() = contents.isNotEmpty()

    val hasArtwork get() = artworkId > -1

    val first get() = contents.first()

    @Parcelize
    data class Content(val uri: Uri, val path: String) : Parcelable

    enum class Type {
        Song, Artist, AlbumArtist, Album
    }

    companion object {
        val Empty = EditTarget(Type.Song, "", -1, -1, emptyList())

        fun song(song: Song) = EditTarget(
            type = Type.Song,
            name = song.title,
            id = song.id,
            artworkId = song.albumId,
            contents = listOf(Content(song.mediaStoreUri, song.data))
        )

        fun album(album: Album) = EditTarget(
            type = Type.Album,
            name = album.name,
            id = album.id,
            artworkId = album.id,
            contents = album.songs.map { Content(it.mediaStoreUri, it.data) }
        )

        fun artist(artist: Artist) = EditTarget(
            type = Type.Artist,
            name = artist.name,
            id = artist.id,
            artworkId = -1,
            contents = artist.songs.map { Content(it.mediaStoreUri, it.data) }
        )
    }
}