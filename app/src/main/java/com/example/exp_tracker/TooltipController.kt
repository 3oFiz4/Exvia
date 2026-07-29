package com.example.exp_tracker

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.abs

class TooltipController(
    private val context: Context,
    private val primaryColor: () -> Int,
    private val backgroundColor: () -> Int,
    private val textColor: () -> Int,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var popup: PopupWindow? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    /**
     * Shows a tooltip only after a genuine stationary hold.
     *
     * A normal tap must never schedule a tooltip that survives ACTION_UP. The
     * delayed callback therefore verifies the local active-press flag before
     * displaying anything. Moving beyond touch slop,
     * adding another pointer, releasing, or cancellation invalidates the hold.
     */
    fun attachHold(view: View, textProvider: () -> String, holdMs: Long = 2_000L) {
        var active = false
        var fired = false
        var downX = 0f
        var downY = 0f
        // Non-input TextViews do not necessarily keep receiving touch events when
        // their listener returns false. Consume those gestures ourselves and replay
        // a short tap with performClick(); EditText keeps its native touch handling.
        val consumeGesture = view !is EditText

        val runnable = Runnable {
            if (!active) return@Runnable
            fired = true
            show(view, textProvider())
        }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(runnable)
                    active = true
                    fired = false
                    downX = event.x
                    downY = event.y
                    handler.postDelayed(runnable, holdMs)
                    return@setOnTouchListener consumeGesture
                }
                MotionEvent.ACTION_MOVE -> {
                    if (active && (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)) {
                        active = false
                        handler.removeCallbacks(runnable)
                    }
                    return@setOnTouchListener consumeGesture
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    active = false
                    handler.removeCallbacks(runnable)
                    return@setOnTouchListener consumeGesture
                }
                MotionEvent.ACTION_UP -> {
                    active = false
                    handler.removeCallbacks(runnable)
                    if (consumeGesture && !fired) view.performClick()
                    return@setOnTouchListener consumeGesture
                }
                MotionEvent.ACTION_CANCEL -> {
                    active = false
                    handler.removeCallbacks(runnable)
                    return@setOnTouchListener consumeGesture
                }
            }
            consumeGesture
        }
    }

    fun show(anchor: View, text: String) {
        if (text.isBlank()) return
        popup?.dismiss()
        val density = context.resources.displayMetrics.density
        val label = TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(textColor())
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            maxWidth = (300 * density).toInt()
            AppFonts.apply(this)
            background = GradientDrawable().apply {
                setColor(backgroundColor())
                setStroke(maxOf(1, density.toInt()), primaryColor())
                cornerRadius = 4 * density
            }
        }
        val p = PopupWindow(label, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            isOutsideTouchable = true
            elevation = 8 * density
        }
        label.measure(
            View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(context.resources.displayMetrics.heightPixels, View.MeasureSpec.AT_MOST),
        )
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val screenHeight = context.resources.displayMetrics.heightPixels
        val anchorCenterY = location[1] + anchor.height / 2
        if (anchorCenterY < screenHeight / 2) {
            p.showAsDropDown(anchor, 0, (4 * density).toInt(), Gravity.START)
        } else {
            val y = location[1] - label.measuredHeight - (4 * density).toInt()
            p.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.START, location[0], y.coerceAtLeast(0))
        }
        popup = p
    }
}
