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

package com.mardous.booming.glide.palette;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.util.Util;

public class BitmapPaletteResource implements Resource<BitmapPaletteWrapper> {

  private final BitmapPaletteWrapper bitmapPaletteWrapper;

  public BitmapPaletteResource(BitmapPaletteWrapper bitmapPaletteWrapper) {
    this.bitmapPaletteWrapper = bitmapPaletteWrapper;
  }

  @NonNull
  @Override
  public BitmapPaletteWrapper get() {
    return bitmapPaletteWrapper;
  }

  @NonNull
  @Override
  public Class<BitmapPaletteWrapper> getResourceClass() {
    return BitmapPaletteWrapper.class;
  }

  @Override
  public int getSize() {
    return Util.getBitmapByteSize(bitmapPaletteWrapper.getBitmap());
  }

  @Override
  public void recycle() {
    bitmapPaletteWrapper.getBitmap().recycle();
  }
}
