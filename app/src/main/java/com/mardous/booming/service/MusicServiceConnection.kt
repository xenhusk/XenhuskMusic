package com.mardous.booming.service

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData

class MusicServiceConnection(private val context: Context, serviceComponent: ComponentName) {

    val isConnected = MutableLiveData<Boolean>()
        .apply { postValue(false) }

    private val mediaBrowserConnectionCallback = MediaBrowserConnectionCallback(context)
    private val mediaBrowser = MediaBrowserCompat(context, serviceComponent, mediaBrowserConnectionCallback, null)

    private val mediaControllerCallback = MediaControllerCallback()
    private var _mediaController: MediaControllerCompat? = null

    val mediaController: MediaControllerCompat?
        get() = _mediaController

    fun connect() {
        val contextWrapper = ContextWrapper(context)
        val intent = Intent(contextWrapper, MusicService::class.java)

        // https://issuetracker.google.com/issues/76112072#comment184
        // Workaround for ForegroundServiceDidNotStartInTimeException
        try {
            context.startService(intent)
        } catch (_: Exception) {
            ContextCompat.startForegroundService(context, intent)
        }

        if (!mediaBrowser.isConnected) {
            mediaBrowser.connect()
        }
    }

    fun disconnect() {
        mediaController?.unregisterCallback(mediaControllerCallback)
        if (mediaBrowser.isConnected) {
            mediaBrowser.disconnect()
        }
    }

    private inner class MediaBrowserConnectionCallback(private val context: Context) :
        MediaBrowserCompat.ConnectionCallback() {

        override fun onConnected() {
            // Get a MediaController for the MediaSession.
            _mediaController = MediaControllerCompat(context, mediaBrowser.sessionToken).apply {
                registerCallback(mediaControllerCallback)
            }
            isConnected.postValue(true)
        }

        override fun onConnectionSuspended() {
            _mediaController = null
            isConnected.postValue(false)
        }

        override fun onConnectionFailed() {
            _mediaController = null
            isConnected.postValue(false)
        }
    }

    private inner class MediaControllerCallback : MediaControllerCompat.Callback() {
        override fun onSessionDestroyed() {
            if (mediaBrowser.isConnected) {
                mediaBrowser.disconnect()
            }
            mediaBrowserConnectionCallback.onConnectionSuspended()
        }
    }
}