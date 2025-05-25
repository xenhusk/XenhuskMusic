/*
 * Copyright (c) 2025 Christians Martínez Alvarado
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

package com.mardous.booming.util

import android.content.SharedPreferences
import androidx.core.content.edit
import com.mardous.booming.model.CategoryInfo
import com.mardous.booming.recordException
import com.mardous.booming.util.Preferences.getDefaultLibraryCategoryInfos
import kotlinx.serialization.json.*

typealias PreferenceMigration = (prefs: SharedPreferences, oldKey: String, newKey: String) -> Unit

object PreferenceMigrations {

    private val lenientJson by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    val LIBRARY_CATEGORIES: PreferenceMigration = { prefs, oldKey, newKey ->
        if (!migrationDone(prefs, oldKey, newKey)) {
            val raw = prefs.getString(oldKey, null)
            if (raw != null) {
                val jsonList = runCatching {
                    lenientJson.parseToJsonElement(raw).jsonArray
                }.getOrNull()

                val migrated = jsonList?.mapNotNull { element ->
                    try {
                        val obj = element.jsonObject
                        val categoryName = obj["category"]?.jsonPrimitive?.content
                        val visible = obj["visible"]?.jsonPrimitive?.booleanOrNull == true

                        if (categoryName == "Folders" && visible) {
                            CategoryInfo(CategoryInfo.Category.Files, true)
                        } else {
                            lenientJson.decodeFromJsonElement<CategoryInfo>(element)
                        }
                    } catch (e: Exception) {
                        recordException(e)
                        null
                    }
                } ?: getDefaultLibraryCategoryInfos()

                val migratedMutable = migrated.distinctBy { it.category }.toMutableList()
                if (migratedMutable.none { it.category == CategoryInfo.Category.Folders }) {
                    migratedMutable.add(CategoryInfo(CategoryInfo.Category.Folders, false))
                }

                prefs.edit {
                    putString(newKey, lenientJson.encodeToString(migratedMutable))
                    putBoolean(migrationDoneKey(newKey), true)
                    remove(oldKey)
                }
            }
        } else if (prefs.contains(oldKey)) {
            prefs.edit { remove(oldKey) }
        }
    }

    fun migrationDone(prefs: SharedPreferences, oldKey: String, newKey: String) =
        !prefs.contains(oldKey) || prefs.getBoolean(migrationDoneKey(newKey), false)

    fun migrationDoneKey(newKey: String) = "${newKey}_migrated"
}