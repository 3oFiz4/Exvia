package xyz.x3ofiz4.exvia.data.sql

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import xyz.x3ofiz4.exvia.domain.model.repository.RepoFile
import xyz.x3ofiz4.exvia.domain.model.table.TableData

/**
 * Executes user-authored SQLite SELECT scripts against an ephemeral in-memory
 * database. Every loaded JSON file is exposed through ALL_FILES with metadata
 * columns __file and __row. The database is destroyed after each execution.
 */
class FileSqlScriptEngine {
    data class Input(val file: RepoFile, val table: TableData)
    data class Result(val columns: List<String>, val rows: List<Map<String, String>>)

    fun execute(inputs: List<Input>, script: String): Result {
        require(script.isNotBlank()) { "SQLite script is empty." }
        val statements = splitStatements(script)
        require(statements.isNotEmpty()) { "SQLite script is empty." }
        val db = SQLiteDatabase.create(null)
        try {
            val unionKeys = inputs.flatMap { it.table.keys }.filterNot { it.equals("__file", true) || it.equals("__row", true) }.distinctBy { it.lowercase() }
            db.execSQL(buildString {
                append("CREATE TABLE ALL_FILES (\"__file\" TEXT, \"__row\" INTEGER")
                unionKeys.forEach { append(", ${quoteId(it)} TEXT") }
                append(")")
            })
            inputs.forEach { input ->
                input.table.rows.forEach { row ->
                    val values = ContentValues().apply {
                        put("__file", input.file.name)
                        put("__row", row.originalIndex)
                        unionKeys.forEach { key -> put(key, row.values[key].orEmpty()) }
                    }
                    db.insertOrThrow("ALL_FILES", null, values)
                }
            }

            if (script.lineSequence().any { it.trim().equals("-- @exact-schema", true) }) {
                val requested = Regex("(?is)__file\\s+IN\\s*\\(([^)]*)\\)").find(script)
                    ?.groupValues?.getOrNull(1)
                    ?.let { body -> Regex("['\"]([^'\"]+)['\"]").findAll(body).map { it.groupValues[1] }.toList() }
                    .orEmpty()
                require(requested.size >= 2) { "@exact-schema requires at least two __file names in an IN (...) clause." }
                val schemas = requested.map { name ->
                    inputs.firstOrNull { it.file.name.equals(name, true) }?.table?.keys?.map(String::lowercase)
                        ?: throw IllegalArgumentException("File '$name' is not loaded.")
                }
                require(schemas.drop(1).all { it == schemas.first() }) { "Selected files do not have exactly the same column schema." }
            }

            statements.dropLast(1).forEach { statement ->
                val normalized = statement.trim().uppercase()
                require(normalized.startsWith("CREATE TEMP VIEW") || normalized.startsWith("CREATE TEMPORARY VIEW") || normalized.startsWith("DROP VIEW")) {
                    "Only CREATE TEMP VIEW / DROP VIEW may precede the final SELECT."
                }
                db.execSQL(statement)
            }
            val query = statements.last().trim()
            require(query.startsWith("SELECT", true) || query.startsWith("WITH", true)) {
                "The final SQLite statement must be SELECT or WITH ... SELECT."
            }
            db.rawQuery(query, null).use { cursor -> return cursorResult(cursor) }
        } finally {
            db.close()
        }
    }

    private fun cursorResult(cursor: Cursor): Result {
        val columns = cursor.columnNames.filterNot { it.equals("__file", true) || it.equals("__row", true) }
        val rows = mutableListOf<Map<String, String>>()
        while (cursor.moveToNext()) {
            val row = linkedMapOf<String, String>()
            columns.forEach { key ->
                val index = cursor.getColumnIndex(key)
                row[key] = if (index < 0 || cursor.isNull(index)) "" else cursor.getString(index).orEmpty()
            }
            rows += row
        }
        return Result(columns, rows)
    }

    private fun quoteId(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun splitStatements(script: String): List<String> {
        val clean = script.lineSequence().filterNot { it.trimStart().startsWith("--") }.joinToString("\n")
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var single = false
        var double = false
        var i = 0
        while (i < clean.length) {
            val c = clean[i]
            if (c == '\'' && !double) {
                if (single && i + 1 < clean.length && clean[i + 1] == '\'') {
                    current.append("''"); i += 2; continue
                }
                single = !single
            } else if (c == '"' && !single) {
                double = !double
            }
            if (c == ';' && !single && !double) {
                current.toString().trim().takeIf(String::isNotBlank)?.let(result::add)
                current.clear()
            } else current.append(c)
            i++
        }
        current.toString().trim().takeIf(String::isNotBlank)?.let(result::add)
        return result
    }
}
