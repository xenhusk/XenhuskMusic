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

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.mardous.booming.R
import com.mardous.booming.activities.base.AbsMusicServiceActivity
import com.mardous.booming.databinding.ActivityPermissionBinding
import com.mardous.booming.extensions.hasS
import com.mardous.booming.extensions.resources.primaryColor

/**
 * @author Christians M. A. (mardous)
 */
class PermissionsActivity : AbsMusicServiceActivity() {

    private var _binding: ActivityPermissionBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appName = getString(R.string.app_name_long).trim()
        val styledAppName = SpannableStringBuilder(getString(R.string.welcome_to_x, appName).trim()).apply {
            setSpan(StyleSpan(Typeface.BOLD), this.indexOf(appName), length, SPAN_INCLUSIVE_INCLUSIVE)
            setSpan(ForegroundColorSpan(primaryColor()), this.lastIndexOf(" "), length, SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        _binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.welcomeLabel.text = styledAppName
        binding.storageAccess.setNumber(1)
        if (hasS()) {
            binding.nearbyDevices.setNumber(2)
            binding.scheduleExactAlarms.setNumber(3)
            binding.ringtone.setNumber(4)
        } else {
            binding.scheduleExactAlarms.isVisible = false
            binding.nearbyDevices.isVisible = false
            binding.ringtone.setNumber(2)
        }
        binding.storageAccess.setButtonOnClickListener {
            requestPermissions()
        }
        if (binding.nearbyDevices.isVisible) {
            binding.nearbyDevices.setButtonOnClickListener {
                if (!binding.nearbyDevices.isGranted() && hasS()) {
                    ActivityCompat.requestPermissions(this, arrayOf(BLUETOOTH_CONNECT), BLUETOOTH_PERMISSION_REQUEST)
                }
            }
        }
        if (binding.scheduleExactAlarms.isVisible) {
            binding.scheduleExactAlarms.setButtonOnClickListener {
                if (!binding.scheduleExactAlarms.isGranted() && hasS()) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                }
            }
        }
        binding.ringtone.setButtonOnClickListener {
            if (!binding.ringtone.isGranted()) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = ("package:" + applicationContext.packageName).toUri()
                startSettingsActivity(intent)
            }
        }
        binding.finish.setOnClickListener {
            if (hasPermissions()) {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAffinity()
                remove()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun startSettingsActivity(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun checkPermissions() {
        binding.storageAccess.setGranted(hasExternalStoragePermission())
        binding.ringtone.setGranted(canWriteSettings())
        if (hasS()) {
            binding.nearbyDevices.setGranted(hasNearbyDevicesPermission())
            binding.scheduleExactAlarms.setGranted(canScheduleExactAlarms())
        }
        binding.finish.isEnabled = binding.storageAccess.isGranted() && (!hasS() || binding.nearbyDevices.isGranted())
    }

    private fun hasExternalStoragePermission(): Boolean {
        return hasPermissions()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasNearbyDevicesPermission(): Boolean =
        checkSelfPermission(BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @RequiresApi(Build.VERSION_CODES.S)
    private fun canScheduleExactAlarms(): Boolean =
        getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true

    private fun canWriteSettings(): Boolean = Settings.System.canWrite(this)
}