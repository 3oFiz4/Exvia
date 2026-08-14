package xyz.x3ofiz4.exvia.presentation.main

import org.json.JSONArray
import org.json.JSONObject
import xyz.x3ofiz4.exvia.domain.model.custom.CustomMetricDefinition
import xyz.x3ofiz4.exvia.domain.model.custom.CustomPlotDefinition
import xyz.x3ofiz4.exvia.domain.model.custom.EnvironmentVariableDefinition
import xyz.x3ofiz4.exvia.domain.model.custom.FilterSnippet
import xyz.x3ofiz4.exvia.domain.model.custom.ImaginaryFieldSnippet
import xyz.x3ofiz4.exvia.domain.model.custom.MetricColorRule
import xyz.x3ofiz4.exvia.domain.model.custom.NotificationRule
import xyz.x3ofiz4.exvia.domain.model.custom.SchemaRuleDefinition
import xyz.x3ofiz4.exvia.domain.model.custom.TableStyleRule
import xyz.x3ofiz4.exvia.domain.service.BuiltinExamples

enum class AssistantCreationKind {
    FILTERING,
    FLAGGING,
    IMAGINARY_FIELD,
    REAL_FIELD,
    COLOR_MAPPING,
    METRIC_COLOR,
    SCHEMA_RULE,
    NOTIFICATION,
    ENVIRONMENT,
    PLOT,
    METRIC,
}

data class AssistantCreationSpec(
    val title: String,
    val available: String,
    val outputSchema: String,
    val examples: List<JSONObject>,
) {
    fun prompt(userRequest: String): String = buildString {
        append(available).append("\n\n")
        append("Output JSON schema:\n").append(outputSchema).append("\n\n")
        if (examples.isEmpty()) append("No built-in examples exist for this editor. Follow the runtime description and output schema.\n\n")
        examples.forEachIndexed { index, example ->
            append("Example #").append(index + 1).append(":\n")
            append(example.toString(2)).append("\n\n")
        }
        append("Your task is: ").append(userRequest.trim())
    }
}

