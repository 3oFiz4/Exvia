package xyz.x3ofiz4.exvia.presentation.common

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/** Small dependency-free Markdown renderer for Assistant messages. */
object MarkdownFormatter {
    private val BOLD_PATTERN = Regex("(\\*\\*|__)(.+?)\\1")
    private val ITALIC_STAR_PATTERN = Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)")
    private val ITALIC_UNDERSCORE_PATTERN = Regex("(?<!_)_([^_\\n]+)_(?!_)")
    private val CODE_PATTERN = Regex("`([^`\\n]+)`")
    private val BULLET_PATTERN = Regex("^(\\s*)[-+*]\\s+(.*)$")
    private val NUMBER_PATTERN = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val HEADING_PATTERN = Regex("^(#{1,6})\\s+(.*)$")

    fun render(markdown: String): CharSequence {
        val out = SpannableStringBuilder()
        var fenced = false
        markdown.replace("\r\n", "\n").lines().forEachIndexed { index, rawLine ->
            if (rawLine.trimStart().startsWith("```")) {
                fenced = !fenced
                return@forEachIndexed
            }
            val lineStart = out.length
            if (fenced) {
                out.append(rawLine)
                out.setSpan(TypefaceSpan("monospace"), lineStart, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                val heading = HEADING_PATTERN.matchEntire(rawLine)
                val bullet = BULLET_PATTERN.matchEntire(rawLine)
                val numbered = NUMBER_PATTERN.matchEntire(rawLine)
                when {
                    heading != null -> {
                        val level = heading.groupValues[1].length
                        appendInline(out, heading.groupValues[2])
                        out.setSpan(StyleSpan(Typeface.BOLD), lineStart, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        out.setSpan(RelativeSizeSpan((1.22f - level * 0.035f).coerceAtLeast(1f)), lineStart, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    bullet != null -> {
                        out.append(bullet.groupValues[1]).append("• ")
                        appendInline(out, bullet.groupValues[2])
                    }
                    numbered != null -> {
                        out.append(numbered.groupValues[1]).append(numbered.groupValues[2]).append(". ")
                        appendInline(out, numbered.groupValues[3])
                    }
                    else -> appendInline(out, rawLine)
                }
            }
            if (index != markdown.lines().lastIndex) out.append('\n')
        }
        return out
    }

    private fun appendInline(out: SpannableStringBuilder, source: String) {
        var cursor = 0
        val tokens = buildList {
            BOLD_PATTERN.findAll(source).forEach { add(Token(it.range, it.groupValues[2], Typeface.BOLD, false)) }
            ITALIC_STAR_PATTERN.findAll(source).forEach { add(Token(it.range, it.groupValues[1], Typeface.ITALIC, false)) }
            ITALIC_UNDERSCORE_PATTERN.findAll(source).forEach { add(Token(it.range, it.groupValues[1], Typeface.ITALIC, false)) }
            CODE_PATTERN.findAll(source).forEach { add(Token(it.range, it.groupValues[1], Typeface.NORMAL, true)) }
        }.sortedWith(compareBy<Token> { it.range.first }.thenByDescending { it.range.last })

        tokens.forEach { token ->
            if (token.range.first < cursor) return@forEach
            out.append(source.substring(cursor, token.range.first))
            val start = out.length
            out.append(token.text)
            val end = out.length
            if (token.code) out.setSpan(TypefaceSpan("monospace"), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            else out.setSpan(StyleSpan(token.style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            cursor = token.range.last + 1
        }
        if (cursor < source.length) out.append(source.substring(cursor))
    }

    private data class Token(
        val range: IntRange,
        val text: String,
        val style: Int,
        val code: Boolean,
    )
}
