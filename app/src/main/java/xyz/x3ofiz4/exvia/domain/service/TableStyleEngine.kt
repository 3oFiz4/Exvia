package xyz.x3ofiz4.exvia.domain.service

import xyz.x3ofiz4.exvia.domain.model.custom.TableStyleRule
import xyz.x3ofiz4.exvia.domain.model.table.DynamicRow
import xyz.x3ofiz4.exvia.domain.model.table.TableData

data class ResolvedCellStyle(
    val foreground: String? = null,
    val background: String? = null,
    val content: String? = null,
)

data class ResolvedRowStyle(
    val foreground: String? = null,
    val background: String? = null,
    val content: String? = null,
    val cells: Map<String, ResolvedCellStyle> = emptyMap(),
)

data class TableStyleResult(
    val rows: Map<Int, ResolvedRowStyle> = emptyMap(),
    val errors: List<String> = emptyList(),
)

/**
 * Resolves automatic Color Mapping and optional Flagging rules without running
 * arbitrary JavaScript. The accepted assignment syntax intentionally mirrors
 * JavaScript property assignment while limiting mutations to table visuals.
 */
object TableStyleEngine {
    private data class Assignment(val column: String?, val property: String, val value: String)

    private val assignmentRegex = Regex(
        """table\s*\[\s*(['\"])MATCHING_ROW\1\s*]\s*(?:\[\s*(['\"])([^'\"]+)\2\s*])?\s*\.\s*(back|fore|content)\s*=\s*(['\"])(.*?)\5\s*;?""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /**
     * Compact alternative used by v1.13.6+: `table.PRICE.fore = "#fff"`.
     * `table.fore = ...` targets the matching row itself. Legacy MATCHING_ROW
     * syntax remains fully supported for existing synchronized rules.
     */
    private val compactAssignmentRegex = Regex(
        """table\s*(?:\.\s*([A-Za-z_][A-Za-z0-9_]*))?\s*\.\s*(back|fore|content)\s*=\s*(['\"])(.*?)\3\s*;?""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun resolve(data: TableData, rules: List<TableStyleRule>): TableStyleResult {
        if (data.rows.isEmpty() || rules.none { it.enabled }) return TableStyleResult()
        val result = linkedMapOf<Int, MutableRowStyle>()
        val errors = mutableListOf<String>()

        rules.filter { it.enabled && it.query.isNotBlank() }.forEach { rule ->
            val filtered = SqlLikeFilter.apply(data, rule.query)
            if (filtered.error != null) {
                errors += "${rule.name}: ${filtered.error}"
                return@forEach
            }
            val assignments = buildList {
                addAll(parseField(rule.foregroundScript, "fore"))
                addAll(parseField(rule.backgroundScript, "back"))
                addAll(parseField(rule.contentScript, "content"))
            }
            if (assignments.isEmpty()) return@forEach
            filtered.rows.forEach { row ->
                val mutable = result.getOrPut(row.originalIndex) { MutableRowStyle() }
                assignments.forEach { assignment -> mutable.apply(assignment, row) }
            }
        }

        return TableStyleResult(
            rows = result.mapValues { (_, value) -> value.freeze() },
            errors = errors,
        )
    }

    private fun parseField(script: String, defaultProperty: String): List<Assignment> {
        val text = script.trim()
        if (text.isBlank()) return emptyList()
        val matches = assignmentRegex.findAll(text).map { match ->
            Assignment(
                column = match.groupValues[3].trim().takeIf { it.isNotBlank() },
                property = match.groupValues[4].lowercase(),
                value = unescape(match.groupValues[6]),
            )
        }.toList()
        if (matches.isNotEmpty()) return matches

        val compact = compactAssignmentRegex.findAll(text).map { match ->
            val first = match.groupValues[1].trim()
            val property = match.groupValues[2].lowercase()
            // `table.fore` has no column. With the regex above it is captured as
            // first=fore only when no final property exists, so valid matches here
            // are either table.COLUMN.property or table.property.
            Assignment(
                column = first.takeIf { it.isNotBlank() && !it.equals(property, true) },
                property = property,
                value = unescape(match.groupValues[4]),
            )
        }.toList()
        if (compact.isNotEmpty()) return compact

        // A plain value is useful in the editor: a color applies to the row;
        // plain content applies to the full row as an inherited cell template.
        return listOf(Assignment(null, defaultProperty, stripQuotes(text.removeSuffix(";").trim())))
    }

    private fun stripQuotes(value: String): String {
        if (value.length >= 2 && ((value.first() == '"' && value.last() == '"') ||
                (value.first() == '\'' && value.last() == '\''))) {
            return value.substring(1, value.length - 1)
        }
        return value
    }

    private fun unescape(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\'", "'")
        .replace("\\\\", "\\")

    private fun interpolate(template: String, row: DynamicRow, currentValue: String = ""): String {
        var result = template.replace("${'$'}value", currentValue)
        Regex("""\$\{([^}]+)}""").findAll(result).toList().asReversed().forEach { match ->
            val key = match.groupValues[1]
            val value = row.values.entries.firstOrNull { it.key.equals(key, true) }?.value.orEmpty()
            result = result.replaceRange(match.range, value)
        }
        return result
    }

    private class MutableCellStyle {
        var foreground: String? = null
        var background: String? = null
        var content: String? = null
        fun freeze() = ResolvedCellStyle(foreground, background, content)
    }

    private class MutableRowStyle {
        var foreground: String? = null
        var background: String? = null
        var content: String? = null
        val cells = linkedMapOf<String, MutableCellStyle>()

        fun apply(assignment: Assignment, row: DynamicRow) {
            val requestedColumn = assignment.column
            if (requestedColumn == null) {
                when (assignment.property) {
                    "fore" -> foreground = assignment.value
                    "back" -> background = assignment.value
                    "content" -> content = interpolate(assignment.value, row)
                }
                return
            }
            val actualColumn = row.values.keys.firstOrNull { it.equals(requestedColumn, true) } ?: requestedColumn
            val cell = cells.getOrPut(actualColumn) { MutableCellStyle() }
            val original = row.values.entries.firstOrNull { it.key.equals(actualColumn, true) }?.value.orEmpty()
            when (assignment.property) {
                "fore" -> cell.foreground = assignment.value
                "back" -> cell.background = assignment.value
                "content" -> cell.content = interpolate(assignment.value, row, original)
            }
        }

        fun freeze() = ResolvedRowStyle(
            foreground = foreground,
            background = background,
            content = content,
            cells = cells.mapValues { it.value.freeze() },
        )
    }
}
