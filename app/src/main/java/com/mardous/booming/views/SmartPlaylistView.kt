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

package com.mardous.booming.views

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.textview.MaterialTextView
import com.mardous.booming.R
import com.mardous.booming.extensions.dp
import com.google.android.material.R as M3R

class SmartPlaylistView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        descendantFocusability = FOCUS_AFTER_DESCENDANTS

        val a = context.obtainStyledAttributes(attrs, R.styleable.SmartPlaylistView)

        val iconView = ShapeableImageView(context).apply {
            layoutParams = LayoutParams(56.dp(context), 56.dp(context))
            scaleType = ImageView.ScaleType.CENTER
            shapeAppearanceModel = ShapeAppearanceModel().toBuilder()
                .setAllCornerSizes(ShapeAppearanceModel.PILL)
                .build()
            background = ContextCompat.getDrawable(context, android.R.color.white)
            backgroundTintList = if (a.hasValue(R.styleable.SmartPlaylistView_playlistIconShapeTint)) {
                a.getColorStateList(R.styleable.SmartPlaylistView_playlistIconShapeTint)
            } else {
                AppCompatResources.getColorStateList(context, R.color.smart_playlist_shape_color)
            }
            imageTintList = if (a.hasValue(R.styleable.SmartPlaylistView_playlistIconTint)) {
                a.getColorStateList(R.styleable.SmartPlaylistView_playlistIconTint)
            } else {
                MaterialColors.getColorStateListOrNull(context, M3R.attr.colorOnSurfaceVariant)
            }
            setImageDrawable(a.getDrawable(R.styleable.SmartPlaylistView_playlistIcon))
        }

        val materialTextView = MaterialTextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                .apply { topMargin = 4.dp(context) }
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 2
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            text = a.getString(R.styleable.SmartPlaylistView_playlistName)
            TextViewCompat.setTextAppearance(this, M3R.style.TextAppearance_Material3_LabelMedium)
        }

        a.recycle()

        addView(iconView)
        addView(materialTextView)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            val iconView = getChildAt(0)
            if (iconView is ShapeableImageView) {
                iconView.drawableHotspotChanged(event.x, event.y)
            }
        }
        return super.onTouchEvent(event)
    }
}