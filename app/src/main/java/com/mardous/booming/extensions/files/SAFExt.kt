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

package com.mardous.booming.extensions.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.mardous.booming.R
import com.mardous.booming.extensions.showToast
import com.mardous.booming.model.Song
import com.mardous.booming.util.Preferences
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.exceptions.CannotWriteException
import org.jaudiotagger.audio.generic.Utils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private const val TAG = "SAF"

fun File.isSAFRequired(): Boolean = !canWrite()

fun AudioFile.isSAFRequired(): Boolean = file.isSAFRequired()

fun Song.isSAFRequiredForSong(): Boolean = data.isSAFRequiredForPath()

fun String.isSAFRequiredForPath(): Boolean = File(this).isSAFRequired()

fun List<String>.isSAFRequiredForPaths(): Boolean = any { it.isSAFRequiredForPath() }

fun List<Song>.isSAFRequiredForSongs(): Boolean = any { it.isSAFRequiredForSong() }

fun Context.saveTreeUri(uri: Uri?): Uri? {
    if (uri == null) return null
    contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
    )
    Preferences.sAFSDCardUri = uri.toString()
    return uri
}

private fun isTreeUriSaved(): Boolean = !Preferences.sAFSDCardUri.isNullOrEmpty()

fun Context.isSDCardAccessGranted(): Boolean {
    if (!isTreeUriSaved()) return false
    val sdcardUri = Preferences.sAFSDCardUri
    return contentResolver.persistedUriPermissions.any {
        it.uri.toString() == sdcardUri && it.isWritePermission
    }
}

/**
 * https://github.com/vanilla-music/vanilla-music-tag-editor/commit/e00e87fef289f463b6682674aa54be834179ccf0#diff-d436417358d5dfbb06846746d43c47a5R359
 * Finds needed file through Document API for SAF. It's not optimized yet - you can still gain wrong URI on
 * files such as "/a/b/c.mp3" and "/b/a/c.mp3", but I consider it complete enough to be usable.
 *
 * @param segments - path segments that are left to find
 * @return URI for found file. Null if nothing found.
 */
private fun DocumentFile.findDocument(segments: MutableList<String>): Uri? {
    for (file in listFiles()) {
        val index = segments.indexOf(file.name)
        if (index == -1) {
            continue
        }
        if (file.isDirectory) {
            segments.remove(file.name)
            return findDocument(segments)
        }
        if (file.isFile && index == segments.size - 1) {
            // got to the last part
            return file.uri
        }
    }
    return null
}

fun Context.writeUsingSAF(audio: AudioFile) {
    if (!audio.isSAFRequired()) {
        try {
            audio.commit()
        } catch (e: CannotWriteException) {
            e.printStackTrace()
        }
    } else {
        var uri: Uri? = null

        if (isTreeUriSaved()) {
            val sdcard = Preferences.sAFSDCardUri!!.toUri()
            val pathSegments = listOf(*audio.file.absolutePath.split("/").toTypedArray())
                .toMutableList()

            uri = DocumentFile.fromTreeUri(this, sdcard)?.findDocument(pathSegments)
        }

        if (uri == null) {
            showToast(R.string.saf_error_uri)
            Log.e(TAG, "write: Can't get SAF URI")
            return
        }

        try {
            // copy file to app folder to use jaudiotagger
            val original = audio.file
            val temp = File.createTempFile("tmp-media", '.'.toString() + Utils.getExtension(original)).also {
                Utils.copy(original, it)

                audio.file = it
                audio.commit()
            }

            val pfd = contentResolver.openFileDescriptor(uri, "rw")
            if (pfd == null) {
                Log.e(TAG, "write: SAF provided incorrect URI: $uri")
                return
            }

            // now read persisted data and write it to real FD provided by SAF
            val fis = FileInputStream(temp)
            val audioContent = fis.readBytes()
            val fos = FileOutputStream(pfd.fileDescriptor)
            fos.write(audioContent)
            fos.close()

            temp.delete()
        } catch (e: Exception) {
            showToast(getString(R.string.saf_write_failed, e.localizedMessage))
            Log.e(TAG, "write: Failed to write to file descriptor provided by SAF", e)
        }
    }
}

fun Context.deleteUsingSAF(path: String): Boolean {
    if (!path.isSAFRequiredForPath()) {
        try {
            return File(path).delete()
        } catch (e: NullPointerException) {
            Log.e(TAG, "Failed to find file $path", e)
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Failed to delete file $path", e)
        }
    } else {
        var uri: Uri? = null

        if (isTreeUriSaved()) {
            val sdcard = Preferences.sAFSDCardUri!!.toUri()
            val pathSegments = listOf(*path.split("/").toTypedArray())
                .toMutableList()

            uri = DocumentFile.fromTreeUri(this, sdcard)?.findDocument(pathSegments)
        }

        if (uri != null) {
            try {
                return DocumentsContract.deleteDocument(contentResolver, uri)
            } catch (e: java.lang.Exception) {
                showToast(getString(R.string.saf_delete_failed, e.localizedMessage))
                Log.e(TAG, "delete: Failed to delete a file descriptor provided by SAF", e)
            }
        } else {
            showToast(getString(R.string.saf_error_uri))
            Log.e(TAG, "delete: Can't get SAF URI")
        }
    }
    return false
}