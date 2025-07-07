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

package com.mardous.booming.glide.audiocover

import android.content.ContentResolver
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.kyant.taglib.TagLib
import okhttp3.internal.closeQuietly
import java.io.ByteArrayInputStream
import java.io.InputStream

class AudioFileCoverFetcher(
    private val contentResolver: ContentResolver,
    private val model: AudioFileCover
) : DataFetcher<InputStream> {

    private var stream: InputStream? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream?>) {
        try {
            stream = contentResolver.openFileDescriptor(model.uri, "r")?.use { fd ->
                val picture = TagLib.getFrontCover(fd.dup().detachFd())
                val binaryData = picture?.data

                if (binaryData != null) {
                    ByteArrayInputStream(binaryData)
                } else {
                    AudioFileCoverUtils.fallback(model.path, model.useFolderArt)
                }
            }
            if (stream != null) {
                callback.onDataReady(stream)
            } else {
                callback.onLoadFailed(IllegalStateException("File descriptor is null"))
            }
        } catch (e: Exception) {
            callback.onLoadFailed(e)
        }
    }

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL

    override fun cleanup() {
        stream?.closeQuietly()
    }

    override fun cancel() {
        // Glide doesn't support canceling this fetcher
    }
}
