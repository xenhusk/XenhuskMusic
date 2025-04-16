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

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mardous.booming.R
import com.mardous.booming.databinding.DialogUpdateInfoBinding
import com.mardous.booming.extensions.openUrl
import com.mardous.booming.extensions.resources.setMarkdownText
import com.mardous.booming.extensions.showToast
import com.mardous.booming.fragments.LibraryViewModel
import com.mardous.booming.http.github.GitHubRelease
import com.mardous.booming.util.Preferences
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class UpdateDialog : BottomSheetDialogFragment(), View.OnClickListener {

    private var _binding: DialogUpdateInfoBinding? = null
    private val binding get() = _binding!!
    private val viewModel by activityViewModel<LibraryViewModel>()

    private lateinit var release: GitHubRelease

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        release = BundleCompat.getParcelable(requireArguments(), EXTRA_RELEASE, GitHubRelease::class.java)!!
        if (release.isNewer(requireContext())) {
            _binding = DialogUpdateInfoBinding.inflate(layoutInflater)
            binding.infoAction.setOnClickListener(this)
            binding.downloadAction.setOnClickListener(this)
            fillVersionInfo()
            return BottomSheetDialog(requireContext()).also {
                it.setContentView(binding.root)
                it.setOnShowListener {
                    Preferences.lastUpdateSearch = System.currentTimeMillis()
                }
            }
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.the_app_is_up_to_date)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        release.setIgnored()
    }

    override fun onClick(view: View) {
        when (view) {
            binding.infoAction -> {
                requireContext().openUrl(release.url)
            }

            binding.downloadAction -> {
                viewModel.downloadUpdate(requireContext(), release)
                showToast(R.string.downloading_update)
                dismiss()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun fillVersionInfo() {
        binding.versionName.text = release.name
        if (release.body.isNotEmpty()) {
            binding.versionInfo.setMarkdownText(release.body)
        } else {
            binding.versionInfo.isVisible = false
        }
    }

    companion object {
        private const val EXTRA_RELEASE = "extra_release"

        fun create(release: GitHubRelease) = UpdateDialog().apply {
            arguments = bundleOf(EXTRA_RELEASE to release)
        }
    }
}