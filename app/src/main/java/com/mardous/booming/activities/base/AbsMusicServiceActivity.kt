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

package com.mardous.booming.activities.base

import android.Manifest
import android.content.*
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.CallSuper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mardous.booming.extensions.hasR
import com.mardous.booming.extensions.hasT
import com.mardous.booming.interfaces.IMusicServiceEventListener
import com.mardous.booming.service.MusicPlayer
import com.mardous.booming.service.constants.ServiceEvent
import java.lang.ref.WeakReference

/**
 * @author Christians M. A. (mardous)
 */
open class AbsMusicServiceActivity : AbsBaseActivity(),
    IMusicServiceEventListener {

    private val musicServiceEventListeners: MutableList<IMusicServiceEventListener?> = ArrayList()
    private var musicStateReceiver: MusicStateReceiver? = null

    private var serviceToken: MusicPlayer.ServiceToken? = null
    private var receiverRegistered = false

    override fun getPermissionsToRequest(): Array<String> {
        return mutableSetOf<String>().apply {
            if (hasT()) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (!hasR()) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC

        serviceToken = MusicPlayer.bindToService(this, object : ServiceConnection {
            override fun onServiceConnected(p1: ComponentName, p2: IBinder) {
                onServiceConnected()
            }

            override fun onServiceDisconnected(p1: ComponentName) {
                onServiceDisconnected()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        MusicPlayer.unbindFromService(serviceToken)
        if (receiverRegistered && musicStateReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(musicStateReceiver!!)
            receiverRegistered = false
        }
    }

    override fun onHasPermissionsChanged(hasPermissions: Boolean) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(ServiceEvent.MEDIA_STORE_CHANGED)
                .putExtra("from_permissions_changed", true) // just in case we need to know this at some point
        )
    }

    fun addMusicServiceEventListener(listener: IMusicServiceEventListener?) {
        if (listener != null) {
            musicServiceEventListeners.add(listener)
            if (receiverRegistered) // if the service is already started we must notify the listener
                listener.onServiceConnected()
        }
    }

    fun removeMusicServiceEventListener(listener: IMusicServiceEventListener?) {
        if (listener != null) {
            musicServiceEventListeners.remove(listener)
        }
    }

    @CallSuper
    override fun onServiceConnected() {
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ServiceEvent.META_CHANGED)
                addAction(ServiceEvent.REPEAT_MODE_CHANGED)
                addAction(ServiceEvent.SHUFFLE_MODE_CHANGED)
                addAction(ServiceEvent.PLAY_STATE_CHANGED)
                addAction(ServiceEvent.QUEUE_CHANGED)
                addAction(ServiceEvent.MEDIA_STORE_CHANGED)
                addAction(ServiceEvent.FAVORITE_STATE_CHANGED)
                addAction(ServiceEvent.PLAYBACK_RESTORED)
            }

            musicStateReceiver = MusicStateReceiver(this).also {
                LocalBroadcastManager.getInstance(this).registerReceiver(it, filter)
                receiverRegistered = true
            }
        }

        musicServiceEventListeners.forEach { it?.onServiceConnected() }
    }

    @CallSuper
    override fun onServiceDisconnected() {
        if (receiverRegistered && musicStateReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(musicStateReceiver!!)
            receiverRegistered = false
        }

        musicServiceEventListeners.forEach { it?.onServiceDisconnected() }
    }

    @CallSuper
    override fun onPlayingMetaChanged() {
        musicServiceEventListeners.forEach { it?.onPlayingMetaChanged() }
    }

    @CallSuper
    override fun onPlayStateChanged() {
        musicServiceEventListeners.forEach { it?.onPlayStateChanged() }
    }

    @CallSuper
    override fun onRepeatModeChanged() {
        musicServiceEventListeners.forEach { it?.onRepeatModeChanged() }
    }

    @CallSuper
    override fun onShuffleModeChanged() {
        musicServiceEventListeners.forEach { it?.onShuffleModeChanged() }
    }

    @CallSuper
    override fun onQueueChanged() {
        musicServiceEventListeners.forEach { it?.onQueueChanged() }
    }

    @CallSuper
    override fun onMediaStoreChanged() {
        musicServiceEventListeners.forEach { it?.onMediaStoreChanged() }
    }

    @CallSuper
    override fun onFavoritesStoreChanged() {
        musicServiceEventListeners.forEach { it?.onFavoritesStoreChanged() }
    }

    override fun onPlaybackRestored() {
        musicServiceEventListeners.forEach { it?.onPlaybackRestored() }
    }

    class MusicStateReceiver internal constructor(activity: AbsMusicServiceActivity) : BroadcastReceiver() {

        private val mReference: WeakReference<AbsMusicServiceActivity> = WeakReference(activity)

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val musicActivity = mReference.get()
            if (musicActivity != null && action != null) {
                when (action) {
                    ServiceEvent.META_CHANGED -> musicActivity.onPlayingMetaChanged()
                    ServiceEvent.PLAY_STATE_CHANGED -> musicActivity.onPlayStateChanged()
                    ServiceEvent.REPEAT_MODE_CHANGED -> musicActivity.onRepeatModeChanged()
                    ServiceEvent.SHUFFLE_MODE_CHANGED -> musicActivity.onShuffleModeChanged()
                    ServiceEvent.QUEUE_CHANGED -> musicActivity.onQueueChanged()
                    ServiceEvent.MEDIA_STORE_CHANGED -> musicActivity.onMediaStoreChanged()
                    ServiceEvent.FAVORITE_STATE_CHANGED -> musicActivity.onFavoritesStoreChanged()
                    ServiceEvent.PLAYBACK_RESTORED -> musicActivity.onPlaybackRestored()
                }
            }
        }
    }

    companion object {
        private val TAG = AbsMusicServiceActivity::class.java.simpleName
    }
}