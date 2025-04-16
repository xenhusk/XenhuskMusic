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

package com.mardous.booming.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mardous.booming.R
import com.mardous.booming.databinding.DialogMarkdownBinding
import com.mardous.booming.extensions.readStringFromAsset
import com.mardous.booming.extensions.resources.setMarkdownText

/**
 * @author Christians M. A. (mardous)
 */
class MarkdownDialog : DialogFragment() {
    private var _binding: DialogMarkdownBinding? = null
    private val binding get() = _binding!!

    private var title: String? = null
    private var markdownContent: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogMarkdownBinding.inflate(layoutInflater)
        if (!markdownContent.isNullOrEmpty()) {
            binding.message.setMarkdownText(markdownContent!!)
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton(R.string.close_action, null)
            .create()
    }

    fun setTitle(title: String): MarkdownDialog =
        apply { this.title = title }

    fun setContentFromAsset(context: Context, assetName: String): MarkdownDialog =
        apply { this.markdownContent = context.readStringFromAsset(assetName) }

    fun setContentFromText(markdown: String): MarkdownDialog =
        apply { this.markdownContent = markdown }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}