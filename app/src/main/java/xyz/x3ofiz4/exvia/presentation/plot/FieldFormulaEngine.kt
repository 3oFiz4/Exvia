package xyz.x3ofiz4.exvia.presentation.plot

import org.json.JSONArray
import org.json.JSONObject
import xyz.x3ofiz4.exvia.domain.model.custom.ImaginaryFieldDefinition
import xyz.x3ofiz4.exvia.domain.model.table.DynamicRow
import xyz.x3ofiz4.exvia.domain.model.table.TableData
import xyz.x3ofiz4.exvia.domain.model.theme.PlotTheme
import xyz.x3ofiz4.exvia.domain.service.FormulaSupport

/**
 * Evaluates Excel-like field formulas. JavaScript is executed by the already
 * pre-warmed plot WebView; SQLite-style scalar formulas reuse the Kotlin filter
 * evaluator and therefore never require a second database/runtime.
 */
class FieldFormulaEngine(
    private val runtime: PlotWebRuntime,
    private val themeProvider: () -> PlotTheme,
) {
    fun resolveInputValues(
        inputValues: LinkedHashMap<String, String>,
        existingRow: DynamicRow?,
        table: TableData,
        fileName: String,
        callback: (Result<LinkedHashMap<String, String>>) -> Unit,
    ) {
        val rowValues = linkedMapOf<String, String>().apply {
            existingRow?.values?.let(::putAll)
            putAll(inputValues)
        }
        val contextRow = DynamicRow(LinkedHashMap(rowValues), existingRow?.originalIndex ?: table.rows.size, existingRow?.originalJson ?: "{}")
        val resolved = LinkedHashMap(inputValues)
        val tasks = JSONArray()

        inputValues.forEach { (field, raw) ->
            val parsed = FormulaSupport.parse(raw)
            when (parsed.kind) {
                FormulaSupport.Kind.VALUE -> Unit
                FormulaSupport.Kind.SQLITE -> try {
                    resolved[field] = FormulaSupport.evaluateSqlScalar(parsed.body, contextRow, table)
                    rowValues[field] = resolved[field].orEmpty()
                } catch (error: Exception) {
                    callback(Result.failure(IllegalArgumentException("$field: ${error.message}")))
                    return
                }
                FormulaSupport.Kind.JAVASCRIPT -> tasks.put(JSONObject().apply {
                    put("id", field)
                    put("field", field)
                    put("index", contextRow.originalIndex)
                    put("row", stringMapJson(rowValues))
                    put("body", FormulaSupport.jsBody(parsed.body))
                })
            }
        }

        if (tasks.length() == 0) {
            callback(Result.success(resolved))
            return
        }
        val payload = formulaPayload(table, fileName, tasks)
        runtime.evaluateFormulas(payload) { result ->
            result.fold(
                onSuccess = { json ->
                    try {
                        val array = json.optJSONArray("results") ?: JSONArray()
                        for (i in 0 until array.length()) {
                            val item = array.optJSONObject(i) ?: continue
                            val field = item.optString("id")
                            if (!item.optBoolean("ok")) {
                                throw IllegalArgumentException("$field: ${item.optString("error", "Formula failed")}")
                            }
                            resolved[field] = displayResult(item.opt("value"))
                        }
                        callback(Result.success(resolved))
                    } catch (error: Exception) {
                        callback(Result.failure(error))
                    }
                },
                onFailure = { callback(Result.failure(it)) },
            )
        }
    }

    fun evaluateImaginaryFields(
        definitions: List<ImaginaryFieldDefinition>,
        table: TableData,
        fileName: String,
        callback: (Result<Map<String, Map<Int, String>>>) -> Unit,
    ) {
        val enabled = definitions.filter { it.enabled && it.name.isNotBlank() }
        if (enabled.isEmpty() || table.rows.isEmpty()) {
            callback(Result.success(emptyMap()))
            return
        }
        val values = linkedMapOf<String, MutableMap<Int, String>>()
        val tasks = JSONArray()

        enabled.forEach { definition ->
            val perField = linkedMapOf<Int, String>()
            values[definition.name] = perField
            table.rows.forEach { row ->
                definition.manualValues["$fileName#${row.originalIndex}"]?.takeIf { it.isNotBlank() }?.let {
                    perField[row.originalIndex] = it
                }
            }
            if (definition.expression.isBlank()) return@forEach
            val parsed = FormulaSupport.parse(definition.expression)
            table.rows.forEach { row ->
                // A manual value is an explicit per-row override and wins over the formula.
                if (perField.containsKey(row.originalIndex)) return@forEach
                when (parsed.kind) {
                    FormulaSupport.Kind.VALUE -> {
                        if (parsed.body.isNotBlank()) perField[row.originalIndex] = parsed.body
                    }
                    FormulaSupport.Kind.SQLITE -> try {
                        val value = FormulaSupport.evaluateSqlScalar(parsed.body, row, table)
                        if (value.isNotBlank()) perField[row.originalIndex] = value
                    } catch (error: Exception) {
                        callback(Result.failure(IllegalArgumentException("${definition.name}: ${error.message}")))
                        return
                    }
                    FormulaSupport.Kind.JAVASCRIPT -> tasks.put(JSONObject().apply {
                        put("id", "${definition.id}:${row.originalIndex}")
                        put("field", definition.name)
                        put("index", row.originalIndex)
                        put("row", stringMapJson(row.values))
                        put("body", FormulaSupport.jsBody(parsed.body))
                    })
                }
            }
        }

        if (tasks.length() == 0) {
            callback(Result.success(values.mapValues { it.value.toMap() }))
            return
        }
        runtime.evaluateFormulas(formulaPayload(table, fileName, tasks)) { result ->
            result.fold(
                onSuccess = { json ->
                    try {
                        val byId = enabled.associateBy { it.id }
                        val array = json.optJSONArray("results") ?: JSONArray()
                        for (i in 0 until array.length()) {
                            val item = array.optJSONObject(i) ?: continue
                            val idParts = item.optString("id").split(':', limit = 2)
                            val definition = byId[idParts.firstOrNull()] ?: continue
                            val rowIndex = idParts.getOrNull(1)?.toIntOrNull() ?: continue
                            if (!item.optBoolean("ok")) {
                                throw IllegalArgumentException("${definition.name}: ${item.optString("error", "Formula failed")}")
                            }
                            val value = displayResult(item.opt("value"))
                            if (value.isNotBlank()) values.getOrPut(definition.name) { linkedMapOf() }[rowIndex] = value
                        }
                        callback(Result.success(values.mapValues { it.value.toMap() }))
                    } catch (error: Exception) {
                        callback(Result.failure(error))
                    }
                },
                onFailure = { callback(Result.failure(it)) },
            )
        }
    }

    private fun formulaPayload(table: TableData, fileName: String, tasks: JSONArray): JSONObject {
        val rows = JSONArray().apply { table.rows.forEach { put(stringMapJson(it.values)) } }
        return JSONObject().apply {
            put("rows", rows)
            put("tasks", tasks)
            put("theme", JSONObject().apply {
                val t = themeProvider()
                put("background", t.background); put("surface", t.surface); put("text", t.text); put("muted", t.muted)
                put("grid", t.grid); put("axis", t.axis); put("positive", t.positive); put("negative", t.negative)
                put("observation", t.observation); put("outlier", t.outlier); put("center", t.center); put("accent", t.accent)
                put("selection", t.selection); put("tooltipBackground", t.tooltipBackground); put("tooltipText", t.tooltipText); put("tooltipBorder", t.tooltipBorder)
            })
            put("jsonFile", JSONObject().apply {
                put("name", fileName)
                put("content", rows.toString())
            })
        }
    }

    private fun stringMapJson(values: Map<String, String>): JSONObject = JSONObject().apply {
        values.forEach { (key, value) -> put(key, value) }
    }

    private fun displayResult(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is JSONObject, is JSONArray -> value.toString()
        else -> value.toString()
    }
}
