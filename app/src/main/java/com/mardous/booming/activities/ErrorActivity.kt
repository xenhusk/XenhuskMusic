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

package com.mardous.booming.activities

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.core.content.FileProvider
import cat.ereza.customactivityoncrash.CustomActivityOnCrash
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mardous.booming.R
import com.mardous.booming.activities.base.AbsThemeActivity
import com.mardous.booming.appContext
import com.mardous.booming.databinding.ActivityErrorBinding
import com.mardous.booming.extensions.fileProviderAuthority
import com.mardous.booming.extensions.files.asFormattedFileTime
import java.io.File

/**
 * @author Christians M. A. (mardous)
 */
class ErrorActivity : AbsThemeActivity() {

    private var _binding: ActivityErrorBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = CustomActivityOnCrash.getConfigFromIntent(intent)
        if (config == null) {
            finish()
            return
        }
        val errorReport = CustomActivityOnCrash.getAllErrorDetailsFromIntent(this, intent)
        val nameFromTime = System.currentTimeMillis().asFormattedFileTime()
        val errorReportFile = File(appContext().cacheDir, "Crash_${nameFromTime}.log")
        if (!errorReportFile.exists() || errorReportFile.delete()) {
            errorReportFile.writeText(errorReport)
        }

        _binding = ActivityErrorBinding.inflate(layoutInflater)
        binding.restartApp.setOnClickListener {
            CustomActivityOnCrash.restartApplication(this, config)
        }
        binding.viewReport.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage(errorReport)
                .setPositiveButton(R.string.uncaught_error_send) { _: DialogInterface, _: Int ->
                    sendFile(errorReportFile)
                }
                .setNegativeButton(R.string.close_action, null)
                .show()
        }
        setContentView(binding.root)
    }

    private fun sendFile(file: File) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_SUBJECT, "${getString(R.string.app_name)} - crash log")
        intent.putExtra(Intent.EXTRA_TEXT, "Please, add a description of the problem")

        val fileUri = FileProvider.getUriForFile(this, fileProviderAuthority, file)
        intent.putExtra(Intent.EXTRA_STREAM, fileUri)

        startActivity(Intent.createChooser(intent, "Send mail"))
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}