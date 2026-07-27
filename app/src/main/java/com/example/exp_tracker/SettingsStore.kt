package com.example.exp_tracker

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): RepoSettings {
        val preset = ThemePreset.fromId(prefs.getString(KEY_THEME_PRESET, ThemePreset.DEFAULT.id))
        val defaults = ThemePalette.preset(preset)
        val palette = ThemePalette(
            primary = prefs.getString(KEY_PRIMARY, defaults.primary) ?: defaults.primary,
            secondary = prefs.getString(KEY_SECONDARY, defaults.secondary) ?: defaults.secondary,
            tertiary = prefs.getString(KEY_TERTIARY, defaults.tertiary) ?: defaults.tertiary,
            quaternary = prefs.getString(KEY_QUATERNARY, defaults.quaternary) ?: defaults.quaternary,
            quinary = prefs.getString(KEY_QUINARY, defaults.quinary) ?: defaults.quinary,
            senary = prefs.getString(KEY_SENARY, defaults.senary) ?: defaults.senary,
        )
        return RepoSettings(
            owner = prefs.getString(KEY_OWNER, RepoConfig.OWNER) ?: RepoConfig.OWNER,
            repo = prefs.getString(KEY_REPO, RepoConfig.REPO) ?: RepoConfig.REPO,
            branch = prefs.getString(KEY_BRANCH, RepoConfig.BRANCH) ?: RepoConfig.BRANCH,
            folder = prefs.getString(KEY_FOLDER, RepoConfig.EXPENSE_FOLDER) ?: RepoConfig.EXPENSE_FOLDER,
            defaultJson = prefs.getString(KEY_DEFAULT_JSON, RepoConfig.DEFAULT_JSON) ?: RepoConfig.DEFAULT_JSON,
            arrayKey = prefs.getString(KEY_ARRAY_KEY, RepoConfig.ARRAY_KEY) ?: RepoConfig.ARRAY_KEY,
            dateKeyOverride = prefs.getString(KEY_DATE_KEY, "") ?: "",
            moneyKeyOverride = prefs.getString(KEY_MONEY_KEY, "") ?: "",
            tickerKeyOverride = prefs.getString(KEY_TICKER_KEY, "") ?: "",
            tagsKeyOverride = prefs.getString(KEY_TAGS_KEY, "") ?: "",
            tickerColors = parseTickerColors(
                prefs.getString(KEY_TICKER_COLORS, null) ?: colorsToText(RepoConfig.TICKER_COLORS),
            ),
            themePreset = preset,
            palette = palette,
        )
    }

    fun save(settings: RepoSettings) {
        prefs.edit()
            .putString(KEY_OWNER, settings.owner.trim())
            .putString(KEY_REPO, settings.repo.trim())
            .putString(KEY_BRANCH, settings.branch.trim().ifBlank { "main" })
            .putString(KEY_FOLDER, settings.folder.trim().trim('/'))
            .putString(KEY_DEFAULT_JSON, settings.defaultJson.trim())
            .putString(KEY_ARRAY_KEY, settings.arrayKey.trim())
            .putString(KEY_DATE_KEY, settings.dateKeyOverride.trim())
            .putString(KEY_MONEY_KEY, settings.moneyKeyOverride.trim())
            .putString(KEY_TICKER_KEY, settings.tickerKeyOverride.trim())
            .putString(KEY_TAGS_KEY, settings.tagsKeyOverride.trim())
            .putString(KEY_TICKER_COLORS, colorsToText(settings.tickerColors))
            .putString(KEY_THEME_PRESET, settings.themePreset.id)
            .putString(KEY_PRIMARY, settings.palette.primary)
            .putString(KEY_SECONDARY, settings.palette.secondary)
            .putString(KEY_TERTIARY, settings.palette.tertiary)
            .putString(KEY_QUATERNARY, settings.palette.quaternary)
            .putString(KEY_QUINARY, settings.palette.quinary)
            .putString(KEY_SENARY, settings.palette.senary)
            .apply()
    }

    companion object {
        private const val PREFS = "exp_tracker_settings"
        private const val KEY_OWNER = "owner"
        private const val KEY_REPO = "repo"
        private const val KEY_BRANCH = "branch"
        private const val KEY_FOLDER = "folder"
        private const val KEY_DEFAULT_JSON = "default_json"
        private const val KEY_ARRAY_KEY = "array_key"
        private const val KEY_DATE_KEY = "date_key"
        private const val KEY_MONEY_KEY = "money_key"
        private const val KEY_TICKER_KEY = "ticker_key"
        private const val KEY_TAGS_KEY = "tags_key"
        private const val KEY_TICKER_COLORS = "ticker_colors"
        private const val KEY_THEME_PRESET = "theme_preset"
        private const val KEY_PRIMARY = "theme_primary"
        private const val KEY_SECONDARY = "theme_secondary"
        private const val KEY_TERTIARY = "theme_tertiary"
        private const val KEY_QUATERNARY = "theme_quaternary"
        private const val KEY_QUINARY = "theme_quinary"
        private const val KEY_SENARY = "theme_senary"

        fun parseTickerColors(text: String): Map<String, String> {
            val result = linkedMapOf<String, String>()
            text.lineSequence().forEach { line ->
                val clean = line.trim()
                if (clean.isBlank() || clean.startsWith("# ")) return@forEach
                val parts = clean.split('=', limit = 2)
                if (parts.size != 2) return@forEach
                val key = parts[0].trim()
                val color = parts[1].trim()
                if (key.isNotBlank() && ThemePalette.isValidHex(color)) result[key] = color
            }
            return result
        }

        fun colorsToText(colors: Map<String, String>): String = colors.entries
            .joinToString("\n") { "${it.key}=${it.value}" }
    }
}
