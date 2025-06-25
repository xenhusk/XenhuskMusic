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

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mardous.booming.R
import com.mardous.booming.activities.base.AbsSlidingMusicPanelActivity
import com.mardous.booming.appshortcuts.DynamicShortcutManager
import com.mardous.booming.dialogs.UpdateDialog
import com.mardous.booming.extensions.currentFragment
import com.mardous.booming.extensions.navigation.isValidCategory
import com.mardous.booming.extensions.showToast
import com.mardous.booming.extensions.whichFragment
import com.mardous.booming.viewmodels.lyrics.LyricsViewModel
import com.mardous.booming.http.github.isAbleToUpdate
import com.mardous.booming.interfaces.IScrollHelper
import com.mardous.booming.model.CategoryInfo
import com.mardous.booming.mvvm.UpdateSearchResult
import com.mardous.booming.service.MusicPlayer
import com.mardous.booming.service.MusicService
import com.mardous.booming.util.PlayOnStartupMode
import com.mardous.booming.util.Preferences
import com.mardous.booming.viewmodels.PlaybackViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * @author Christians M. A. (mardous)
 */
class MainActivity : AbsSlidingMusicPanelActivity() {

    private val playbackViewModel: PlaybackViewModel by viewModel()
    private val lyricsViewModel: LyricsViewModel by viewModel()

    private lateinit var mediaBrowser: MediaBrowserCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = null

        updateTabs()
        setupNavigationController()

        // Set up dynamic shortcuts
        DynamicShortcutManager(this).initDynamicShortcuts()

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MusicService::class.java),
            connectionCallback,
            null
        )
        mediaBrowser.connect()

        libraryViewModel.getUpdateSearchEvent().observe(this) { result ->
            result.getContentIfNotConsumed()?.let {
                processUpdateSearchResult(it)
            }
        }

        if (savedInstanceState == null) {
            searchUpdate()
        }
    }

    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val token = mediaBrowser.sessionToken
            val mediaController = MediaControllerCompat(this@MainActivity, token)
            MediaControllerCompat.setMediaController(this@MainActivity, mediaController)
            playbackViewModel.setMediaController(mediaController)
        }

        override fun onConnectionFailed() {
            playbackViewModel.setMediaController(null)
        }
    }

    fun scanAllPaths() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.scan_media)
            .setMessage(R.string.scan_media_message)
            .setPositiveButton(R.string.scan_media_positive) { _, _ ->
                libraryViewModel.scanAllPaths(this).observe(this) {
                    // TODO show detailed info about scanned songs
                    showToast(R.string.scan_finished)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun searchUpdate() {
        if (isAbleToUpdate()) {
            libraryViewModel.searchForUpdate(false)
        }
    }

    private fun processUpdateSearchResult(result: UpdateSearchResult) {
        when (result.state) {
//            UpdateSearchResult.State.Searching -> {
//                if (result.wasFromUser) {
//                    showToast(R.string.checking_please_wait)
//                }
//            }
            UpdateSearchResult.State.Completed -> {
                val release = result.data ?: return
                if (result.wasFromUser || result.data.isDownloadable(this)) {
                    val existingDialog = supportFragmentManager.findFragmentByTag("UPDATE_FOUND")
                    if (existingDialog == null) {
                        UpdateDialog.create(release).show(supportFragmentManager, "UPDATE_FOUND")
                    }
                }
            }
            UpdateSearchResult.State.Failed -> {
                if (result.wasFromUser) {
                    showToast(R.string.could_not_check_for_updates)
                }
            }
            else -> {}
        }
    }

    private fun setupNavigationController() {
        val navController = whichFragment<NavHostFragment>(R.id.fragment_container).navController
        val navInflater = navController.navInflater
        val navGraph = navInflater.inflate(R.navigation.graph_main)

        val categoryInfo: CategoryInfo = Preferences.libraryCategories.first { it.visible }
        if (categoryInfo.visible) {
            val lastPage = Preferences.lastPage
            if (!navGraph.isValidCategory(lastPage)) {
                Preferences.lastPage = categoryInfo.category.id
                navGraph.setStartDestination(categoryInfo.category.id)
            } else {
                navGraph.setStartDestination(
                    if (Preferences.isRememberLastPage) {
                        lastPage.let {
                            if (it == 0) {
                                categoryInfo.category.id
                            } else {
                                it
                            }
                        }
                    } else categoryInfo.category.id
                )
            }
        }

        navController.graph = navGraph
        navigationView.setupWithNavController(navController)
        // Scroll Fragment to top
        navigationView.setOnItemReselectedListener {
            currentFragment(R.id.fragment_container).apply {
                if (this is IScrollHelper) {
                    scrollToTop()
                }
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == navGraph.startDestinationId) {
                currentFragment(R.id.fragment_container)?.enterTransition = null
            }
            if (destination.navigatorName == "dialog") {
                return@addOnDestinationChangedListener
            }
            when (destination.id) {
                R.id.nav_home,
                R.id.nav_songs,
                R.id.nav_albums,
                R.id.nav_artists,
                R.id.nav_folders,
                R.id.nav_playlists,
                R.id.nav_genres,
                R.id.nav_years -> {
                    // Save the last tab
                    if (Preferences.isRememberLastPage) {
                        saveTab(destination.id)
                    }
                    // Show Bottom Navigation Bar
                    setBottomNavVisibility(visible = true, animate = true)
                }

                R.id.nav_queue,
                R.id.nav_lyrics_editor,
                R.id.nav_play_info -> {
                    setBottomNavVisibility(visible = false, hideBottomSheet = true)
                }

                else -> setBottomNavVisibility(visible = false, animate = true) // Hide Bottom Navigation Bar
            }
        }
    }

    private fun saveTab(id: Int) {
        if (Preferences.libraryCategories.firstOrNull { it.category.id == id }?.visible == true) {
            Preferences.lastPage = id
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePlaybackIntent(intent, false)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        intent?.let { handlePlaybackIntent(it, true) }
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        playbackViewModel.updateSong(MusicPlayer.currentSong)
        lyricsViewModel.updateSong(MusicPlayer.currentSong)
    }

    override fun onPlaybackRestored() {
        super.onPlaybackRestored()
        if (Preferences.playOnStartupMode == PlayOnStartupMode.WITH_EXPANDED_PLAYER) {
            expandPanel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaBrowser.disconnect()
    }

    private fun handlePlaybackIntent(intent: Intent, canRestorePlayback: Boolean) {
        libraryViewModel.handleIntent(intent).observe(this) { result ->
            if (result.handled) {
                setIntent(Intent())
            } else if (canRestorePlayback) {
                libraryViewModel.restorePlayback()
            }
            if (result.failed) {
                showToast(R.string.unplayable_file)
            }
        }
    }
}