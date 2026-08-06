package xyz.x3ofiz4.exvia.domain.service

import xyz.x3ofiz4.exvia.domain.model.theme.ThemePalette

/** Pure parsing helpers for values entered in the Settings View. */
object SettingsTextParser {
    fun parseTickerColors(text: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@forEach
            val separator = when {
                '=' in trimmed -> '='
                ':' in trimmed -> ':'
                else -> return@forEach
            }
            val key = trimmed.substringBefore(separator).trim()
            val color = trimmed.substringAfter(separator).trim()
            if (key.isNotBlank() && ThemePalette.isValidHex(color)) result[key] = color
        }
        return result
    }

    fun parseColumnList(text: String): List<String> = text
        .split(',', '\n')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
}
