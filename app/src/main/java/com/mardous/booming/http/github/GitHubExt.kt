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

package com.mardous.booming.http.github

import android.content.Context
import com.mardous.booming.R
import com.mardous.booming.extensions.isOnline
import com.mardous.booming.util.Preferences
import com.mardous.booming.util.UpdateSearchMode
import java.util.concurrent.TimeUnit

fun Context.isAbleToUpdate(): Boolean {
    if (!resources.getBoolean(R.bool.enable_app_update))
        return false

    val minElapsedMillis = when (Preferences.updateSearchMode) {
        UpdateSearchMode.EVERY_DAY -> TimeUnit.DAYS.toMillis(1)
        UpdateSearchMode.EVERY_FIFTEEN_DAYS -> TimeUnit.DAYS.toMillis(15)
        UpdateSearchMode.WEEKLY -> TimeUnit.DAYS.toMillis(7)
        UpdateSearchMode.MONTHLY -> TimeUnit.DAYS.toMillis(30)
        else -> -1
    }
    val elapsedMillis = System.currentTimeMillis() - Preferences.lastUpdateSearch
    if ((minElapsedMillis > -1) && elapsedMillis >= minElapsedMillis) {
        return isOnline(Preferences.updateOnlyWifi)
    }
    return false
}