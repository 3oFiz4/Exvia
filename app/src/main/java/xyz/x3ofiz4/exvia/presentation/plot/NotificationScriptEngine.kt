package xyz.x3ofiz4.exvia.presentation.plot

import org.json.JSONObject
import xyz.x3ofiz4.exvia.domain.model.custom.NotificationRule

class NotificationScriptEngine(private val runtime: PlotWebRuntime) {
    data class ResultValue(
        val notify: Boolean,
        val title: String,
        val body: String,
        val severity: String,
        val isToast: Boolean = false,
    )
    fun evaluate(rule: NotificationRule, event: JSONObject, metrics: Map<String, Any?>, jsonFile: JSONObject, callback: (Result<ResultValue>) -> Unit) {
        val payload = JSONObject().apply {
            put("script", rule.script); put("event", event); put("metrics", JSONObject(metrics)); put("jsonFile",jsonFile)
        }
        runtime.evaluateNotification(payload) { result ->
            result.fold(onSuccess = { json ->
                try {
                    val value=json.opt("value")
                    if(value==null || value==JSONObject.NULL || value==false){ callback(Result.success(ResultValue(false,"","","normal",false))); return@fold }
                    val obj = value as? JSONObject ?: JSONObject().apply { put("notify",true); put("body",value.toString()) }
                    val isToast = obj.optBoolean("IS_TOAST", obj.optBoolean("isToast", false))
                    val shouldNotify = if (obj.has("notify")) obj.optBoolean("notify", false) else !isToast
                    callback(Result.success(ResultValue(
                        notify = shouldNotify,
                        title = obj.optString("title", rule.name),
                        body = obj.optString("body"),
                        severity = obj.optString("severity", "normal"),
                        isToast = isToast,
                    )))
                } catch(error:Exception){ callback(Result.failure(error)) }
            }, onFailure = { callback(Result.failure(it)) })
        }
    }
}
