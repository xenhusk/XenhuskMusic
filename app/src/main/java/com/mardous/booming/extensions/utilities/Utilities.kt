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

package com.mardous.booming.extensions.utilities

import kotlinx.serialization.json.Json
import java.text.DecimalFormat
import java.util.Locale

const val DEFAULT_INFO_DELIMITER = " • "

fun buildInfoString(vararg parts: String?, delimiter: String = DEFAULT_INFO_DELIMITER): String {
    val sb = StringBuilder()
    if (parts.isNotEmpty()) {
        for (part in parts) {
            if (part.isNullOrEmpty()) {
                continue
            }
            if (sb.isNotEmpty()) {
                sb.append(delimiter)
            }
            sb.append(part)
        }
    }
    return sb.toString()
}

fun StringBuilder.appendWithDelimiter(text: CharSequence, delimiter: String = DEFAULT_INFO_DELIMITER) =
    apply {
        if (isNotEmpty()) {
            append(delimiter)
        }
        append(text)
    }

fun Int.formatTime() = toLong().formatTime()

fun Long.formatTime() = String.format(
    Locale.getDefault(),
    "%d:%02d.%03d",
    this / 1000 / 60, //minutes
    this / 1000 % 60, //seconds
    this % 100
)

fun Int.isInRange(from: Int, to: Int): Boolean = this in from until to

fun <T> List<T>.toMutableListIfRequired(): MutableList<T> = if (this is MutableList) this else toMutableList()

fun Float.formatted(): String {
    var value = this
    val arr = arrayOf("", "K", "M", "B", "T", "P", "E")
    var index = 0
    while (value / 1000 >= 1) {
        value /= 1000
        index++
    }
    val decimalFormat = DecimalFormat("#.##")
    return String.format("%s %s", decimalFormat.format(value.toDouble()), arr[index])
}

inline fun <reified T : Enum<T>> String.toEnum() =
    enumValues<T>().firstOrNull { it.name.equals(this, ignoreCase = true) }

inline fun <reified T> String?.deserialize(defaultValue: T): T {
    return if (!isNullOrEmpty())
        runCatching<T> { Json.decodeFromString(this) }.getOrDefault(defaultValue)
    else defaultValue
}

inline fun <reified T> T.serialize(): String = Json.encodeToString(this)