package com.example.exp_tracker

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.FrameLayout
import org.json.JSONObject

/** A lightweight Android host that borrows one pre-warmed WebView on demand. */
class WebPlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private var runtime: PlotWebRuntime? = null
    private var slot: PlotWebRuntime.Slot? = null
    private var payload: JSONObject? = null
    private var plotActive = true

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
        if (isAttachedToWindow) attachRuntime()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachRuntime()
    }

    override fun onDetachedFromWindow() {
        releaseRuntime()
        super.onDetachedFromWindow()
    }

    private fun attachRuntime() {
        if (!plotActive || slot != null) return
        val rt = runtime ?: return
        val acquired = rt.acquireChart()
        slot = acquired
        addView(acquired.webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        payload?.let { rt.render(acquired, it) }
    }

    private fun releaseRuntime() {
        val rt = runtime ?: return
        val acquired = slot ?: return
        slot = null
        rt.releaseChart(acquired)
    }
}
