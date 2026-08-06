package xyz.x3ofiz4.exvia.presentation.common

import android.graphics.Color
import xyz.x3ofiz4.exvia.domain.model.theme.PlotTheme
import xyz.x3ofiz4.exvia.domain.model.theme.ThemePalette

fun ThemePalette.primaryColor(): Int = parseColorOr(primary, "#F72323")
fun ThemePalette.secondaryColor(): Int = parseColorOr(secondary, "#CC0000")
fun ThemePalette.tertiaryColor(): Int = parseColorOr(tertiary, "#000000")
fun ThemePalette.quaternaryColor(): Int = parseColorOr(quaternary, "#1F1F1F")
fun ThemePalette.quinaryColor(): Int = parseColorOr(quinary, "#7D7D7D")
fun ThemePalette.senaryColor(): Int = parseColorOr(senary, "#EDEDED")
fun PlotTheme.backgroundColor(): Int = parseColorOr(background, "#000000")

private fun parseColorOr(value: String, fallback: String): Int = try {
    Color.parseColor(value)
} catch (_: IllegalArgumentException) {
    Color.parseColor(fallback)
}
