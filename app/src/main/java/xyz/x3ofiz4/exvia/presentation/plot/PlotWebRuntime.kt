package xyz.x3ofiz4.exvia.presentation.plot
import xyz.x3ofiz4.exvia.domain.model.theme.PlotTheme


import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import org.json.JSONObject
import java.util.ArrayDeque

/**
 * Activity-scoped WebView runtime for every Exvia chart and JavaScript metric.
 *
 * A small pool is pre-warmed once. Chart WebViews are borrowed only while a
 * plot accordion is attached, then returned to the pool. Custom metrics share
 * one separate serial runtime so metric evaluation never creates a WebView.
 */
class PlotWebRuntime(private val activity: Activity) {
    internal class Slot(val webView: WebView) {
        var ready = false
        val pending = ArrayDeque<() -> Unit>()
        val readyCallbacks = ArrayDeque<() -> Unit>()
    }

    private val idleCharts = ArrayDeque<Slot>()
    private val allSlots = linkedSetOf<Slot>()
    private var metricSlot: Slot? = null
    private var destroyed = false

    fun prewarm() {
        activity.runOnUiThread {
            if (destroyed) return@runOnUiThread
            if (metricSlot == null) {
                val first = createSlot()
                metricSlot = first
                whenReady(first) {
                    // Load chart runtimes only after the shared HTTP cache contains the modules.
                    while (!destroyed && idleCharts.size < PREWARMED_CHARTS) idleCharts.addLast(createSlot())
                }
            }
        }
    }

    internal fun acquireChart(): Slot {
        check(!destroyed) { "Plot runtime is destroyed" }
        return if (idleCharts.isNotEmpty()) idleCharts.removeFirst() else createSlot()
    }

    internal fun releaseChart(slot: Slot) {
        (slot.webView.parent as? ViewGroup)?.removeView(slot.webView)
        execute(slot, "window.ExviaRuntime && ExviaRuntime.clear();")
        if (destroyed || idleCharts.size >= MAX_IDLE_CHARTS) {
            destroySlot(slot)
        } else {
            idleCharts.addLast(slot)
        }
    }

    internal fun render(slot: Slot, payload: JSONObject, callback: ((String?) -> Unit)? = null) {
        execute(slot, "window.ExviaRuntime.render(${payload});", callback)
    }

    fun evaluateMetric(payload: JSONObject, callback: (Result<String>) -> Unit) {
        val slot = metricSlot ?: createSlot().also { metricSlot = it }
        execute(slot, "window.ExviaRuntime.evaluateMetric(${payload});") { encoded ->
            try {
                val decoded = decodeEvaluateResult(encoded)
                val result = JSONObject(decoded)
                if (!result.optBoolean("ok")) {
                    throw IllegalArgumentException(result.optString("error", "Custom metric failed"))
                }
                val value = result.opt("value")
                callback(Result.success(when (value) {
                    null, JSONObject.NULL -> "null"
                    else -> value.toString()
                }))
            } catch (error: Exception) {
                callback(Result.failure(error))
            }
        }
    }

    fun evaluateFormulas(payload: JSONObject, callback: (Result<JSONObject>) -> Unit) {
        val slot = metricSlot ?: createSlot().also { metricSlot = it }
        execute(slot, "window.ExviaRuntime.evaluateFormulaBatch(${payload});") { encoded ->
            try {
                val decoded = decodeEvaluateResult(encoded)
                val result = JSONObject(decoded)
                if (!result.optBoolean("ok")) {
                    throw IllegalArgumentException(result.optString("error", "Field formula evaluation failed"))
                }
                callback(Result.success(result))
            } catch (error: Exception) {
                callback(Result.failure(error))
            }
        }
    }

    fun destroy() {
        destroyed = true
        allSlots.toList().forEach(::destroySlot)
        allSlots.clear()
        idleCharts.clear()
        metricSlot = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun createSlot(): Slot {
        val webView = WebView(activity).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode = WebView.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            isLongClickable = false
            isHapticFeedbackEnabled = false
            setOnLongClickListener { true }
            setOnTouchListener { view, event ->
                // A gesture that starts on a plot belongs to the plot, including vertical panning.
                // The surrounding ScrollView can still be moved by starting the gesture outside it.
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_POINTER_UP -> view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            settings.apply {
                javaScriptEnabled = true
                allowFileAccess = true // required only for file:///android_asset/plot_runtime
                allowContentAccess = false
                domStorageEnabled = false
                databaseEnabled = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
                allowUniversalAccessFromFileURLs = false
                allowFileAccessFromFileURLs = false
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                textZoom = 100
                useWideViewPort = true
                loadWithOverviewMode = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) offscreenPreRaster = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
            }
        }
        val slot = Slot(webView)
        allSlots += slot
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // The runtime may load pinned script subresources, but custom code may not navigate the WebView.
                return request?.isForMainFrame == true && request.url.toString() != RUNTIME_URL
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return !url.isNullOrBlank() && url != RUNTIME_URL
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                slot.ready = true
                while (slot.pending.isNotEmpty()) slot.pending.removeFirst().invoke()
                while (slot.readyCallbacks.isNotEmpty()) slot.readyCallbacks.removeFirst().invoke()
            }
        }
        webView.loadUrl(RUNTIME_URL)
        return slot
    }

    private fun whenReady(slot: Slot, action: () -> Unit) {
        if (slot.ready) action() else slot.readyCallbacks.addLast(action)
    }

    private fun execute(slot: Slot, script: String, callback: ((String?) -> Unit)? = null) {
        val action = {
            if (!destroyed) slot.webView.evaluateJavascript(script) { callback?.invoke(it) }
        }
        if (slot.ready) action() else slot.pending.addLast(action)
    }

    private fun destroySlot(slot: Slot) {
        allSlots.remove(slot)
        idleCharts.remove(slot)
        (slot.webView.parent as? ViewGroup)?.removeView(slot.webView)
        slot.pending.clear()
        slot.readyCallbacks.clear()
        slot.webView.stopLoading()
        slot.webView.loadUrl("about:blank")
        slot.webView.removeAllViews()
        slot.webView.destroy()
    }

    private fun decodeEvaluateResult(encoded: String?): String {
        if (encoded.isNullOrBlank() || encoded == "null") return ""
        return JSONObject("{\"value\":$encoded}").optString("value")
    }

    companion object {
        private const val RUNTIME_URL = "file:///android_asset/plot_runtime/index.html"
        private const val PREWARMED_CHARTS = 2
        private const val MAX_IDLE_CHARTS = 3
    }
}
