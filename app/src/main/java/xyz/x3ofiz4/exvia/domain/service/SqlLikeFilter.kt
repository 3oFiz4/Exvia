package xyz.x3ofiz4.exvia.domain.service
import xyz.x3ofiz4.exvia.domain.model.table.DynamicRow
import xyz.x3ofiz4.exvia.domain.model.table.TableData


import java.util.Locale

/**
 * Small SQLite-style WHERE evaluator for JSON rows.
 *
 * Accepted examples:
 *   SELECT * WHERE price >= 10 AND ticker = 'FD'
 *   SELECT * WHERE description LIKE '%coffee%'
 *   SELECT * WHERE REGEX(description, '(?i)food|lunch')
 *   SELECT * WHERE tags REGEXP 'food|big'
 *
 * This intentionally evaluates only a WHERE expression; it does not mutate data and
 * does not execute arbitrary SQL. Column names are matched case-insensitively.
 */
object SqlLikeFilter {
    data class FilterResult(val rows: List<DynamicRow>, val error: String? = null)

    fun apply(data: TableData, query: String): FilterResult {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return FilterResult(data.rows)
        return try {
            val where = extractWhere(trimmed)
            val expr = Parser(tokenize(where)).parse()
            FilterResult(data.rows.filter { row -> expr.eval(row) })
        } catch (e: FilterException) {
            FilterResult(data.rows, e.message ?: "Invalid filter")
        } catch (e: Exception) {
            FilterResult(data.rows, e.message ?: "Invalid filter")
        }
    }

    private fun extractWhere(input: String): String {
        val normalized = input.trim().removeSuffix(";").trim()
        val match = Regex("(?is)^SELECT\\s+\\*\\s+(?:FROM\\s+[^\\s]+\\s+)?WHERE\\s+(.+)$").matchEntire(normalized)
        if (match != null) return match.groupValues[1].trim()
        if (normalized.startsWith("SELECT", ignoreCase = true)) {
            throw FilterException("Use SELECT * WHERE …")
        }
        return normalized
    }

    private enum class Type { IDENT, STRING, NUMBER, OP, LPAREN, RPAREN, COMMA, KEYWORD, END }
    private data class Token(val type: Type, val text: String)

