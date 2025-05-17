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

package com.mardous.booming.fragments.folders

import android.media.MediaScannerConnection
import android.os.Bundle
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.mardous.booming.R
import com.mardous.booming.adapters.SongFileAdapter
import com.mardous.booming.adapters.StorageAdapter
import com.mardous.booming.database.InclExclDao
import com.mardous.booming.database.InclExclEntity
import com.mardous.booming.databinding.FragmentFoldersBinding
import com.mardous.booming.extensions.dip
import com.mardous.booming.extensions.files.*
import com.mardous.booming.extensions.resources.*
import com.mardous.booming.extensions.showToast
import com.mardous.booming.extensions.toHtml
import com.mardous.booming.extensions.topLevelTransition
import com.mardous.booming.fragments.base.AbsMainActivityFragment
import com.mardous.booming.helper.menu.onSongMenu
import com.mardous.booming.helper.menu.onSongsMenu
import com.mardous.booming.interfaces.IFileCallbacks
import com.mardous.booming.interfaces.IScrollHelper
import com.mardous.booming.interfaces.IStorageDeviceCallback
import com.mardous.booming.misc.UpdateToastMediaScannerCompletionListener
import com.mardous.booming.misc.WrappedAsyncTaskLoader
import com.mardous.booming.model.Song
import com.mardous.booming.model.StorageDevice
import com.mardous.booming.service.MusicPlayer
import com.mardous.booming.util.FileUtil
import com.mardous.booming.util.Preferences
import com.mardous.booming.util.StorageUtil
import com.mardous.booming.views.BreadCrumbLayout
import com.mardous.booming.views.BreadCrumbLayout.Crumb
import com.mardous.booming.views.BreadCrumbLayout.SelectionCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.LinkedList

