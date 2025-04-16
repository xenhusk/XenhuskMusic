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

package com.mardous.booming.model

import android.content.Context
import com.google.gson.annotations.Expose
import com.mardous.booming.R

/**
 * @author Christians M. A. (mardous)
 */
data class EQPreset(
    @Expose val name: String,
    @Expose val levels: IntArray,
    @Expose val effects: HashMap<String, Float> = hashMapOf(),
    val isCustom: Boolean = false
) {

    constructor(source: EQPreset, name: String, isCustom: Boolean) :
            this(name, source.levels.copyOf(), HashMap(source.effects), isCustom)

    val isValid: Boolean
        get() = name.isNotBlank() && levels.isNotEmpty()

    val numberOfBands: Int
        get() = levels.size

    fun getName(context: Context): String {
        if (isCustom) {
            return context.getString(R.string.custom)
        }
        return name
    }

    fun hasEffect(effect: String): Boolean {
        if (!isValid) return false

        return effects.containsKey(effect)
    }

    fun getEffect(effect: String, default: Float = 0f): Float {
        if (!isValid) return 0f

        return effects.getOrDefault(effect, default)
    }

    fun setEffect(effect: String, value: Float) {
        if (!isValid) return

        effects[effect] = value
    }

    fun removeEffect(effect: String): Boolean {
        if (!isValid) return false

        return effects.remove(effect) != null
    }

    fun getLevelShort(band: Int): Short {
        if (!isValid) return 0

        if (band >= 0 && band < levels.size) {
            return levels[band].toShort()
        }
        throw IllegalArgumentException("Invalid band: $band")
    }

    fun setBandLevel(band: Int, level: Int) {
        if (!isValid) return

        if (band >= 0 && band < levels.size) {
            levels[band] = level
        } else {
            throw IllegalArgumentException("Invalid band: $band")
        }
    }

    fun areSameLevels(other: EQPreset?): Boolean {
        if (other == null) return false
        return levels.contentEquals(other.levels)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EQPreset

        if (name != other.name) return false
        if (!levels.contentEquals(other.levels)) return false
        if (effects != other.effects) return false
        if (isCustom != other.isCustom) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + levels.contentHashCode()
        result = 31 * result + effects.hashCode()
        result = 31 * result + isCustom.hashCode()
        return result
    }

    companion object {
        fun getEmptyPreset(name: String, isCustom: Boolean, numBands: Int): EQPreset {
            val flatLevels = IntArray(numBands) { 0 }
            return EQPreset(name, flatLevels, isCustom = isCustom)
        }
    }
}