object AssistantCreationPrompts {
    fun spec(kind: AssistantCreationKind, currentKeys: List<String> = emptyList()): AssistantCreationSpec = when (kind) {
        AssistantCreationKind.FILTERING -> AssistantCreationSpec(
            title = "Filtering snippet",
            available = "Available: Exvia's SQL-like SELECT * WHERE syntax, AND, OR, comparisons, IS NULL, and REGEXP(column, pattern). Return a reusable filter that hides non-matching rows.",
            outputSchema = "{\"name\":\"string\",\"query\":\"SELECT * WHERE ...\"}",
            examples = BuiltinExamples.filterSnippets.map(::filterJson),
        )
        AssistantCreationKind.FLAGGING -> AssistantCreationSpec(
            title = "Flagging method",
            available = "Available: Filtering query syntax plus table, MATCHING_ROW, .fore, .back, .content, theme colors, template values such as ${'$'}{COLUMN}, and ${'$'}value. A flag keeps rows visible and changes their presentation.",
            outputSchema = styleSchema,
            examples = BuiltinExamples.flaggingRules.map(::styleJson),
        )
        AssistantCreationKind.IMAGINARY_FIELD -> AssistantCreationSpec(
            title = "Imaginary field",
            available = "Available: blank/manual value, = JavaScript with row, table, jsonFile and ENV, or == SQLite scalar syntax. Imaginary fields never modify core JSON.",
            outputSchema = "{\"name\":\"UPPERCASE_FIELD_NAME\",\"expression\":\"= JavaScript | == SELECT ... | plain value | empty\"}",
            examples = BuiltinExamples.imaginaryFieldSnippets.map(::imaginaryJson),
        )
        AssistantCreationKind.REAL_FIELD -> AssistantCreationSpec(
            title = "Real field",
            available = "Available: create one real JSON schema key. Optional initial value supports plain text, = JavaScript, or == SQLite scalar syntax. Existing keys: ${JSONArray(currentKeys)}.",
            outputSchema = "{\"key\":\"string\",\"value\":\"optional plain value or formula\"}",
            examples = BuiltinExamples.imaginaryFieldSnippets.map(::imaginaryJson),
        )
        AssistantCreationKind.COLOR_MAPPING -> AssistantCreationSpec(
            title = "Color Mapping",
            available = "Available: Filtering query syntax plus table, MATCHING_ROW, .fore, .back, .content, theme colors, compact table.PRICE.fore syntax, and legacy table['MATCHING_ROW']['PRICE'].fore syntax. Mappings run automatically.",
            outputSchema = styleSchema,
            examples = (BuiltinExamples.defaultColorMappings + BuiltinExamples.colorMappingRules).map(::styleJson),
        )
        AssistantCreationKind.METRIC_COLOR -> AssistantCreationSpec(
            title = "Metric Color Mapping",
            available = "Available: metric.name, metric.value, metrics(name), ENV, and theme. Return an object with optional key and value hexadecimal colors.",
            outputSchema = "{\"name\":\"string\",\"metricName\":\"metric name or *\",\"script\":\"JavaScript returning {key,value}\"}",
            examples = BuiltinExamples.metricColorRules.map(::metricColorJson),
        )
        AssistantCreationKind.SCHEMA_RULE -> AssistantCreationSpec(
            title = "Key schema script",
            available = "Available: key, rows, ENV, and context. Per key, return any of DEFAULT_VALUE, HIDDEN, NUMBER_ONLY_KEYPAD, PLACEHOLDER, AUTO_COMPLETION, AUTO_COMPLETION_PARSING, and BOOLEAN_01.",
            outputSchema = "{\"name\":\"string\",\"script\":\"JavaScript returning a schema configuration object\"}",
            examples = BuiltinExamples.schemaRules.map(::schemaJson),
        )
        AssistantCreationKind.NOTIFICATION -> AssistantCreationSpec(
            title = "Notification event",
            available = "Available: event, metric(name), jsonFile, ENV, d3, Plot, aq, theme, and helpers. Events are event.amend, event.resync, or event.save. Return {notify,title,body,severity:'red|normal',IS_TOAST:boolean}.",
            outputSchema = "{\"name\":\"string\",\"eventName\":\"event.amend|event.resync|event.save\",\"script\":\"JavaScript\"}",
            examples = BuiltinExamples.notificationRules.map(::notificationJson),
        )
        AssistantCreationKind.ENVIRONMENT -> AssistantCreationSpec(
            title = "ENV variable",
            available = "Available: JavaScript initializer returning a JSON-compatible value. Other scripts access it as ENV.name and can call get, put, post, and delete on stored objects.",
            outputSchema = "{\"name\":\"valid JavaScript identifier\",\"initializerScript\":\"JavaScript ending in return ...;\"}",
            examples = BuiltinExamples.environmentVariables.map(::environmentJson),
        )
        AssistantCreationKind.PLOT -> AssistantCreationSpec(
            title = "Custom plot",
            available = "Available: d3, Plot, aq, jsonFile, context, theme, helpers, and ENV. Return an SVG/DOM plot. engine must be auto, observable, or d3.",
            outputSchema = "{\"name\":\"string\",\"engine\":\"auto|observable|d3\",\"script\":\"JavaScript\"}",
            examples = BuiltinExamples.customPlots.map(::plotJson),
        )
        AssistantCreationKind.METRIC -> AssistantCreationSpec(
            title = "Custom metric",
            available = "Available: jsonFile, d3, Plot, aq, theme, helpers, context.inputs, and ENV. Return a scalar/object, or {label,value,inputs:[{name,label,placeholder,default,env}]}.",
            outputSchema = "{\"name\":\"string\",\"script\":\"JavaScript returning the metric\"}",
            examples = (BuiltinExamples.customMetrics + BuiltinExamples.customMetricInputExamples).map(::metricJson),
        )
    }

    private const val styleSchema = "{\"name\":\"string\",\"query\":\"SELECT * WHERE ...\",\"foregroundScript\":\"string or empty\",\"backgroundScript\":\"string or empty\",\"contentScript\":\"string or empty\"}"

    private fun filterJson(item: FilterSnippet) = JSONObject().put("name", item.name).put("query", item.query)
    private fun styleJson(item: TableStyleRule) = JSONObject().put("name", item.name).put("query", item.query)
        .put("foregroundScript", item.foregroundScript).put("backgroundScript", item.backgroundScript).put("contentScript", item.contentScript)
    private fun imaginaryJson(item: ImaginaryFieldSnippet) = JSONObject().put("name", item.name).put("description", item.description).put("expression", item.expression)
    private fun metricJson(item: CustomMetricDefinition) = JSONObject().put("name", item.name).put("script", item.script)
    private fun plotJson(item: CustomPlotDefinition) = JSONObject().put("name", item.name).put("engine", item.engine).put("script", item.script)
    private fun environmentJson(item: EnvironmentVariableDefinition) = JSONObject().put("name", item.name).put("initializerScript", item.initializerScript).put("valueJson", item.valueJson)
    private fun notificationJson(item: NotificationRule) = JSONObject().put("name", item.name).put("eventName", item.eventName).put("script", item.script)
    private fun schemaJson(item: SchemaRuleDefinition) = JSONObject().put("name", item.name).put("script", item.script)
    private fun metricColorJson(item: MetricColorRule) = JSONObject().put("name", item.name).put("metricName", item.metricName).put("script", item.script)
}
