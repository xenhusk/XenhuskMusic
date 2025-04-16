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

package com.mardous.booming.preferences.dialog

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mardous.booming.R
import com.mardous.booming.databinding.PreferenceDialogNowPlayingScreenBinding
import com.mardous.booming.databinding.PreferenceNowPlayingScreenItemBinding
import com.mardous.booming.extensions.dp
import com.mardous.booming.model.theme.NowPlayingScreen
import com.mardous.booming.util.Preferences

class NowPlayingScreenPreferenceDialog : DialogFragment(), ViewPager.OnPageChangeListener {

    private var viewPagerPosition = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = PreferenceDialogNowPlayingScreenBinding.inflate(layoutInflater)

        binding.nowPlayingScreenViewPager.adapter = NowPlayingScreenAdapter(context)
        binding.nowPlayingScreenViewPager.addOnPageChangeListener(this)
        binding.nowPlayingScreenViewPager.pageMargin = 32.dp(resources)
        binding.nowPlayingScreenViewPager.currentItem = Preferences.nowPlayingScreen.ordinal

        binding.pageIndicator.setViewPager(binding.nowPlayingScreenViewPager)
        binding.pageIndicator.onPageSelected(binding.nowPlayingScreenViewPager.currentItem)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.now_playing_screen_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                Preferences.nowPlayingScreen = NowPlayingScreen.entries[viewPagerPosition]
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
    override fun onPageSelected(position: Int) {
        viewPagerPosition = position
    }

    override fun onPageScrollStateChanged(state: Int) {}

    private class NowPlayingScreenAdapter(private val context: Context?) : PagerAdapter() {

        override fun instantiateItem(collection: ViewGroup, position: Int): Any {
            val nowPlayingScreen = NowPlayingScreen.entries[position]
            val inflater = LayoutInflater.from(context)

            val binding = PreferenceNowPlayingScreenItemBinding.inflate(inflater)
            collection.addView(binding.root)

            binding.image.setImageResource(nowPlayingScreen.drawableResId)
            binding.title.setText(nowPlayingScreen.titleRes)
            return binding.root
        }

        override fun destroyItem(collection: ViewGroup, position: Int, view: Any) {
            collection.removeView(view as View)
        }

        override fun getCount(): Int {
            return NowPlayingScreen.entries.size
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view === `object`
        }

        override fun getPageTitle(position: Int): CharSequence? {
            return context?.getString(NowPlayingScreen.entries[position].titleRes)
        }
    }
}