    private fun tokenize(source: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> { out += Token(Type.LPAREN, "("); i++ }
                c == ')' -> { out += Token(Type.RPAREN, ")"); i++ }
                c == ',' -> { out += Token(Type.COMMA, ","); i++ }
                c == '\'' || c == '"' -> {
                    val quote = c
                    i++
                    val b = StringBuilder()
                    while (i < source.length) {
                        val ch = source[i++]
                        if (ch == quote) {
                            if (i < source.length && source[i] == quote) {
                                b.append(quote); i++
                            } else break
                        } else if (ch == '\\' && i < source.length) {
                            val next = source[i++]
                            if (next == quote || next == '\\') b.append(next) else { b.append('\\'); b.append(next) }
                        } else b.append(ch)
                    }
                    out += Token(Type.STRING, b.toString())
                }
                c == '`' || c == '[' -> {
                    val end = if (c == '`') '`' else ']'
                    i++
                    val start = i
                    while (i < source.length && source[i] != end) i++
                    if (i >= source.length) throw FilterException("Unclosed quoted column")
                    out += Token(Type.IDENT, source.substring(start, i))
                    i++
                }
                c in listOf('=', '!', '<', '>') -> {
                    val start = i++
                    if (i < source.length && source[i] == '=') i++
                    else if (source[start] == '<' && i < source.length && source[i] == '>') i++
                    out += Token(Type.OP, source.substring(start, i))
                }
                c.isDigit() || (c in listOf('+', '-') && i + 1 < source.length && (source[i + 1].isDigit() || source[i + 1] == '.')) || (c == '.' && i + 1 < source.length && source[i + 1].isDigit()) -> {
                    val start = i++
                    while (i < source.length && (source[i].isDigit() || source[i] == '.' || source[i] == 'e' || source[i] == 'E' || source[i] == '+' || source[i] == '-')) i++
                    out += Token(Type.NUMBER, source.substring(start, i))
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    val start = i++
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_' || source[i] == '.' || source[i] == '$')) i++
                    val word = source.substring(start, i)
                    val upper = word.uppercase(Locale.US)
                    val keywords = setOf("AND", "OR", "NOT", "LIKE", "REGEXP", "REGEX", "IS", "NULL", "IN", "TRUE", "FALSE")
                    out += Token(if (upper in keywords) Type.KEYWORD else Type.IDENT, word)
                }
                else -> throw FilterException("Unexpected character '$c'")
            }
        }
        out += Token(Type.END, "")
        return out
    }

    private sealed interface Expr { fun eval(row: DynamicRow): Boolean }
    private data class BoolExpr(val value: Boolean) : Expr { override fun eval(row: DynamicRow) = value }
    private data class AndExpr(val a: Expr, val b: Expr) : Expr { override fun eval(row: DynamicRow) = a.eval(row) && b.eval(row) }
    private data class OrExpr(val a: Expr, val b: Expr) : Expr { override fun eval(row: DynamicRow) = a.eval(row) || b.eval(row) }
    private data class NotExpr(val inner: Expr) : Expr { override fun eval(row: DynamicRow) = !inner.eval(row) }

    private sealed interface ValueExpr { fun value(row: DynamicRow): String? }
    private data class Column(val name: String) : ValueExpr {
        override fun value(row: DynamicRow): String? = row.values.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }
    private data class Literal(val text: String?) : ValueExpr { override fun value(row: DynamicRow) = text }

    private data class Comparison(val left: ValueExpr, val op: String, val right: ValueExpr) : Expr {
        override fun eval(row: DynamicRow): Boolean {
            val a = left.value(row)
            val b = right.value(row)
            if (a == null || b == null) return false
            val an = Statistics.parseNumber(a)
            val bn = Statistics.parseNumber(b)
            val cmp = if (an != null && bn != null) an.compareTo(bn) else a.compareTo(b, ignoreCase = true)
            return when (op) {
                "=" -> cmp == 0
                "!=", "<>" -> cmp != 0
                ">" -> cmp > 0
                ">=" -> cmp >= 0
                "<" -> cmp < 0
                "<=" -> cmp <= 0
                else -> false
            }
        }
    }

    private data class LikeExpr(val left: ValueExpr, val pattern: ValueExpr, val negated: Boolean) : Expr {
        override fun eval(row: DynamicRow): Boolean {
            val value = left.value(row) ?: return false.xor(negated)
            val p = pattern.value(row) ?: return false.xor(negated)
            val regex = buildString {
                append("(?is)^")
                p.forEach { ch ->
                    when (ch) {
                        '%' -> append(".*")
                        '_' -> append('.')
                        else -> append(Regex.escape(ch.toString()))
                    }
                }
                append('$')
            }
            val matched = Regex(regex).matches(value)
            return if (negated) !matched else matched
        }
    }

    private data class RegexExpr(val left: ValueExpr, val pattern: ValueExpr, val negated: Boolean = false) : Expr {
        override fun eval(row: DynamicRow): Boolean {
            val value = left.value(row) ?: return false.xor(negated)
            val p = pattern.value(row) ?: return false.xor(negated)
            val matched = try { Regex(p).containsMatchIn(value) } catch (_: Exception) { false }
            return if (negated) !matched else matched
        }
    }

    private data class IsNullExpr(val left: ValueExpr, val negated: Boolean) : Expr {
        override fun eval(row: DynamicRow): Boolean {
            val value = left.value(row)
            val isNull = value == null || value.isBlank()
            return if (negated) !isNull else isNull
        }
    }

    private data class InExpr(val left: ValueExpr, val values: List<ValueExpr>, val negated: Boolean) : Expr {
        override fun eval(row: DynamicRow): Boolean {
            val a = left.value(row) ?: return false.xor(negated)
            val matched = values.any { bExpr ->
                val b = bExpr.value(row) ?: return@any false
                val an = Statistics.parseNumber(a)
                val bn = Statistics.parseNumber(b)
                if (an != null && bn != null) an == bn else a.equals(b, ignoreCase = true)
            }
            return if (negated) !matched else matched
        }
    }

    private class Parser(private val tokens: List<Token>) {
        private var i = 0
        private fun peek() = tokens[i]
        private fun take() = tokens[i++]
        private fun keyword(word: String): Boolean = peek().type == Type.KEYWORD && peek().text.equals(word, true)
        private fun consumeKeyword(word: String): Boolean = if (keyword(word)) { i++; true } else false
        private fun expect(type: Type, label: String): Token {
            if (peek().type != type) throw FilterException("Expected $label near '${peek().text}'")
            return take()
        }

        fun parse(): Expr {
            if (peek().type == Type.END) return BoolExpr(true)
            val result = parseOr()
            if (peek().type != Type.END) throw FilterException("Unexpected token '${peek().text}'")
            return result
        }

        private fun parseOr(): Expr {
            var left = parseAnd()
            while (consumeKeyword("OR")) left = OrExpr(left, parseAnd())
            return left
        }

        private fun parseAnd(): Expr {
            var left = parseNot()
            while (consumeKeyword("AND")) left = AndExpr(left, parseNot())
            return left
        }

        private fun parseNot(): Expr = if (consumeKeyword("NOT")) NotExpr(parseNot()) else parsePrimary()

        private fun parsePrimary(): Expr {
            if (peek().type == Type.LPAREN) {
                take()
                val inner = parseOr()
                expect(Type.RPAREN, ")")
                return inner
            }
            if (keyword("REGEX") || keyword("REGEXP")) return parseRegexFunction()
            val left = parseValue()

            if (consumeKeyword("IS")) {
                val negated = consumeKeyword("NOT")
                if (!consumeKeyword("NULL")) throw FilterException("Expected NULL after IS")
                return IsNullExpr(left, negated)
            }

            var negated = false
            if (consumeKeyword("NOT")) negated = true
            if (consumeKeyword("LIKE")) return LikeExpr(left, parseValue(), negated)
            if (consumeKeyword("REGEXP") || consumeKeyword("REGEX")) return RegexExpr(left, parseValue(), negated)
            if (consumeKeyword("IN")) {
                expect(Type.LPAREN, "(")
                val list = mutableListOf<ValueExpr>()
                if (peek().type != Type.RPAREN) {
                    list += parseValue()
                    while (peek().type == Type.COMMA) { take(); list += parseValue() }
                }
                expect(Type.RPAREN, ")")
                return InExpr(left, list, negated)
            }
            if (negated) throw FilterException("NOT must precede LIKE, REGEX/REGEXP, IN, or an expression")

            val op = expect(Type.OP, "comparison operator").text
            if (op !in setOf("=", "!=", "<>", ">", ">=", "<", "<=")) throw FilterException("Unsupported operator '$op'")
            return Comparison(left, op, parseValue())
        }

        private fun parseRegexFunction(): Expr {
            take() // REGEX / REGEXP
            expect(Type.LPAREN, "(")
            val value = parseValue()
            expect(Type.COMMA, ",")
            val pattern = parseValue()
            expect(Type.RPAREN, ")")
            return RegexExpr(value, pattern)
        }

        private fun parseValue(): ValueExpr {
            val t = take()
            return when (t.type) {
                Type.IDENT -> Column(t.text)
                Type.STRING, Type.NUMBER -> Literal(t.text)
                Type.KEYWORD -> when {
                    t.text.equals("NULL", true) -> Literal(null)
                    t.text.equals("TRUE", true) -> Literal("1")
                    t.text.equals("FALSE", true) -> Literal("0")
                    else -> throw FilterException("Expected column or value near '${t.text}'")
                }
                else -> throw FilterException("Expected column or value near '${t.text}'")
            }
        }
    }

    private class FilterException(message: String) : Exception(message)
}
