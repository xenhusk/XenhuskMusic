package com.mardous.booming.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import com.mardous.booming.R

/**
 * Custom progress bar with water flowing effect
 */
class WaterProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ProgressBar(context, attrs, android.R.attr.progressBarStyleHorizontal) {

    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint1 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint2 = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var progress = 0f
    private var shimmerOffset = 0f
    private var waveOffset1 = 0f
    private var waveOffset2 = 0f
    private var cornerRadius = 24f
    
    private var shimmerAnimator: ValueAnimator? = null
    private var waveAnimator1: ValueAnimator? = null
    private var waveAnimator2: ValueAnimator? = null
    
    init {
        setupPaints()
        startAnimations()
    }
    
    private fun setupPaints() {
        // Water fill paint with horizontal gradient (left to right)
        waterPaint.apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                intArrayOf(
                    Color.parseColor("#4FC3F7"),
                    Color.parseColor("#29B6F6"),
                    Color.parseColor("#0288D1")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        
        // Shimmer effect paint (moves across the progress)
        shimmerPaint.apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat() * 0.3f, 0f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.parseColor("#60FFFFFF"),
                    Color.parseColor("#A0FFFFFF"),
                    Color.parseColor("#60FFFFFF"),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            alpha = 150
        }
        
        // Wave effect paint 1 (higher opacity) - using primary color
        wavePaint1.apply {
            color = Color.parseColor("#804D5C92") // Primary color with 50% opacity
            style = Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
        }
        
        // Wave effect paint 2 (lower opacity) - using primary container color
        wavePaint2.apply {
            color = Color.parseColor("#60DCE1FF") // Primary container color with 37% opacity
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
        }
        
        // Background paint
        backgroundPaint.apply {
            color = ContextCompat.getColor(context, R.color.surface_container_highest)
            style = Paint.Style.FILL
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cornerRadius = minOf(w, h) / 12f
        setupPaints() // Recreate shaders with new dimensions
    }
    
    override fun onDraw(canvas: Canvas) {
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        
        // Draw background
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, backgroundPaint)
        
        if (progress > 0) {
            // Calculate progress width (left to right)
            val progressWidth = bounds.width() * progress
            
            // Draw water fill (left to right)
            val waterBounds = RectF(
                bounds.left,
                bounds.top,
                bounds.left + progressWidth,
                bounds.bottom
            )
            canvas.drawRoundRect(waterBounds, cornerRadius, cornerRadius, waterPaint)
            
            // Draw shimmer effect on the progress area
            if (shimmerOffset > 0 && progressWidth > 0) {
                val shimmerStart = bounds.left + shimmerOffset
                val shimmerEnd = shimmerStart + progressWidth * 0.4f
                
                if (shimmerStart < bounds.left + progressWidth) {
                    val shimmerBounds = RectF(
                        shimmerStart.coerceAtLeast(bounds.left),
                        bounds.top,
                        shimmerEnd.coerceAtMost(bounds.left + progressWidth),
                        bounds.bottom
                    )
                    canvas.drawRoundRect(shimmerBounds, cornerRadius, cornerRadius, shimmerPaint)
                }
            }
            
            // Draw wave effects (two waves with different timing)
            if (progressWidth > 0) {
                val centerY = bounds.centerY()
                val waveAmplitude = bounds.height() * 0.15f
                
                // Wave 1 (higher opacity)
                val wavePath1 = Path()
                wavePath1.moveTo(bounds.left, centerY)
                for (x in 0..progressWidth.toInt() step 4) {
                    val waveX = bounds.left + x
                    val waveY = centerY + sin((waveX + waveOffset1) * 0.03) * waveAmplitude
                    wavePath1.lineTo(waveX, waveY)
                }
                canvas.drawPath(wavePath1, wavePaint1)
                
                // Wave 2 (lower opacity, different timing)
                val wavePath2 = Path()
                wavePath2.moveTo(bounds.left, centerY)
                for (x in 0..progressWidth.toInt() step 3) {
                    val waveX = bounds.left + x
                    val waveY = centerY + sin((waveX + waveOffset2) * 0.025) * (waveAmplitude * 0.7f)
                    wavePath2.lineTo(waveX, waveY)
                }
                canvas.drawPath(wavePath2, wavePaint2)
            }
        }
    }
    
    private fun sin(angle: Double): Float {
        return kotlin.math.sin(angle).toFloat()
    }
    
    fun setWaterProgress(progress: Float) {
        this.progress = progress.coerceIn(0f, 1f)
        invalidate()
    }
    
    private fun startAnimations() {
        // Shimmer animation
        shimmerAnimator = ValueAnimator.ofFloat(-width.toFloat(), width.toFloat()).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animator ->
                shimmerOffset = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
        
        // Wave animation 1 (faster)
        waveAnimator1 = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animator ->
                waveOffset1 = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
        
        // Wave animation 2 (slower, different timing)
        waveAnimator2 = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animator ->
                waveOffset2 = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    fun stopAnimations() {
        shimmerAnimator?.cancel()
        waveAnimator1?.cancel()
        waveAnimator2?.cancel()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimations()
    }
    
    override fun setProgress(progress: Int) {
        super.setProgress(progress)
        setWaterProgress(progress / 100f)
    }
}
