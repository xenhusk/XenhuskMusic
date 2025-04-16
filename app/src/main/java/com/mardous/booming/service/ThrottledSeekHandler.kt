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

import android.os.Handler
import com.mardous.booming.service.constants.ServiceEvent
import kotlinx.coroutines.Runnable

class ThrottledSeekHandler constructor(private val musicService: MusicService, private val mHandler: Handler) :
    Runnable {
    override fun run() {
        musicService.savePositionInTrack()
        musicService.sendPublicIntent(ServiceEvent.PLAY_STATE_CHANGED) // for musixmatch synced lyrics
    }

    fun notifySeek() {
        musicService.updateMediaSessionPlaybackState()
        mHandler.removeCallbacks(this)
        mHandler.postDelayed(this, THROTTLE)
    }

    companion object {
        private const val THROTTLE: Long = 500
    }
}