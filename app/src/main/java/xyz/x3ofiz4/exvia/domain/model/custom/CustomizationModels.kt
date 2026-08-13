package xyz.x3ofiz4.exvia.domain.model.custom

import xyz.x3ofiz4.exvia.domain.model.theme.PlotTheme
import xyz.x3ofiz4.exvia.domain.model.theme.ThemePalette

data class ScriptGroupDefinition(
    val id: String,
    val name: String,
)

data class CustomMetricDefinition(
    val id: String,
    val name: String,
    val script: String,
    val enabled: Boolean = true,
    /** Shared Custom Accordion id. Older scripts migrate to `default`. */
    val groupId: String = DEFAULT_SCRIPT_GROUP_ID,
)

data class FilterSnippet(
    val id: String,
    val name: String,
    val query: String,
)

/**
 * A table styling rule. [query] selects rows through the same SQLite-like WHERE
 * evaluator used by Filtering. The three script fields accept either a plain
 * value or assignment expressions such as:
 *
 * table['MATCHING_ROW'].back = "#ff0000aa"
 * table['MATCHING_ROW']['PRICE'].fore = "#ffffff"
 * table['MATCHING_ROW']['DESCRIPTION'].content = "Over budget: ${'$'}{DESCRIPTION}"
 */
data class TableStyleRule(
    val id: String,
    val name: String,
    val query: String,
    val foregroundScript: String = "",
    val backgroundScript: String = "",
    val contentScript: String = "",
    val enabled: Boolean = true,
)

enum class TableQueryMode { FILTERING, FLAGGING }

data class CustomPlotDefinition(
    val id: String,
    val name: String,
    val script: String,
    val engine: String = "auto",
    val enabled: Boolean = true,
    /** Shared Custom Accordion id. Older scripts migrate to `default`. */
    val groupId: String = DEFAULT_SCRIPT_GROUP_ID,
)

data class FileScriptDefinition(
    val id: String,
    val name: String,
    val script: String,
    val enabled: Boolean = true,
)

data class ImaginaryFieldSnippet(
    val id: String,
    val name: String,
    val expression: String,
    val description: String = "",
)

/** A persistent Exvia environment variable. This is not an OS/.env variable. */
data class EnvironmentVariableDefinition(
    val id: String,
    val name: String,
    /** JavaScript initializer. `return ...` is accepted. */
    val initializerScript: String = "return null;",
    /** Last persistent runtime value encoded as JSON. */
    val valueJson: String = "null",
    val enabled: Boolean = true,
)

/** Android notification automation driven by one of Exvia's predefined events. */
data class NotificationRule(
    val id: String,
    val name: String,
    val eventName: String,
    val script: String,
    val enabled: Boolean = true,
)

/** JavaScript schema policy evaluated for each real or imaginary key. */
data class SchemaRuleDefinition(
    val id: String,
    val name: String,
    val script: String,
    val enabled: Boolean = true,
)

/** JavaScript override for the key/value color of a rendered statistic/finance/custom metric. */
data class MetricColorRule(
    val id: String,
    val name: String,
    val metricName: String,
    val script: String,
    val enabled: Boolean = true,
)

/**
 * A computed field that exists only in Exvia's effective table clone. It never
 * mutates the underlying expense JSON schema. Blank formula results are omitted
 * from the row, which keeps the overlay sparse.
 */
data class ImaginaryFieldDefinition(
    val id: String,
    val name: String,
    /** Plain value, =JavaScript, or ==SQLite scalar expression. Blank means manual-only. */
    val expression: String = "",
    /** Sparse per-file/per-row manual overrides. Keys are `<file>#<originalIndex>`. */
    val manualValues: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
)

data class NamedUiTheme(
    val id: String,
    val name: String,
    val palette: ThemePalette,
)

data class NamedPlotTheme(
    val id: String,
    val name: String,
    val theme: PlotTheme,
)

const val DEFAULT_SCRIPT_GROUP_ID = "default"
