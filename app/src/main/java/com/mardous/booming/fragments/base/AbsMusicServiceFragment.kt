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

package com.mardous.booming.fragments.base

import android.content.Context
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.mardous.booming.activities.base.AbsMusicServiceActivity
import com.mardous.booming.interfaces.IMusicServiceEventListener

abstract class AbsMusicServiceFragment @JvmOverloads constructor(@LayoutRes layoutRes: Int = 0) :
    Fragment(layoutRes),
    IMusicServiceEventListener {

    private var activity: AbsMusicServiceActivity? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity = context as? AbsMusicServiceActivity
            ?: throw RuntimeException(context.javaClass.simpleName + " must be an instance of " + AbsMusicServiceActivity::class.java.simpleName)
    }

    override fun onDetach() {
        super.onDetach()
        activity = null
    }

    override fun onStart() {
        super.onStart()
        activity?.addMusicServiceEventListener(this)
    }

    override fun onStop() {
        super.onStop()
        activity?.removeMusicServiceEventListener(this)
    }

    @CallSuper
    override fun onServiceConnected() {
    }

    @CallSuper
    override fun onServiceDisconnected() {
    }

    @CallSuper
    override fun onPlayingMetaChanged() {
    }

    @CallSuper
    override fun onPlayStateChanged() {
    }

    @CallSuper
    override fun onRepeatModeChanged() {
    }

    @CallSuper
    override fun onShuffleModeChanged() {
    }

    @CallSuper
    override fun onQueueChanged() {
    }

    @CallSuper
    override fun onMediaStoreChanged() {
    }

    @CallSuper
    override fun onFavoritesStoreChanged() {
    }
}