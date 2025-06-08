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

package com.mardous.booming.fragments.about

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.mardous.booming.BuildConfig
import com.mardous.booming.R
import com.mardous.booming.extensions.*
import com.mardous.booming.model.DeviceInfo
import com.mardous.booming.ui.screens.AboutScreen
import com.mardous.booming.ui.screens.LicensesDialog
import com.mardous.booming.ui.screens.ReportBugsDialog
import com.mardous.booming.ui.theme.BoomingMusicTheme
import com.mardous.booming.util.Preferences

/**
 * @author Christians M. A. (mardous)
 */
class AboutFragment : Fragment() {

    private lateinit var deviceInfo: DeviceInfo

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        deviceInfo = DeviceInfo(requireActivity())
        val appLicenses = requireContext().readStringFromAsset("LICENSES.md")
        return ComposeView(requireContext()).apply {
            setContent {
                BoomingMusicTheme(dynamicColor = Preferences.materialYou) {
                    var showReportDialog by remember { mutableStateOf(false) }
                    var showLicensesDialog by remember { mutableStateOf(false) }

                    AboutScreen(
                        appVersion = BuildConfig.VERSION_NAME,
                        onBackClick = {
                            getOnBackPressedDispatcher().onBackPressed()
                        },
                        onChangelogClick = {
                            openUrl(RELEASES_LINK)
                        },
                        onForkClick = {
                            openUrl(GITHUB_URL)
                        },
                        onLicensesClick = {
                            showLicensesDialog = true
                        },
                        onTelegramClick = {
                            openUrl(AUTHOR_TELEGRAM_LINK)
                        },
                        onGitHubClick = {
                            openUrl(AUTHOR_GITHUB_URL)
                        },
                        onEmailClick = {
                            sendEmail()
                        },
                        onTranslatorsClick = {
                            findNavController().navigate(R.id.nav_translators)
                        },
                        onTranslateClick = {
                            openUrl(CROWDIN_PROJECT_LINK)
                        },
                        onJoinChatClick = {
                            openUrl(APP_TELEGRAM_LINK)
                        },
                        onShareAppClick = {
                            sendInvitationMessage()
                        },
                        onReportBugsClick = {
                            showReportDialog = true
                        }
                    )

                    if (showReportDialog) {
                        ReportBugsDialog(
                            onDismiss = {
                                showReportDialog = false
                            },
                            onContinue = {
                                showReportDialog = false
                                openIssueTracker()
                            }
                        )
                    }

                    if (showLicensesDialog) {
                        LicensesDialog(
                            licensesContent = appLicenses ?: "",
                            onDismiss = { showLicensesDialog = false }
                        )
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        materialSharedAxis(view)
    }

    private fun openUrl(url: String) {
        startActivity(url.openWeb())
    }

    private fun sendInvitationMessage() {
        val intent = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.invitation_message_content, "${GITHUB_URL}/releases"))
            .setType(MIME_TYPE_PLAIN_TEXT)

        startActivity(Intent.createChooser(intent, getString(R.string.send_invitation_message)))
    }

    private fun sendEmail() {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf("mardous.contact@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "${getString(R.string.app_name)} - Support & questions")
        }
        startActivity(Intent.createChooser(emailIntent, getString(R.string.write_an_email)))
    }

    private fun openIssueTracker() {
        try {
            startActivity(ISSUE_TRACKER_LINK.openWeb())
            copyDeviceInfoToClipBoard()
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun copyDeviceInfoToClipBoard() {
        val clipboard = requireContext().getSystemService<ClipboardManager>()
        if (clipboard != null) {
            val clip =
                ClipData.newPlainText(getString(R.string.device_info), deviceInfo.toMarkdown())
            clipboard.setPrimaryClip(clip)
        }
        showToast(R.string.copied_device_info_to_clipboard, Toast.LENGTH_LONG)
    }

    companion object {
        private const val AUTHOR_GITHUB_URL = "https://www.github.com/mardous"
        private const val GITHUB_URL = "$AUTHOR_GITHUB_URL/BoomingMusic"
        private const val RELEASES_LINK = "$GITHUB_URL/releases"
        private const val ISSUE_TRACKER_LINK = "$GITHUB_URL/issues"
        private const val AUTHOR_TELEGRAM_LINK = "https://t.me/mardeez"
        private const val APP_TELEGRAM_LINK = "https://t.me/mardousdev"
        private const val CROWDIN_PROJECT_LINK = "https://crowdin.com/project/booming-music"
    }
}