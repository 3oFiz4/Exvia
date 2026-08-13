package xyz.x3ofiz4.exvia.presentation.plot

import org.json.JSONArray
import org.json.JSONObject
import xyz.x3ofiz4.exvia.domain.model.custom.MetricColorRule

class MetricColorEngine(private val runtime: PlotWebRuntime) {
    data class Colors(val key: String? = null, val value: String? = null)
    fun evaluate(rules: List<MetricColorRule>, metrics: List<Pair<String, Double?>>, callback: (Result<Map<String, Colors>>) -> Unit) {
        val active = rules.filter { it.enabled && it.script.isNotBlank() }
        if (active.isEmpty() || metrics.isEmpty()) { callback(Result.success(emptyMap())); return }
        val payload = JSONObject().apply {
            put("rules", JSONArray().apply { active.forEach { rule -> put(JSONObject().apply {
                put("id",rule.id); put("metricName",rule.metricName); put("script",rule.script); put("enabled",rule.enabled)
            }) } })
            put("metricItems", JSONArray().apply { metrics.forEach { (name, value) -> put(JSONObject().apply {
                put("name",name); if (value == null) put("value",JSONObject.NULL) else put("value",value)
            }) } })
        }
        runtime.evaluateMetricColors(payload) { result ->
            result.fold(onSuccess = { json ->
                val map = linkedMapOf<String, Colors>(); val arr=json.optJSONArray("results")?:JSONArray()
                for(i in 0 until arr.length()){ val item=arr.optJSONObject(i)?:continue; map[item.optString("name")]=Colors(item.optString("key").takeIf{it.isNotBlank()},item.optString("value").takeIf{it.isNotBlank()}) }
                callback(Result.success(map))
            }, onFailure = { callback(Result.failure(it)) })
        }
    }
}
