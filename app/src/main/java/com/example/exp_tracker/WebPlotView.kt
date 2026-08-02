package com.example.exp_tracker

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import org.json.JSONObject
import kotlin.math.abs

/** A lightweight Android host that borrows one pre-warmed WebView on demand. */
class WebPlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private var runtime: PlotWebRuntime? = null
    private var slot: PlotWebRuntime.Slot? = null
    private var payload: JSONObject? = null
    private var plotActive = true
    private var lastRenderedWidth = 0
    private var lastRenderedHeight = 0

    private val webLayoutListener = View.OnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        val width = right - left
        val height = bottom - top
        val oldWidth = oldRight - oldLeft
        val oldHeight = oldBottom - oldTop
        if (width > 0 && height > 0 && (abs(width - oldWidth) > 2 || abs(height - oldHeight) > 2)) {
            renderCurrent(force = true)
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
    }

    fun setPlotActive(active: Boolean) {
        plotActive = active
        if (active && isAttachedToWindow) attachRuntime() else if (!active) releaseRuntime()
    }

    fun bind(runtime: PlotWebRuntime, payload: JSONObject) {
        this.runtime = runtime
        this.payload = payload
        if (isAttachedToWindow) {
            if (slot == null) attachRuntime() else renderCurrent(force = true)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachRuntime()
    }

    override fun onDetachedFromWindow() {
        releaseRuntime()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0 && (abs(width - oldWidth) > 2 || abs(height - oldHeight) > 2)) {
            post { renderCurrent(force = true) }
        }
    }

    private fun attachRuntime() {
        if (!plotActive || slot != null) return
        val rt = runtime ?: return
        val acquired = rt.acquireChart()
        slot = acquired
        acquired.webView.addOnLayoutChangeListener(webLayoutListener)
        addView(acquired.webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Rendering before Android has measured the WebView caused 360px-square plots in landscape.
        acquired.webView.post { renderCurrent(force = true) }
    }

    private fun renderCurrent(force: Boolean = false) {
        val rt = runtime ?: return
        val acquired = slot ?: return
        val currentPayload = payload ?: return
        val width = acquired.webView.width
        val height = acquired.webView.height
        if (width <= 0 || height <= 0) {
            acquired.webView.postDelayed({ renderCurrent(force = true) }, 24L)
            return
        }
        if (!force && width == lastRenderedWidth && height == lastRenderedHeight) return
        lastRenderedWidth = width
        lastRenderedHeight = height
        rt.render(acquired, currentPayload)
    }

    private fun releaseRuntime() {
        val rt = runtime ?: return
        val acquired = slot ?: return
        slot = null
        acquired.webView.removeOnLayoutChangeListener(webLayoutListener)
        lastRenderedWidth = 0
        lastRenderedHeight = 0
        rt.releaseChart(acquired)
    }
}
