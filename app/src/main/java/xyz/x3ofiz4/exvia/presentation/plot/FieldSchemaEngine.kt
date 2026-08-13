package xyz.x3ofiz4.exvia.presentation.plot

import org.json.JSONArray
import org.json.JSONObject
import xyz.x3ofiz4.exvia.domain.model.custom.SchemaRuleDefinition
import xyz.x3ofiz4.exvia.domain.model.table.TableData

/** Evaluates user-defined JavaScript schema policies through the shared WebView runtime. */
class FieldSchemaEngine(private val runtime: PlotWebRuntime) {
    data class FieldConfig(
        val defaultValue: String = "",
        val hidden: Boolean = false,
        val numberOnlyKeypad: Boolean = false,
        val placeholder: String = "",
        val autoCompletion: Boolean = true,
        val autoCompletionParsing: String = "",
        val boolean01: Boolean = false,
    )

    fun evaluate(rules: List<SchemaRuleDefinition>, data: TableData, callback: (Result<Map<String, FieldConfig>>) -> Unit) {
        val active = rules.filter { it.enabled && it.script.isNotBlank() }
        if (active.isEmpty() || data.keys.isEmpty()) { callback(Result.success(emptyMap())); return }
        val payload = JSONObject().apply {
            put("keys", JSONArray(data.keys))
            put("rows", JSONArray().apply { data.rows.forEach { row -> put(JSONObject(row.values as Map<*, *>)) } })
            put("rules", JSONArray().apply { active.forEach { rule -> put(JSONObject().apply {
                put("id", rule.id); put("name", rule.name); put("script", rule.script); put("enabled", rule.enabled)
            }) } })
        }
        runtime.evaluateSchema(payload) { result ->
            result.fold(onSuccess = { json ->
                try {
                    val out = linkedMapOf<String, FieldConfig>()
                    val array = json.optJSONArray("results") ?: JSONArray()
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val key = item.optString("key")
                        val config = item.optJSONObject("config") ?: JSONObject()
                        out[key] = FieldConfig(
                            defaultValue = config.opt("DEFAULT_VALUE")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty(),
                            hidden = config.optBoolean("HIDDEN", false),
                            numberOnlyKeypad = config.optBoolean("NUMBER_ONLY_KEYPAD", false),
                            placeholder = config.optString("PLACEHOLDER"),
                            autoCompletion = config.optBoolean("AUTO_COMPLETION", true),
                            autoCompletionParsing = config.optString("AUTO_COMPLETION_PARSING"),
                            boolean01 = config.optBoolean("BOOLEAN_01", false),
                        )
                    }
                    callback(Result.success(out))
                } catch (error: Exception) { callback(Result.failure(error)) }
            }, onFailure = { callback(Result.failure(it)) })
        }
    }
}
