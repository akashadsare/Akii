package com.videoplayer.akii

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

class SwipeGestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface GestureListener {
        fun onVolumeChange(percent: Float)
        fun onBrightnessChange(percent: Float)
        fun onSeekChange(percent: Float)
    }

    private var gestureListener: GestureListener? = null
    private var initialX: Float = 0f
    private var initialY: Float = 0f
    private var isHorizontalScroll = false
    private var isVerticalScroll = false
    private var isGestureStarted = false
    private var currentVolumePercent: Float = 0.5f  // Default to 50%
    private var currentBrightnessPercent: Float = 0.5f  // Default to 50%
    private var currentSeekPercent: Float = 0f

    // Reduced sensitivity factors (lower values = less sensitive)
    private val volumeSensitivity = 0.25f      // Reduced from 0.5f
    private val brightnessSensitivity = 0.25f  // Reduced from 0.5f
    private val seekSensitivity = 0.15f        // Reduced from 0.3f

    // Increased minimum distance to start a gesture
    private val minGestureDistance = 40f       // Increased from 20f

    // Minimum distance for gesture recognition
    private val minSwipeDistance = 30f

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            e1 ?: return false

            if (!isGestureStarted) {
                // Check if the movement is significant enough to start a gesture
                val deltaX = abs(e2.x - initialX)
                val deltaY = abs(e2.y - initialY)
                
                // Require more movement to start the gesture
                if (deltaX < minGestureDistance && deltaY < minGestureDistance) {
                    return false
                }

                // Determine gesture direction with more strict thresholds
                isGestureStarted = true
                isHorizontalScroll = deltaX > deltaY * 1.5f  // Require more horizontal movement
                isVerticalScroll = !isHorizontalScroll
                Log.d("SwipeGestureLayout", "Gesture started: horizontal=$isHorizontalScroll, vertical=$isVerticalScroll")
            }

            if (isHorizontalScroll) {
                // Handle horizontal seeking with reduced sensitivity
                val deltaX = e2.x - initialX
                // Only apply changes if the movement is significant
                if (abs(deltaX) >= minSwipeDistance) {
                    val widthPercent = (deltaX / width) * seekSensitivity
                    currentSeekPercent = (currentSeekPercent + widthPercent).coerceIn(0f, 1f)
                    Log.d("SwipeGestureLayout", "Horizontal scroll: $currentSeekPercent")
                    gestureListener?.onSeekChange(currentSeekPercent)
                }
            } else {
                // Handle vertical volume/brightness with reduced sensitivity
                val deltaY = initialY - e2.y
                // Only apply changes if the movement is significant
                if (abs(deltaY) >= minSwipeDistance) {
                    val heightPercent = (deltaY / height)
                    
                    if (e1.x < width / 2) {
                        // Left side - Brightness
                        val adjustedPercent = heightPercent * brightnessSensitivity
                        currentBrightnessPercent = (currentBrightnessPercent + adjustedPercent).coerceIn(0f, 1f)
                        Log.d("SwipeGestureLayout", "Vertical scroll (brightness): $currentBrightnessPercent")
                        gestureListener?.onBrightnessChange(currentBrightnessPercent)
                    } else {
                        // Right side - Volume
                        val adjustedPercent = heightPercent * volumeSensitivity
                        currentVolumePercent = (currentVolumePercent + adjustedPercent).coerceIn(0f, 1f)
                        Log.d("SwipeGestureLayout", "Vertical scroll (volume): $currentVolumePercent")
                        gestureListener?.onVolumeChange(currentVolumePercent)
                    }
                }
            }
            return true
        }
    })

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y
                isHorizontalScroll = false
                isVerticalScroll = false
                isGestureStarted = false
                Log.d("SwipeGestureLayout", "Touch down: x=$initialX, y=$initialY")
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isHorizontalScroll = false
                isVerticalScroll = false
                isGestureStarted = false
                Log.d("SwipeGestureLayout", "Touch up/cancel")
            }
        }
        return gestureDetector.onTouchEvent(event) || super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    fun setGestureListener(listener: GestureListener) {
        this.gestureListener = listener
    }

    fun setCurrentValues(volumePercent: Float, brightnessPercent: Float, seekPercent: Float) {
        currentVolumePercent = volumePercent.coerceIn(0f, 1f)
        currentBrightnessPercent = brightnessPercent.coerceIn(0f, 1f)
        currentSeekPercent = seekPercent.coerceIn(0f, 1f)
    }
} 
 