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

package com.mardous.booming.adapters

import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.MediaStoreSignature
import com.mardous.booming.R
import com.mardous.booming.adapters.base.AbsMultiSelectAdapter
import com.mardous.booming.adapters.base.MediaEntryViewHolder
import com.mardous.booming.extensions.files.getHumanReadableSize
import com.mardous.booming.extensions.glide.DEFAULT_SONG_IMAGE
import com.mardous.booming.extensions.glide.getDefaultGlideTransition
import com.mardous.booming.extensions.media.sectionName
import com.mardous.booming.glide.audiocover.AudioFileCover
import com.mardous.booming.interfaces.IFileCallbacks
import me.zhanghai.android.fastscroll.PopupTextProvider
import java.io.File

class SongFileAdapter(
    private val activity: AppCompatActivity,
    private var dataSet: List<File>,
    private val itemLayoutRes: Int,
    private val callbacks: IFileCallbacks?
) : AbsMultiSelectAdapter<SongFileAdapter.ViewHolder, File>(activity, R.menu.menu_media_selection), PopupTextProvider {

    init {
        this.setHasStableIds(true)
    }

    override fun getItemViewType(position: Int): Int {
        return if (dataSet[position].isDirectory) FOLDER else FILE
    }

    override fun getItemId(position: Int): Long {
        return dataSet[position].hashCode().toLong()
    }

    fun swapDataSet(songFiles: List<File>) {
        this.dataSet = songFiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(activity).inflate(itemLayoutRes, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, index: Int) {
        val file = dataSet[index]
        val isChecked = isChecked(file)
        holder.itemView.isActivated = isChecked
        holder.menu?.isGone = isChecked
        holder.title?.text = getFileTitle(file)
        if (holder.text != null) {
            if (holder.itemViewType == FILE) {
                holder.text.text = getFileText(file)
            } else {
                holder.text.visibility = View.GONE
            }
        }

        if (holder.image != null) {
            loadFileImage(file, holder)
        }
    }

    private fun getFileTitle(file: File): String {
        return file.name
    }

    private fun getFileText(file: File): String? {
        return if (file.isDirectory) null else file.getHumanReadableSize()
    }

    private fun loadFileImage(file: File, holder: ViewHolder) {
        if (holder.image == null)
            return

        if (file.isDirectory) {
            holder.image.scaleType = ImageView.ScaleType.CENTER
            holder.image.setImageResource(R.drawable.ic_folder_24dp)
        } else {
            Glide.with(activity)
                .load(AudioFileCover(file.path, true))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .error(DEFAULT_SONG_IMAGE)
                .placeholder(DEFAULT_SONG_IMAGE)
                .transition(getDefaultGlideTransition())
                .signature(MediaStoreSignature("", file.lastModified(), 0))
                .into(holder.image)
        }
    }

    override fun getItemCount(): Int {
        return dataSet.size
    }

    override fun getIdentifier(position: Int): File {
        return dataSet[position]
    }

    override fun getName(item: File): String {
        return getFileTitle(item)
    }

    override fun onMultipleItemAction(menuItem: MenuItem, selection: List<File>) {
        callbacks?.filesMenuClick(menuItem, selection)
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        return dataSet.getOrNull(position)?.name?.sectionName() ?: ""
    }

    inner class ViewHolder(itemView: View) : MediaEntryViewHolder(itemView) {

        init {
            if (menu != null && callbacks != null) {
                menu.setOnClickListener { v ->
                    val position = layoutPosition
                    if (isPositionInRange(position)) {
                        callbacks.fileMenuClick(dataSet[position], v)
                    }
                }
            }
        }

        override fun onClick(v: View?) {
            val position = layoutPosition
            if (isPositionInRange(position)) {
                if (isInQuickSelectMode) {
                    toggleChecked(position)
                } else {
                    callbacks?.fileSelected(dataSet[position])
                }
            }
        }

        override fun onLongClick(v: View?): Boolean {
            val position = layoutPosition
            return isPositionInRange(position) && toggleChecked(position)
        }

        private fun isPositionInRange(position: Int): Boolean {
            return position >= 0 && position < dataSet.size
        }
    }

    companion object {
        private const val FILE = 0
        private const val FOLDER = 1
    }
}