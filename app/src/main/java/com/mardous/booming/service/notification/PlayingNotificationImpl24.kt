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

package com.mardous.booming.service.notification

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.mardous.booming.R
import com.mardous.booming.activities.MainActivity
import com.mardous.booming.extensions.dip
import com.mardous.booming.extensions.glide.getSongGlideModel
import com.mardous.booming.extensions.glide.songOptions
import com.mardous.booming.extensions.hasS
import com.mardous.booming.extensions.media.displayArtistName
import com.mardous.booming.model.Song
import com.mardous.booming.service.MusicService
import com.mardous.booming.service.constants.ServiceAction

class PlayingNotificationImpl24(service: MusicService, mediaSessionToken: MediaSessionCompat.Token) :
    PlayingNotification(service) {

    init {
        val action = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val clickIntent = PendingIntent.getActivity(
            context, 0, action, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val serviceName = ComponentName(context, MusicService::class.java)
        val intent = Intent(ServiceAction.ACTION_QUIT)
            .setComponent(serviceName)

        val deleteIntent = PendingIntent.getService(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playPauseAction = buildPlayAction(true)
        val previousAction = NotificationCompat.Action(
            R.drawable.ic_previous_24dp,
            context.getString(R.string.action_previous),
            retrievePlaybackAction(ServiceAction.ACTION_PREVIOUS)
        )
        val nextAction = NotificationCompat.Action(
            R.drawable.ic_next_24dp,
            context.getString(R.string.action_next),
            retrievePlaybackAction(ServiceAction.ACTION_NEXT)
        )
        val dismissAction = NotificationCompat.Action(
            R.drawable.ic_close_24dp,
            context.getString(R.string.action_cancel),
            retrievePlaybackAction(ServiceAction.ACTION_QUIT)
        )

        setSmallIcon(R.drawable.ic_stat_music_playback)
        setContentIntent(clickIntent)
        setDeleteIntent(deleteIntent)
        setShowWhen(false)
        addAction(previousAction) //0
        addAction(playPauseAction) //1
        addAction(nextAction) //2
        if (hasS()) {
            addAction(dismissAction) //3
        }

        setStyle(
            MediaStyle()
                .setMediaSession(mediaSessionToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
        setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        priority = notificationPriority
    }

    @SuppressLint("RestrictedApi")
    override fun update(song: Song, isPlaying: Boolean, onUpdate: () -> Unit) {
        if (song == Song.emptySong) return
        mActions[1] = buildPlayAction(isPlaying)
        setContentTitle(song.title)
        setContentText(song.displayArtistName())
        setSubText(getExtraTextString(song))
        val bigNotificationImageSize = context.dip(R.dimen.notification_big_image_size)
        Glide.with(context)
            .asBitmap()
            .songOptions(song)
            .load(song.getSongGlideModel())
            .centerCrop()
            .into(object : CustomTarget<Bitmap>(bigNotificationImageSize, bigNotificationImageSize) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    setLargeIcon(resource)
                    onUpdate()
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.default_audio_art))
                    onUpdate()
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.default_audio_art))
                    onUpdate()
                }
            })
    }

    private fun buildPlayAction(isPlaying: Boolean): NotificationCompat.Action {
        val playButtonResId =
            if (isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp
        return NotificationCompat.Action.Builder(
            playButtonResId,
            context.getString(R.string.action_play_pause),
            retrievePlaybackAction(ServiceAction.ACTION_TOGGLE_PAUSE)
        ).build()
    }

    private fun retrievePlaybackAction(action: String): PendingIntent {
        val serviceName = ComponentName(context, MusicService::class.java)
        val intent = Intent(action)
        intent.component = serviceName
        return PendingIntent.getService(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        fun from(
            context: MusicService,
            notificationManager: NotificationManager,
            mediaSession: MediaSessionCompat,
        ): PlayingNotification {
            createNotificationChannel(context, notificationManager)
            return PlayingNotificationImpl24(context, mediaSession.sessionToken)
        }
    }
}