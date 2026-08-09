package xyz.x3ofiz4.exvia.domain.model.custom

import xyz.x3ofiz4.exvia.domain.model.theme.PlotTheme
import xyz.x3ofiz4.exvia.domain.model.theme.ThemePalette

data class CustomMetricDefinition(
    val id: String,
    val name: String,
    val script: String,
    val enabled: Boolean = true,
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
 *
 * The small assignment language is deliberately constrained: it may only
 * change .back, .fore, and .content on the matching row or one matching cell.
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
