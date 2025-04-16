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

package com.mardous.booming.util

import android.annotation.SuppressLint
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.getSystemService
import com.mardous.booming.R
import com.mardous.booming.appContext
import com.mardous.booming.extensions.hasR
import com.mardous.booming.model.StorageDevice
import com.mardous.booming.recordException
import java.lang.reflect.InvocationTargetException

object StorageUtil {

    val storageVolumes: List<StorageDevice> by lazy {
        arrayListOf<StorageDevice>().also { newList ->
            try {
                val storageManager = appContext().getSystemService<StorageManager>()!!
                for (sv in storageManager.storageVolumes) {
                    val icon = if (sv.isRemovable && !sv.isPrimary) {
                        R.drawable.ic_sd_card_24dp
                    } else {
                        R.drawable.ic_phone_android_24dp
                    }
                    newList.add(StorageDevice(sv.getPath(), sv.getDescription(appContext()), icon))
                }
            } catch (t: Throwable) {
                recordException(t)
            }
        }
    }

    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    @SuppressLint("DiscouragedPrivateApi")
    private fun StorageVolume.getPath(): String {
        return if (hasR()) {
            this.directory!!.absolutePath
        } else {
            StorageVolume::class.java.getDeclaredMethod("getPath").invoke(this) as String
        }
    }
}
