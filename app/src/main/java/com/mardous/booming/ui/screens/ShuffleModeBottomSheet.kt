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

package com.mardous.booming.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mardous.booming.service.queue.ShuffleManager
import com.mardous.booming.ui.components.ShuffleModeItem

@Composable
fun ShuffleModeBottomSheet(
    modes: Array<ShuffleManager.ShuffleMode>,
    onModeClick: (ShuffleManager.ShuffleMode) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)
    ) {
        items(modes) { mode ->
            ShuffleModeItem(
                mode = mode,
                onClick = {
                    onModeClick(mode)
                }
            )
        }
    }
}