class FoldersFragment : AbsMainActivityFragment(R.layout.fragment_folders), SelectionCallback, IFileCallbacks,
    LoaderManager.LoaderCallbacks<List<File>>, IStorageDeviceCallback, IScrollHelper {

    private var _binding: FragmentFoldersBinding? = null
    private val binding get() = _binding!!
    private val inclExclDao: InclExclDao by inject<InclExclDao>()

    val toolbar: Toolbar get() = binding.appBarLayout.toolbar

    private var adapter: SongFileAdapter? = null
    private var storageAdapter: StorageAdapter? = null
    private val fileComparator = Comparator { lhs: File, rhs: File ->
        if (lhs.isDirectory && !rhs.isDirectory) {
            return@Comparator -1
        } else if (!lhs.isDirectory && rhs.isDirectory) {
            return@Comparator 1
        } else {
            return@Comparator lhs.name.compareTo(rhs.name, ignoreCase = true)
        }
    }
    private var storageItems: List<StorageDevice> = arrayListOf()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyWindowInsetsFromView(view)

        _binding = FragmentFoldersBinding.bind(view)
        mainActivity.setSupportActionBar(toolbar)
        mainActivity.supportActionBar?.title = null
        topLevelTransition(view)

        setUpBreadCrumbs()
        checkForMargins()
        setUpRecyclerView()
        setUpAdapter()
        setUpTitle()
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!handleBackPress()) {
                        remove()
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            })
        if (savedInstanceState == null) {
            switchToFileAdapter()
            setCrumb(Crumb(Preferences.startDirectory.getCanonicalFileSafe()), true)
        } else {
            binding.breadCrumbs.restoreFromStateWrapper(
                BundleCompat.getParcelable(savedInstanceState, CRUMBS, BreadCrumbLayout.SavedStateWrapper::class.java)
            )
            LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (_binding != null) {
            outState.putParcelable(CRUMBS, binding.breadCrumbs.stateWrapper)
        }
    }

    private fun setUpTitle() {
        toolbar.setNavigationOnClickListener {
            findNavController().navigate(R.id.nav_search, null, navOptions)
        }
        binding.appBarLayout.title = resources.getString(R.string.folders_label)
    }

    override fun onPause() {
        super.onPause()
        saveScrollPosition()
        adapter?.actionMode?.finish()
    }

    private fun handleBackPress(): Boolean {
        if (binding.breadCrumbs.popHistory()) {
            setCrumb(binding.breadCrumbs.lastHistory(), false)
            return true
        }
        return false
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<File>> {
        return AsyncFileLoader(this)
    }

    override fun onCrumbSelection(crumb: Crumb, index: Int) {
        setCrumb(crumb, true)
    }

    override fun fileMenuClick(file: File, view: View) {
        val popupMenu = PopupMenu(requireActivity(), view)
        if (file.isDirectory) {
            popupMenu.inflate(R.menu.menu_item_directory)
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.action_blacklist -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            inclExclDao.insertPath(InclExclEntity(file.getCanonicalPathSafe(), InclExclDao.BLACKLIST))
                        }
                        true
                    }

                    R.id.action_set_as_start_directory -> {
                        Preferences.startDirectory = file
                        showToast(getString(R.string.new_start_directory, file.path))
                        true
                    }

                    R.id.action_scan -> {
                        lifecycleScope.launch {
                            listPaths(file, AUDIO_FILE_FILTER) { paths -> scanPaths(paths) }
                        }
                        true
                    }

                    else -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            listSongs(listOf(file), AUDIO_FILE_FILTER, fileComparator) { songs ->
                                if (songs.isNotEmpty()) {
                                    songs.onSongsMenu(this@FoldersFragment, item)
                                }
                            }
                        }
                        true
                    }
                }
            }
        } else {
            popupMenu.inflate(R.menu.menu_item_file)
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.action_scan -> {
                        lifecycleScope.launch {
                            listPaths(file, AUDIO_FILE_FILTER) { paths -> scanPaths(paths) }
                        }
                        true
                    }

                    else -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            listSongs(listOf(file), AUDIO_FILE_FILTER, fileComparator) { songs ->
                                if (songs.isNotEmpty()) {
                                    val song = songs.first()
                                    song.onSongMenu(this@FoldersFragment, item)
                                }
                            }
                        }
                        true
                    }
                }
            }
        }
        popupMenu.show()
    }

    override fun fileSelected(file: File) {
        var mFile = file
        mFile = tryGetCanonicalFile(mFile) // important as we compare the path value later
        if (mFile.isDirectory) {
            setCrumb(Crumb(mFile), true)
        } else {
            val fileFilter = FileFilter { pathname: File ->
                !pathname.isDirectory && AUDIO_FILE_FILTER.accept(pathname)
            }
            lifecycleScope.launch(Dispatchers.IO) {
                listSongs(listOfNotNull(mFile.parentFile), fileFilter, fileComparator) { songs ->
                    if (songs.isNotEmpty()) {
                        var startIndex = -1
                        for (i in songs.indices) {
                            if (mFile.path == songs[i].data) { // path is already canonical here
                                startIndex = i
                                break
                            }
                        }
                        if (startIndex > -1) {
                            MusicPlayer.openQueue(songs, startIndex, true)
                        } else {
                            Snackbar.make(
                                mainActivity.slidingPanel,
                                getString(R.string.not_listed_in_media_store, mFile.name).toHtml(),
                                Snackbar.LENGTH_LONG
                            ).setAction(R.string.action_scan) {
                                lifecycleScope.launch {
                                    listPaths(mFile, AUDIO_FILE_FILTER) { paths -> scanPaths(paths) }
                                }
                            }.show()
                        }
                    }
                }
            }
        }
    }

    override fun onLoadFinished(loader: Loader<List<File>>, data: List<File>) {
        updateAdapter(data)
    }

    override fun onLoaderReset(loader: Loader<List<File>>) {
        updateAdapter(LinkedList())
    }

    override fun filesMenuClick(item: MenuItem, files: List<File>) {
        lifecycleScope.launch(Dispatchers.IO) {
            listSongs(files, AUDIO_FILE_FILTER, fileComparator) { songs ->
                if (songs.isNotEmpty()) {
                    songs.onSongsMenu(this@FoldersFragment, item)
                }
            }
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(0, R.id.action_scan, 0, R.string.scan_media)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, R.id.action_go_to_start_directory, 1, R.string.action_go_to_start_directory)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, R.id.action_settings, 2, R.string.settings_title)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.removeItem(R.id.action_grid_size)
        menu.removeItem(R.id.action_view_type)
        menu.removeItem(R.id.action_sort_order)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_go_to_start_directory -> {
                setCrumb(Crumb(Preferences.startDirectory.getCanonicalFileSafe()), true)
                return true
            }

            R.id.action_scan -> {
                val crumb = activeCrumb
                if (crumb != null) {
                    lifecycleScope.launch {
                        listPaths(crumb.file, AUDIO_FILE_FILTER) { paths -> scanPaths(paths) }
                    }
                }
                return true
            }

            R.id.action_settings -> {
                findNavController().navigate(R.id.nav_settings, null, navOptions)
                return true
            }
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        checkForMargins()
    }

    private fun checkForMargins() {
        if (mainActivity.isBottomNavVisible) {
            binding.recyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = dip(R.dimen.bottom_nav_height)
            }
        }
    }

    private fun checkIsEmpty() {
        if (_binding != null) {
            binding.emptyEmoji.text = getEmojiByUnicode(0x1F631)
            binding.empty.isVisible = adapter?.itemCount == 0
        }
    }

    private val activeCrumb: Crumb?
        get() = if (_binding != null) {
            if (binding.breadCrumbs.size() > 0) binding.breadCrumbs.getCrumb(binding.breadCrumbs.activeIndex) else null
        } else null

    private fun getEmojiByUnicode(unicode: Int): String {
        return String(Character.toChars(unicode))
    }

    private fun saveScrollPosition() {
        activeCrumb?.scrollPosition =
            (binding.recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
    }

    private fun scanPaths(toBeScanned: Array<String?>) {
        if (activity == null) {
            return
        }
        if (toBeScanned.isEmpty()) {
            showToast(R.string.nothing_to_scan)
        } else {
            MediaScannerConnection.scanFile(
                requireContext(),
                toBeScanned,
                null,
                UpdateToastMediaScannerCompletionListener(requireActivity(), listOf(*toBeScanned))
            )
        }
    }

    private fun setCrumb(crumb: Crumb?, addToHistory: Boolean) {
        if (crumb == null) {
            return
        }
        val path = crumb.file.path
        if (path == "/" || path == "/storage" || path == "/storage/emulated") {
            switchToStorageAdapter()
        } else {
            saveScrollPosition()
            binding.breadCrumbs.setActiveOrAdd(crumb, false)
            if (addToHistory) {
                binding.breadCrumbs.addHistory(crumb)
            }
            LoaderManager.getInstance(this).restartLoader(LOADER_ID, null, this)
        }
    }

    private fun setUpAdapter() {
        switchToFileAdapter()
    }

    private fun setUpBreadCrumbs() {
        binding.breadCrumbs.setActivatedContentColor(textColorPrimary())
        binding.breadCrumbs.setDeactivatedContentColor(textColorSecondary())
        binding.breadCrumbs.setCallback(this)
    }

    private fun setUpRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.createFastScroller()
    }

    private fun updateAdapter(files: List<File>) {
        adapter?.swapDataSet(files)
        val crumb = activeCrumb
        if (crumb != null) {
            (binding.recyclerView.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(crumb.scrollPosition, 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private suspend fun listPaths(
        file: File,
        fileFilter: FileFilter,
        doOnPathListed: (paths: Array<String?>) -> Unit,
    ) {
        val paths = try {
            val paths: Array<String?>
            if (file.isDirectory) {
                val files = file.listFilesDeep(fileFilter)
                paths = arrayOfNulls(files.size)
                for (i in files.indices) {
                    val f = files[i]
                    paths[i] = f.getCanonicalPathSafe()
                }
            } else {
                paths = arrayOfNulls(1)
                paths[0] = file.path
            }
            paths
        } catch (e: Exception) {
            e.printStackTrace()
            arrayOf()
        }
        withContext(Dispatchers.Main) {
            doOnPathListed(paths)
        }
    }

    private class AsyncFileLoader(foldersFragment: FoldersFragment) :
        WrappedAsyncTaskLoader<List<File>>(foldersFragment.requireActivity()) {

        private val fragmentWeakReference: WeakReference<FoldersFragment> =
            WeakReference(foldersFragment)

        override fun loadInBackground(): List<File> {
            val foldersFragment = fragmentWeakReference.get()
            var directory: File? = null
            if (foldersFragment != null) {
                val crumb = foldersFragment.activeCrumb
                if (crumb != null) {
                    directory = crumb.file
                }
            }
            return directory?.listFilesAsList(AUDIO_FILE_FILTER)?.sortedWith(foldersFragment!!.fileComparator)
                ?: LinkedList()
        }
    }

    private suspend fun listSongs(
        files: List<File>,
        fileFilter: FileFilter,
        fileComparator: Comparator<File>,
        doOnSongsListed: (songs: List<Song>) -> Unit,
    ) {
        val songs = try {
            FileUtil.matchFilesWithMediaStore(files.listFilesDeep(fileFilter).sortedWith(fileComparator))
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
        withContext(Dispatchers.Main) {
            doOnSongsListed(songs)
        }
    }

    override fun storageDeviceClick(storage: StorageDevice) {
        switchToFileAdapter()
        setCrumb(Crumb(storage.file.getCanonicalFileSafe()), true)
    }

    override fun scrollToTop() {
        binding.recyclerView.scrollToPosition(0)
        binding.appBarLayout.setExpanded(true, true)
    }

    private fun switchToFileAdapter() {
        adapter = SongFileAdapter(mainActivity, LinkedList(), R.layout.item_list, this)
        adapter!!.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    super.onChanged()
                    checkIsEmpty()
                }
            })
        binding.recyclerView.adapter = adapter
        binding.breadCrumbs.show()
        checkIsEmpty()
    }

    private fun switchToStorageAdapter() {
        storageItems = StorageUtil.storageVolumes
        storageAdapter = StorageAdapter(storageItems, this)
        binding.recyclerView.adapter = storageAdapter
        binding.breadCrumbs.clearCrumbs()
        binding.breadCrumbs.hide()
    }

    companion object {
        val TAG: String = FoldersFragment::class.java.simpleName

        val AUDIO_FILE_FILTER = FileFilter { file: File ->
            (!file.isHidden && (file.isDirectory
                    || file.isMimeType("audio/*")
                    || file.isMimeType("application/opus")
                    || file.isMimeType("application/ogg")))
        }

        private const val CRUMBS = "crumbs"
        private const val LOADER_ID = 5

        private fun tryGetCanonicalFile(file: File): File {
            return try {
                file.canonicalFile
            } catch (e: IOException) {
                e.printStackTrace()
                file
            }
        }
    }
}