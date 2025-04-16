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

package com.mardous.booming.misc

import android.content.Context
import androidx.loader.content.AsyncTaskLoader

/**
 * [Issue
 * 14944](http://code.google.com/p/android/issues/detail?id=14944)
 *
 * @author Alexander Blom
 */
abstract class WrappedAsyncTaskLoader<D>(context: Context): AsyncTaskLoader<D>(context) {

    private var mData: D? = null

    /**
     * {@inheritDoc}
     */
    override fun deliverResult(data: D?) {
        if (!isReset) {
            this.mData = data
            super.deliverResult(data)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onStartLoading() {
        super.onStartLoading()
        if (this.mData != null) {
            deliverResult(this.mData)
        } else if (takeContentChanged() || this.mData == null) {
            forceLoad()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onStopLoading() {
        super.onStopLoading()
        // Attempt to cancel the current load task if possible
        cancelLoad()
    }

    /**
     * {@inheritDoc}
     */
    override fun onReset() {
        super.onReset()
        // Ensure the loader is stopped
        onStopLoading()
        this.mData = null
    }
}
