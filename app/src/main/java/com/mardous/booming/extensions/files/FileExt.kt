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
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.mardous.booming.extensions.fileProviderAuthority
import com.mardous.booming.util.StorageUtil
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import java.io.FileFilter
import java.io.InputStream
import java.io.OutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import java.util.zip.ZipOutputStream
import kotlin.math.log10
import kotlin.math.pow


typealias FileFilter = ((File) -> Boolean)

private val FILE_NAME_DATE_FORMAT = SimpleDateFormat("MMM_d_yyyy_HH.mm.ss", Locale.ENGLISH)

fun getFormattedFileName(prefix: String, extension: String, timeMillis: Long = System.currentTimeMillis()): String {
    return String.format(Locale.ROOT, "%s_%s.%s", prefix, timeMillis.asFormattedFileTime(), extension)
}

fun Long.asFormattedFileTime(): String {
    return FILE_NAME_DATE_FORMAT.format(Date(this))
}

fun Long.asReadableFileSize(): String {
    if (this <= 0) {
        return "0"
    }
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(this.toDouble()) / log10(1024.0)).toInt()
    return String.format(
        "%s %s",
        DecimalFormat("#,##0.#").format(this / 1024.0.pow(digitGroups.toDouble())),
        units[digitGroups]
    )
}

fun String.asFileNameExtension(): String {
    if (isEmpty() || !contains(".")) return this

    val pos = lastIndexOf(".")
    return if (pos == -1) this else substring(pos + 1)
}

fun File.isMimeType(mimeType: String?, mimeTypeMap: MimeTypeMap = MimeTypeMap.getSingleton()): Boolean {
    return if (mimeType == null || mimeType == "*/*") {
        true
    } else {
        val fileExtension = name.asFileNameExtension()
        val fileType = mimeTypeMap.getMimeTypeFromExtension(fileExtension) ?: return false
        // check the 'type/subtype' pattern
        if (fileType == mimeType) {
            return true
        }
        // check the 'type/*' pattern
        val mimeTypeDelimiter = mimeType.lastIndexOf('/')
        if (mimeTypeDelimiter == -1) {
            return false
        }
        val mimeTypeMainType = mimeType.substring(0, mimeTypeDelimiter)
        val mimeTypeSubtype = mimeType.substring(mimeTypeDelimiter + 1)
        if (mimeTypeSubtype != "*") {
            return false
        }
        val fileTypeDelimiter = fileType.lastIndexOf('/')
        if (fileTypeDelimiter == -1) {
            return false
        }
        val fileTypeMainType = fileType.substring(0, fileTypeDelimiter)
        fileTypeMainType == mimeTypeMainType
    }
}

fun File.getPrettyAbsolutePath(): String {
    val filePath = absolutePath
    for (storageDevice in StorageUtil.storageVolumes) {
        if (filePath == storageDevice.path) {
            return storageDevice.name
        }
        if (filePath.startsWith(storageDevice.path)) {
            return filePath.replace(storageDevice.path, storageDevice.name)
        }
    }
    return filePath
}

fun File.getCanonicalFileSafe(): File = runCatching { canonicalFile }.getOrDefault(absoluteFile)

fun File.getCanonicalPathSafe(): String = runCatching { canonicalPath }.getOrDefault(absolutePath)

fun File.getHumanReadableSize() = length().asReadableFileSize()

fun File.getContentUri(context: Context): Uri =
    FileProvider.getUriForFile(context, context.fileProviderAuthority, this)

fun File.toAudioFile(): AudioFile? = runCatching { AudioFileIO.read(this) }.getOrNull()

fun File.copyToUri(context: Context, toUri: Uri) {
    context.contentResolver.openOutputStream(toUri)?.use { output ->
        this.inputStream().use { input ->
            input.copyTo(output)
        }
    }
}

fun File.listFilesAsList(fileFilter: FileFilter?): List<File> {
    val fileList = LinkedList<File>()
    val found = this.listFiles(fileFilter)
    if (found != null) {
        fileList.addAll(found)
    }
    return fileList
}

fun File.listFilesDeep(fileFilter: FileFilter?): List<File> {
    val files = LinkedList<File>()
    internalListFilesDeep(files, this, fileFilter)
    return files
}

fun List<File>.listFilesDeep(fileFilter: FileFilter?): MutableList<File> {
    val resFiles = LinkedList<File>()
    for (file in this) {
        if (file.isDirectory) {
            internalListFilesDeep(resFiles, file, fileFilter)
        } else if (fileFilter == null || fileFilter.accept(file)) {
            resFiles.add(file)
        }
    }
    return resFiles
}

private fun internalListFilesDeep(files: MutableList<File>, directory: File, fileFilter: FileFilter?) {
    val found = directory.listFiles(fileFilter)
    if (found != null) {
        for (file in found) {
            if (file.isDirectory) {
                internalListFilesDeep(files, file, fileFilter)
            } else {
                files.add(file)
            }
        }
    }
}

/**
 * Reads this stream completely as a String.
 *
 * *Note*:  The stream is closed automatically.
 *
 * @return the string with corresponding file content.
 */
fun InputStream.readString(): String = this.bufferedReader().use { it.readText() }

fun OutputStream.zipOutputStream(): ZipOutputStream = ZipOutputStream(buffered())