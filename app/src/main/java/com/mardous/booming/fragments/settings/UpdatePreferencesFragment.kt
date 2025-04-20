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

package com.mardous.booming.fragments.settings

import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import com.mardous.booming.R
import com.mardous.booming.extensions.requestContext
import com.mardous.booming.extensions.utilities.dateStr
import com.mardous.booming.fragments.LibraryViewModel
import com.mardous.booming.mvvm.UpdateSearchResult
import com.mardous.booming.preferences.ProgressIndicatorPreference
import com.mardous.booming.util.Preferences
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class UpdatePreferencesFragment : PreferencesScreenFragment() {

    private val libraryViewModel: LibraryViewModel by activityViewModel()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_screen_update)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val preference = findPreference<ProgressIndicatorPreference>("search_for_update")
        defaultState(preference, Preferences.lastUpdateSearch)
        preference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            libraryViewModel.searchForUpdate(true)
            true
        }
        libraryViewModel.getUpdateSearchEvent().observe(viewLifecycleOwner) {
            val result = it.peekContent()
            when (result.state) {
                UpdateSearchResult.State.Searching -> {
                    preference?.showProgressIndicator()
                    preference?.isEnabled = false
                    preference?.summary = getString(R.string.searching_for_update)
                }
                else -> {
                    defaultState(preference, result.executedAtMillis)
                }
            }
        }
    }

    private fun defaultState(preference: ProgressIndicatorPreference?, lastUpdateSearch: Long) {
        requestContext {
            preference?.hideProgressIndicator()
            preference?.isEnabled = true
            preference?.summary = getString(R.string.last_update_search_x, it.dateStr(lastUpdateSearch))
        }
    }
}