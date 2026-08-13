package xyz.x3ofiz4.exvia.presentation.plot
import xyz.x3ofiz4.exvia.core.json.toJson
import xyz.x3ofiz4.exvia.domain.model.theme.PlotTheme
import xyz.x3ofiz4.exvia.domain.model.custom.CustomMetricDefinition


import org.json.JSONObject

/** Runs custom metrics through the same pre-warmed D3/Plot/Arquero runtime as plots. */
class CustomMetricEngine(
    private val runtime: PlotWebRuntime,
    private val themeProvider: () -> PlotTheme,
) {
    fun evaluateObject(
        definition: CustomMetricDefinition,
        jsonFile: JSONObject,
        inputs: Map<String, String> = emptyMap(),
        callback: (Result<JSONObject>) -> Unit,
    ) {
        val payload = JSONObject().apply {
            put("script", definition.script)
            put("jsonFile", jsonFile)
            put("inputs", JSONObject(inputs))
            put("theme", themeProvider().toJson())
        }
        runtime.evaluateMetricObject(payload, callback)
    }

    fun evaluate(
        definition: CustomMetricDefinition,
        jsonFile: JSONObject,
        inputs: Map<String, String> = emptyMap(),
        callback: (Result<String>) -> Unit,
    ) {
        val payload = JSONObject().apply {
            put("script", definition.script)
            put("jsonFile", jsonFile)
            put("inputs", JSONObject(inputs))
            put("theme", themeProvider().toJson())
        }
        runtime.evaluateMetric(payload, callback)
    }
}
