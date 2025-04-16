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

package com.mardous.booming.misc;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.mardous.booming.R;

import java.lang.ref.WeakReference;
import java.util.List;

public class UpdateToastMediaScannerCompletionListener implements MediaScannerConnection.OnScanCompletedListener {
    private int scanned = 0;
    private int failed = 0;

    private final List<String> toBeScanned;

    private final String scannedFiles;
    private final String couldNotScanFiles;

    private final Toast toast;
    private final WeakReference<Activity> activityWeakReference;

    @SuppressLint("ShowToast")
    public UpdateToastMediaScannerCompletionListener(@NonNull Activity activity, @NonNull List<String> toBeScanned) {
        this.toBeScanned = toBeScanned;
        scannedFiles = activity.getString(R.string.scanned_x_of_x_files);
        couldNotScanFiles = activity.getString(R.string.could_not_scan_files);
        toast = Toast.makeText(activity.getApplicationContext(), "", Toast.LENGTH_SHORT);
        activityWeakReference = new WeakReference<>(activity);
    }

    @Override
    public void onScanCompleted(final String path, final Uri uri) {
        Activity activity = activityWeakReference.get();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (uri == null) {
                    failed++;
                } else {
                    scanned++;
                }
                String text = " " + String.format(scannedFiles, scanned, toBeScanned.size()) + (failed > 0 ? " " + String.format(couldNotScanFiles, failed) : "");
                toast.setText(text);
                toast.show();
            });
        }
    }
}
