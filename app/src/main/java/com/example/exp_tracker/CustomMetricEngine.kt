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
 * v1.9.1 deliberately injects only one host value:
 *
 *   jsonFile.name     selected JSON filename
 *   jsonFile.content  current effective JSON row array as text
 *
 * "Effective" means the same filtered subset currently visible in Table/Stat.
 * Scripts parse the JSON themselves with JSON.parse(jsonFile.content).
 */
class CustomMetricEngine(private val context: Context) {
    @SuppressLint("SetJavaScriptEnabled")
    fun evaluate(
        definition: CustomMetricDefinition,
        data: TableData,
        fileName: String,
        callback: (Result<String>) -> Unit,
    ) {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.domStorageEnabled = false
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        try { webView.settings.blockNetworkLoads = true } catch (_: Exception) { }

        val effectiveRows = JSONArray().apply {
            data.rows.forEach { row ->
                val rawObject = try {
                    JSONObject(row.originalJson)
                } catch (_: Exception) {
                    JSONObject().apply { row.values.forEach { (key, value) -> put(key, value) } }
                }
                put(rawObject)
            }
        }
        val jsonFile = JSONObject().apply {
            put("name", fileName)
            put("content", effectiveRows.toString())
        }

        val script = """
            (() => {
              const jsonFile = Object.freeze($jsonFile);
              try {
                const value = (function(jsonFile) {
                  ${definition.script}
                })(jsonFile);
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
