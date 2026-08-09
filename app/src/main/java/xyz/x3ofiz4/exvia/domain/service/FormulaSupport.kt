package xyz.x3ofiz4.exvia.domain.service

import xyz.x3ofiz4.exvia.domain.model.table.DynamicRow
import xyz.x3ofiz4.exvia.domain.model.table.TableData

/**
 * Shared parsing for Excel-like Exvia field formulas.
 *
 * JavaScript formulas start with = and are evaluated by the pre-warmed WebView
 * runtime. SQLite-style scalar formulas start with ==. SQL formulas reuse the
 * same WHERE matcher as Filtering and return one scalar column/literal.
 */
object FormulaSupport {
    const val JS_PREFIX = "="
    const val SQL_PREFIX = "=="

    enum class Kind { VALUE, JAVASCRIPT, SQLITE }

    data class Parsed(val kind: Kind, val body: String)

    fun parse(value: String): Parsed {
        val trimmed = value.trim()
        return when {
            // `==` must be checked before `=` because it shares the same first character.
            trimmed.startsWith(SQL_PREFIX) -> Parsed(Kind.SQLITE, trimmed.substring(SQL_PREFIX.length).trim())
            trimmed.startsWith(JS_PREFIX) -> Parsed(Kind.JAVASCRIPT, trimmed.substring(JS_PREFIX.length).trim())
            else -> Parsed(Kind.VALUE, value)
        }
    }

    fun jsBody(source: String): String {
        val body = source.trim()
        if (body.isBlank()) return "return '';"
        if (Regex("\\breturn\\b").containsMatchIn(body)) return body
        return "return ($body);"
    }

    /**
     * Supported scalar form:
     *   SELECT PRICE WHERE CATEGORY = 'FD'
     *   SELECT 'review' WHERE PRICE >= 50
     *   SELECT PRICE
     *
     * The WHERE clause supports every operator accepted by [SqlLikeFilter].
     */
    fun evaluateSqlScalar(expression: String, row: DynamicRow, table: TableData): String {
        val match = Regex("(?is)^\\s*SELECT\\s+(.+?)(?:\\s+WHERE\\s+(.+))?\\s*$").matchEntire(expression)
            ?: throw IllegalArgumentException("SQLite formula must look like SELECT <column|literal> [WHERE ...]")
        val selector = match.groupValues[1].trim()
        val where = match.groupValues.getOrNull(2)?.trim().orEmpty()
        if (where.isNotBlank()) {
            val one = table.copy(rows = listOf(row))
            val result = SqlLikeFilter.apply(one, "SELECT * WHERE $where")
            result.error?.let { throw IllegalArgumentException(it) }
            if (result.rows.isEmpty()) return ""
        }
        if (selector == "*") return "true"
        if ((selector.startsWith("'") && selector.endsWith("'")) ||
            (selector.startsWith("\"") && selector.endsWith("\""))) {
            return selector.substring(1, selector.length - 1)
        }
        selector.toBigDecimalOrNull()?.let { return selector }
        val key = table.keys.firstOrNull { it.equals(selector.trim('`', '[', ']'), ignoreCase = true) }
            ?: row.values.keys.firstOrNull { it.equals(selector.trim('`', '[', ']'), ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown column '$selector' in SQLite formula")
        return row.values[key].orEmpty()
    }
}
