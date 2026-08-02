package com.example.exp_tracker

import org.json.JSONObject

import android.graphics.Color

/** Theme used exclusively by WebView-based charts and script editors. */
data class PlotTheme(
    val background: String = "#000000",
    val surface: String = "#11151C",
    val text: String = "#EDEDED",
    val muted: String = "#7D7D7D",
    val grid: String = "#1F1F1F",
    val axis: String = "#7D7D7D",
    val positive: String = "#34C759",
    val negative: String = "#F72323",
    val observation: String = "#3D8BFF",
    val outlier: String = "#F72323",
    val center: String = "#FFFFFF",
    val accent: String = "#A970FF",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("background", background)
        put("surface", surface)
        put("text", text)
        put("muted", muted)
        put("grid", grid)
        put("axis", axis)
        put("positive", positive)
        put("negative", negative)
        put("observation", observation)
        put("outlier", outlier)
        put("center", center)
        put("accent", accent)
    }

    fun backgroundColor(): Int = safe(background, "#000000")

    companion object {
        fun default(): PlotTheme = PlotTheme()

        fun ayu(): PlotTheme = PlotTheme(
            background = "#0B0E14",
            surface = "#131721",
            text = "#BFBDB6",
            muted = "#6C7380",
            grid = "#1C2433",
            axis = "#6C7380",
            positive = "#AAD94C",
            negative = "#F07178",
            observation = "#59C2FF",
            outlier = "#F07178",
            center = "#FFFFFF",
            accent = "#D2A6FF",
        )

        fun isValid(value: String): Boolean = ThemePalette.isValidHex(value)

        private fun safe(value: String, fallback: String): Int = try {
            Color.parseColor(value)
        } catch (_: Exception) {
            Color.parseColor(fallback)
        }
    }
}
