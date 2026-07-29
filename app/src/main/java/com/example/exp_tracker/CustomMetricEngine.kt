package com.example.exp_tracker

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Executes user-defined custom metrics from Kotlin using Android's built-in
 * WebView JavaScript runtime. No JavaScript bridge, file/content access,
 * DOM storage, or network loading is enabled.
 *
 * A script is treated as a JavaScript function body. It receives:
 *   rows, keys, dateKey, moneyKey, tickerKey, tagsKey, num(value)
 * and should use `return` to produce a displayed result.
 */
class CustomMetricEngine(private val context: Context) {
    @SuppressLint("SetJavaScriptEnabled")
    fun evaluate(definition: CustomMetricDefinition, data: TableData, callback: (Result<String>) -> Unit) {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.domStorageEnabled = false
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        try { webView.settings.blockNetworkLoads = true } catch (_: Exception) { }

        val rows = JSONArray().apply {
            data.rows.forEach { row ->
                put(JSONObject().apply { row.values.forEach { (k, v) -> put(k, v) } })
            }
        }
        val keys = JSONArray(data.keys)
        val script = """
            (() => {
              const rows = $rows;
              const keys = $keys;
              const dateKey = ${JSONObject.quote(data.dateKey ?: "")};
              const moneyKey = ${JSONObject.quote(data.moneyKey ?: "")};
              const tickerKey = ${JSONObject.quote(data.tickerKey ?: "")};
              const tagsKey = ${JSONObject.quote(data.tagsKey ?: "")};
              const num = (value) => {
                if (value === null || value === undefined) return null;
                const n = Number(String(value).replace(/,/g, ''));
                return Number.isFinite(n) ? n : null;
              };
              try {
                const value = (function(rows, keys, dateKey, moneyKey, tickerKey, tagsKey, num) {
                  ${definition.script}
                })(rows, keys, dateKey, moneyKey, tickerKey, tagsKey, num);
                return JSON.stringify({ok:true, value:value});
              } catch (e) {
                return JSON.stringify({ok:false, error:String(e && e.message ? e.message : e)});
              }
            })()
        """.trimIndent()

        var evaluated = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (evaluated) return
                evaluated = true
                webView.evaluateJavascript(script) { encoded ->
                    try {
                        val outer = if (encoded == null || encoded == "null") "" else JSONObject("{\"v\":$encoded}").optString("v")
                        val result = JSONObject(outer)
                        if (!result.optBoolean("ok")) throw IllegalArgumentException(result.optString("error", "Custom metric failed"))
                        val value = result.opt("value")
                        val display = when (value) {
                            null, JSONObject.NULL -> "null"
                            is JSONObject, is JSONArray -> value.toString()
                            else -> value.toString()
                        }
                        callback(Result.success(display))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    } finally {
                        webView.destroy()
                    }
                }
            }
        }
        webView.loadDataWithBaseURL(null, "<html><body></body></html>", "text/html", "UTF-8", null)
    }
}
