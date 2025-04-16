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

package com.mardous.booming.service

import android.database.ContentObserver
import android.os.Handler
import com.mardous.booming.service.constants.ServiceEvent

open class MediaStoreObserver(private val service: MusicService, private val uiHandler: Handler) :
    ContentObserver(uiHandler), Runnable {

    override fun onChange(selfChange: Boolean) {
        // if a change is detected, remove any scheduled callback
        // then post a new one. This is intended to prevent closely
        // spaced events from generating multiple refresh calls
        uiHandler.removeCallbacks(this)
        uiHandler.postDelayed(this, REFRESH_DELAY)
    }

    override fun run() {
        // actually call refresh when the delayed callback fires
        // do not send a sticky broadcast here
        service.handleAndSendChangeInternal(ServiceEvent.MEDIA_STORE_CHANGED)
    }

    companion object {
        // milliseconds to delay before calling refresh to aggregate events
        private const val REFRESH_DELAY: Long = 500
    }
}
