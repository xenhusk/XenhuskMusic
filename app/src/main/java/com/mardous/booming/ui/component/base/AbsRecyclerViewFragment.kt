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

package com.mardous.booming.ui.component.base

import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mardous.booming.R
import com.mardous.booming.core.model.MediaEvent
import com.mardous.booming.databinding.FragmentMainRecyclerBinding
import com.mardous.booming.extensions.*
import com.mardous.booming.extensions.resources.createFastScroller
import com.mardous.booming.extensions.resources.onVerticalScroll
import com.mardous.booming.ui.IScrollHelper
import com.mardous.booming.ui.dialogs.playlists.ImportPlaylistDialog
import com.mardous.booming.ui.screen.other.ShuffleModeFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScroller
import org.koin.android.ext.android.inject

abstract class AbsRecyclerViewFragment<A : RecyclerView.Adapter<*>, LM : RecyclerView.LayoutManager> :
    AbsMainActivityFragment(R.layout.fragment_main_recycler), IScrollHelper {

    private var _binding: FragmentMainRecyclerBinding? = null
    private val binding get() = _binding!!

    protected var adapter: A? = null
    protected var layoutManager: LM? = null

    val toolbar: Toolbar get() = binding.appBarLayout.toolbar
    val shuffleButton: FloatingActionButton get() = binding.shuffleButton

    abstract val isShuffleVisible: Boolean
    abstract val titleRes: Int

    protected val sharedPreferences: SharedPreferences by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyWindowInsetsFromView(view)

        _binding = FragmentMainRecyclerBinding.bind(view)

        topLevelTransition(view)
        setSupportActionBar(toolbar)

        initLayoutManager()
        initAdapter()
        checkForMargins()
        setUpRecyclerView()
        setupToolbar()

        // Add listeners when shuffle is visible
        if (isShuffleVisible) {
            binding.recyclerView.onVerticalScroll(
                viewLifecycleOwner,
                onScrollDown = { binding.shuffleButton.hide() },
                onScrollUp = { binding.shuffleButton.show() }
            )
            binding.shuffleButton.apply {
                setOnClickListener {
                    onShuffleClicked()
                }
                setOnLongClickListener {
                    onShuffleLongClick()
                }
            }
        } else {
            binding.shuffleButton.isVisible = false
        }

        libraryViewModel.getMiniPlayerMargin().observe(viewLifecycleOwner) {
            binding.recyclerView.updatePadding(bottom = it.totalMargin)
        }
        libraryViewModel.getFabMargin().observe(viewLifecycleOwner) {
            binding.shuffleButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = it.totalMargin
            }
        }
        viewLifecycleOwner.launchAndRepeatWithViewLifecycle {
            playerViewModel.mediaEvent.collect {
                if (it == MediaEvent.PlaybackStarted) {
                    whichFragment<DialogFragment>("SHUFFLE_MODE")
                        ?.dismissAllowingStateLoss()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        _binding?.shuffleButton?.doOnLayout {
            viewLifecycleOwner.lifecycleScope.launch {
                delay(1000)
                val balloon = createBoomingMusicBalloon("shuffle_button_tip") {
                    setDismissWhenClicked(true)
                    setText(getString(R.string.shuffle_button_tip))
                }
                _binding?.shuffleButton?.let {
                    if (it.isVisible && balloon?.isShowing == false) {
                        balloon.showAlignTop(it, yOff = (-8).dp(resources))
                    }
                }
            }
        }
    }

    open fun onShuffleClicked() {}

    open fun onShuffleLongClick(): Boolean {
        var shuffleModeFragment = whichFragment<ShuffleModeFragment>("SHUFFLE_MODE")
        if (shuffleModeFragment == null) {
            shuffleModeFragment = ShuffleModeFragment()
            shuffleModeFragment.show(childFragmentManager, "SHUFFLE_MODE")
            return true
        }
        return false
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            findNavController().navigate(R.id.nav_search)
        }
        val appName = resources.getString(titleRes)
        binding.appBarLayout.title = appName
    }

    private fun setUpRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = this@AbsRecyclerViewFragment.layoutManager
            adapter = this@AbsRecyclerViewFragment.adapter
            createFastScroller(this)
        }
    }

    protected open fun createFastScroller(recyclerView: RecyclerView): FastScroller {
        return recyclerView.createFastScroller()
    }

    private fun initAdapter() {
        adapter = createAdapter()
        adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                super.onChanged()
                checkIsEmpty()
            }
        })
    }

    protected open val emptyMessageRes: Int
        @StringRes get() = R.string.empty_label

    private fun checkIsEmpty() {
        binding.emptyText.setText(emptyMessageRes)
        binding.empty.isVisible = adapter!!.itemCount == 0
    }

    private fun checkForMargins() {
        checkForMargins(binding.recyclerView)
    }

    private fun initLayoutManager() {
        layoutManager = createLayoutManager()
    }

    protected abstract fun createLayoutManager(): LM

    protected abstract fun createAdapter(): A

    protected fun invalidateLayoutManager() {
        initLayoutManager()
        binding.recyclerView.layoutManager = layoutManager
    }

    protected fun invalidateAdapter() {
        initAdapter()
        checkIsEmpty()
        binding.recyclerView.adapter = adapter
    }

    val recyclerView get() = binding.recyclerView

    val container get() = binding.root

    override fun scrollToTop() {
        recyclerView.scrollToPosition(0)
        binding.appBarLayout.setExpanded(true, true)
    }

    override fun onPrepareMenu(menu: Menu) {
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_library, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> findNavController().navigate(R.id.nav_settings)
            R.id.action_scan -> mainActivity.scanAllPaths()
            R.id.action_import_playlist -> ImportPlaylistDialog().show(childFragmentManager, "IMPORT_PLAYLIST")
            R.id.action_classification -> findNavController().navigate(R.id.nav_classification)
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        checkForMargins()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        (adapter as? AbsMultiSelectAdapter<*, *>)?.actionMode?.finish()
    }
}