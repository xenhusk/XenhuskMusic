package com.mardous.booming.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.core.content.withStyledAttributes
import com.google.android.material.slider.Slider
import com.mardous.booming.R

/**
 * @author Christians M.A. (mardous)
 */
class MusicSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var listener: Listener? = null
    private var internalView: View? = null
    val progressView get() = internalView

    private val seekBar get() = internalView as? SeekBar
    private val slider get() = internalView as? Slider

    private var useSquiggly: Boolean = false

    var isTrackingTouch: Boolean = false
        private set

    var animateSquigglyProgress: Boolean
        get() = (seekBar?.progressDrawable as? SquigglyProgress)?.animate == true
        set(value) {
            (seekBar?.progressDrawable as? SquigglyProgress)?.animate = value
        }

    var valueFrom: Int
        set(valueFrom) {
            seekBar?.min = valueFrom
            slider?.valueFrom = valueFrom.toFloat()
        }
        get() = seekBar?.min ?: slider?.valueFrom?.toInt() ?: 0

    var valueTo: Int
        set(valueTo) {
            seekBar?.max = valueTo
            slider?.valueTo = valueTo.toFloat().coerceAtLeast(1f)
        }
        get() = seekBar?.max ?: slider?.valueTo?.toInt() ?: 0

    var value: Int
        set(value) {
            seekBar?.progress = value
            slider?.let { slider ->
                slider.value = value.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
            }
        }
        get() = seekBar?.progress ?: slider?.value?.toInt() ?: 0

    init {
        context.withStyledAttributes(attrs, R.styleable.MusicSlider) {
            useSquiggly = getBoolean(R.styleable.MusicSlider_squigglyStyle, false)
            inflateSliderView(
                useSquiggly = useSquiggly,
                progress = getInt(R.styleable.MusicSlider_android_progress, 0),
                max = getInt(R.styleable.MusicSlider_android_max, 100),
                min = getInt(R.styleable.MusicSlider_android_min, 0)
            )
        }
    }

    var trackActiveTintList: ColorStateList
        set(value) {
            seekBar?.progressTintList = value
            slider?.trackActiveTintList = value
        }
        get() = seekBar?.progressTintList ?: slider?.trackActiveTintList ?: ColorStateList.valueOf(Color.TRANSPARENT)

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detachInternalView()
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun setUseSquiggly(useSquiggly: Boolean) {
        if (useSquiggly != this.useSquiggly) {
            val progress = this.value
            val max = this.valueTo
            val min = this.valueFrom
            detachInternalView()
            inflateSliderView(useSquiggly, progress, max, min)
        }
        this.useSquiggly = useSquiggly
    }

    private fun detachInternalView() {
        removeAllViews()
        slider?.clearOnChangeListeners()
        slider?.clearOnSliderTouchListeners()
        seekBar?.setOnSeekBarChangeListener(null)
        internalView = null
    }

    private fun inflateSliderView(useSquiggly: Boolean, progress: Int, max: Int, min: Int) {
        internalView = if (useSquiggly) {
            LayoutInflater.from(context).inflate(R.layout.music_squiggly_slider, this, false)
        } else {
            LayoutInflater.from(context).inflate(R.layout.music_progress_slider, this, false)
        }
        when (val view = internalView) {
            is SeekBar -> {
                view.max = max
                view.min = min
                view.progress = progress
            }
            is Slider -> {
                view.valueTo = max.toFloat()
                view.valueFrom = min.toFloat()
                view.value = progress.toFloat()
            }
        }
        addView(internalView)
        setupInternalListener()
    }

    private fun setupInternalListener() {
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                listener?.onProgressChanged(this@MusicSlider, progress, fromUser)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isTrackingTouch = true
                listener?.onStartTrackingTouch(this@MusicSlider)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isTrackingTouch = false
                listener?.onStopTrackingTouch(this@MusicSlider)
            }
        })
        slider?.addOnChangeListener { slider, value, fromUser ->
            listener?.onProgressChanged(this, value.toInt(), fromUser)
        }
        slider?.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                isTrackingTouch = true
                listener?.onStartTrackingTouch(this@MusicSlider)
            }

            override fun onStopTrackingTouch(slider: Slider) {
                isTrackingTouch = false
                listener?.onStopTrackingTouch(this@MusicSlider)
            }
        })
    }

    interface Listener {
        fun onProgressChanged(slider: MusicSlider, progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch(slider: MusicSlider)
        fun onStopTrackingTouch(slider: MusicSlider)
    }
}
