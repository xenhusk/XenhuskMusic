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

package com.mardous.booming.helper;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import com.mardous.booming.service.MusicPlayer;

public class MusicProgressViewUpdateHelper extends Handler {

    private static final int CMD_REFRESH_PROGRESS_VIEWS = 1;

    private static final int MIN_INTERVAL = 20;
    private static final int UPDATE_INTERVAL_PLAYING = 1000;
    private static final int UPDATE_INTERVAL_PAUSED = 500;

    private final Callback callback;
    private final int intervalPlaying;
    private final int intervalPaused;
    private boolean firstUpdate = true;

    public void start() {
        queueNextRefresh(1);
    }

    public void stop() {
        removeMessages(CMD_REFRESH_PROGRESS_VIEWS);
    }

    public MusicProgressViewUpdateHelper(@NonNull Callback callback) {
        this(callback, UPDATE_INTERVAL_PLAYING, UPDATE_INTERVAL_PAUSED);
    }

    public MusicProgressViewUpdateHelper(@NonNull Callback callback, int intervalPlaying, int intervalPaused) {
        super(Looper.getMainLooper());
        this.callback = callback;
        this.intervalPlaying = intervalPlaying;
        this.intervalPaused = intervalPaused;
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        super.handleMessage(msg);
        if (msg.what == CMD_REFRESH_PROGRESS_VIEWS) {
            queueNextRefresh(refreshProgressViews());
        }
    }

    private long refreshProgressViews() {
        final long progressMillis = MusicPlayer.INSTANCE.getSongProgressMillis();
        final long totalMillis = MusicPlayer.INSTANCE.getSongDurationMillis();
        if (totalMillis > 0) {
            firstUpdate = false;
            callback.onUpdateProgressViews(progressMillis, totalMillis);
        }
        if (!MusicPlayer.INSTANCE.isPlaying() && !firstUpdate) {
            return intervalPaused;
        }

		final long remainingMillis = intervalPlaying - progressMillis % intervalPlaying;

        return Math.max(MIN_INTERVAL, remainingMillis);
    }

    private void queueNextRefresh(final long delay) {
        final Message message = obtainMessage(CMD_REFRESH_PROGRESS_VIEWS);
        removeMessages(CMD_REFRESH_PROGRESS_VIEWS);
        sendMessageDelayed(message, delay);
    }

    public interface Callback {
        void onUpdateProgressViews(long progress, long total);
    }
}
