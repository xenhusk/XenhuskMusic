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

package com.mardous.booming.ui.screen.classification

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mardous.booming.databinding.ItemClassificationBinding
import com.mardous.booming.data.local.room.SongClassificationEntity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying classification results
 */
class ClassificationAdapter(
    private val onItemClick: (SongClassificationEntity) -> Unit
) : ListAdapter<SongClassificationEntity, ClassificationAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemClassificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(
        private val binding: ItemClassificationBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(classification: SongClassificationEntity) {
            binding.apply {
                songTitle.text = classification.songTitle
                artistName.text = classification.artistName
                classificationType.text = classification.classificationType
                confidence.text = "${(classification.confidence * 100).toInt()}%"
                
                // Set color based on classification type
                val color = when (classification.classificationType) {
                    "Christian" -> root.context.getColor(R.color.christian_color)
                    "Secular" -> root.context.getColor(R.color.secular_color)
                    else -> root.context.getColor(android.R.color.darker_gray)
                }
                classificationType.setTextColor(color)
                
                // Format timestamp
                val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                val date = Date(classification.classificationTimestamp)
                timestamp.text = dateFormat.format(date)
                
                // Show manual override indicator
                manualOverrideIndicator.isVisible = classification.isManualOverride
                
                // Set click listener
                root.setOnClickListener {
                    onItemClick(classification)
                }
            }
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<SongClassificationEntity>() {
        override fun areItemsTheSame(
            oldItem: SongClassificationEntity,
            newItem: SongClassificationEntity
        ): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(
            oldItem: SongClassificationEntity,
            newItem: SongClassificationEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}
