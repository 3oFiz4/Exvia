package com.example.exp_tracker

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView

class TooltipController(
    private val context: Context,
    private val primaryColor: () -> Int,
    private val backgroundColor: () -> Int,
    private val textColor: () -> Int,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var popup: PopupWindow? = null

    fun attachHold(view: View, textProvider: () -> String, holdMs: Long = 2_000L) {
        var fired = false
        val runnable = Runnable {
            fired = true
            show(view, textProvider())
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false
                    handler.postDelayed(runnable, holdMs)
                }
                MotionEvent.ACTION_MOVE -> Unit
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(runnable)
                    if (fired) true else false
                }
            }
            false
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
