package com.example.exp_tracker

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

class SwipeSettingsLayout(context: Context) : FrameLayout(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val edgeSize = (36 * resources.displayMetrics.density).toInt()
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var drawer: View? = null
    private var scrim: View? = null
    var isDrawerOpen: Boolean = false
        private set

    fun attachDrawer(drawerView: View, scrimView: View) {
        drawer = drawerView
        scrim = scrimView
        scrimView.setOnClickListener { closeDrawer() }
        post { applyDrawerPosition(animated = false) }
    }

    fun openDrawer() {
        isDrawerOpen = true
        applyDrawerPosition(animated = true)
    }

    fun closeDrawer() {
        isDrawerOpen = false
        applyDrawerPosition(animated = true)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                if (isDrawerOpen && drawer != null && event.x > drawer!!.width) return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val fromEdge = downX <= edgeSize
                if (abs(dx) > touchSlop && abs(dx) > abs(dy) && ((fromEdge && dx > 0) || isDrawerOpen)) {
                    dragging = true
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                dragging = true
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = event.x - downX
                if (dragging || abs(dx) > touchSlop) {
                    if (!isDrawerOpen && dx > 60 * resources.displayMetrics.density) openDrawer()
                    else if (isDrawerOpen && dx < -60 * resources.displayMetrics.density) closeDrawer()
                } else if (isDrawerOpen && drawer != null && event.x > drawer!!.width) {
                    closeDrawer()
                }
                dragging = false
                return true
            }
        }
        return true
    }

    private fun applyDrawerPosition(animated: Boolean) {
        val drawerView = drawer ?: return
        val scrimView = scrim ?: return
        val target = if (isDrawerOpen) 0f else -drawerView.width.toFloat()
        scrimView.visibility = if (isDrawerOpen) View.VISIBLE else View.GONE
        scrimView.setBackgroundColor(Color.argb(160, 0, 0, 0))
        if (animated) {
            drawerView.animate().translationX(target).setDuration(180).start()
            scrimView.animate().alpha(if (isDrawerOpen) 1f else 0f).setDuration(180).withEndAction {
                if (!isDrawerOpen) scrimView.visibility = View.GONE
            }.start()
        } else {
            drawerView.translationX = target
            scrimView.alpha = if (isDrawerOpen) 1f else 0f
        }
    }
}
