/*
 * Copyright (c) 2025 Christians Martínez Alvarado
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

import android.annotation.SuppressLint
import android.view.*
import androidx.core.view.isGone
import androidx.fragment.app.FragmentActivity
import com.mardous.booming.R
import com.mardous.booming.adapters.base.AbsMultiSelectAdapter
import com.mardous.booming.adapters.base.MediaEntryViewHolder
import com.mardous.booming.adapters.extension.isActivated
import com.mardous.booming.extensions.media.songsStr
import com.mardous.booming.extensions.resources.useAsIcon
import com.mardous.booming.helper.menu.OnClickMenu
import com.mardous.booming.interfaces.IFolderCallback
import com.mardous.booming.model.Folder

class FolderAdapter(
    activity: FragmentActivity,
    folders: List<Folder>,
    private val itemLayoutRes: Int,
    private val callback: IFolderCallback?,
) : AbsMultiSelectAdapter<FolderAdapter.ViewHolder, Folder>(activity, R.menu.menu_media_selection) {

    var folders: List<Folder> = folders
        private set

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(itemLayoutRes, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]
        val isChecked = isChecked(folder)
        holder.isActivated = isChecked
        holder.menu?.isGone = isChecked
        holder.title?.text = folder.name
        holder.text?.text = folder.songCount.songsStr(holder.itemView.context)
        holder.image?.setImageResource(R.drawable.ic_folder_24dp)
    }

    override fun getItemCount(): Int = folders.size

    override fun getItemId(position: Int): Long = folders[position].id

    override fun getIdentifier(position: Int): Folder = folders[position]

    override fun getName(item: Folder): String? = item.name

    override fun onMultipleItemAction(menuItem: MenuItem, selection: List<Folder>) {
        callback?.foldersMenuItemClick(selection, menuItem)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(folders: List<Folder>) {
        this.folders = folders
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : MediaEntryViewHolder(itemView) {
        private val currentFolder: Folder
            get() = folders[layoutPosition]

        override fun onClick(view: View) {
            if (isInQuickSelectMode) {
                toggleChecked(layoutPosition)
            } else {
                callback?.folderClick(currentFolder)
            }
        }

        override fun onLongClick(view: View): Boolean {
            toggleChecked(layoutPosition)
            return true
        }

        init {
            image?.useAsIcon()
            menu?.setOnClickListener(object : OnClickMenu() {
                override val popupMenuRes: Int
                    get() = R.menu.menu_item_directory

                override fun onPreparePopup(menu: Menu) {
                    menu.removeItem(R.id.action_set_as_start_directory)
                    menu.removeItem(R.id.action_scan)
                }

                override fun onMenuItemClick(item: MenuItem): Boolean {
                    return callback?.folderMenuItemClick(currentFolder, item) == true
                }
            })
        }
    }
}