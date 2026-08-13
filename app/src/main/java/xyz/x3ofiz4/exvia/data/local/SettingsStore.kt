package xyz.x3ofiz4.exvia.data.local
import xyz.x3ofiz4.exvia.core.json.toJson
import xyz.x3ofiz4.exvia.core.config.RepoConfig
import xyz.x3ofiz4.exvia.domain.model.custom.*
import xyz.x3ofiz4.exvia.domain.model.repository.*
import xyz.x3ofiz4.exvia.domain.model.settings.*
import xyz.x3ofiz4.exvia.domain.model.table.*
import xyz.x3ofiz4.exvia.domain.model.theme.*
import xyz.x3ofiz4.exvia.domain.service.BuiltinExamples


import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        // Preserve settings from exp_tracker when upgrading to the Exvia name.
        if (prefs.all.isEmpty()) {
            val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            if (legacy.all.isNotEmpty()) {
                val editor = prefs.edit()
                legacy.all.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        else -> Unit
                    }
                }
                editor.apply()
            }
        }
    }

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
        val customUiThemes = parseCustomUiThemes(prefs.getString(KEY_CUSTOM_UI_THEMES, "[]") ?: "[]")
        val customPlotThemes = parseCustomPlotThemes(prefs.getString(KEY_CUSTOM_PLOT_THEMES, "[]") ?: "[]")
        val plotDefaults = PlotTheme.default()
        val plotTheme = PlotTheme(
            background = prefs.getString(KEY_PLOT_BACKGROUND, plotDefaults.background) ?: plotDefaults.background,
            surface = prefs.getString(KEY_PLOT_SURFACE, plotDefaults.surface) ?: plotDefaults.surface,
            text = prefs.getString(KEY_PLOT_TEXT, plotDefaults.text) ?: plotDefaults.text,
            muted = prefs.getString(KEY_PLOT_MUTED, plotDefaults.muted) ?: plotDefaults.muted,
            grid = prefs.getString(KEY_PLOT_GRID, plotDefaults.grid) ?: plotDefaults.grid,
            axis = prefs.getString(KEY_PLOT_AXIS, plotDefaults.axis) ?: plotDefaults.axis,
            positive = prefs.getString(KEY_PLOT_POSITIVE, plotDefaults.positive) ?: plotDefaults.positive,
            negative = prefs.getString(KEY_PLOT_NEGATIVE, plotDefaults.negative) ?: plotDefaults.negative,
            observation = prefs.getString(KEY_PLOT_OBSERVATION, plotDefaults.observation) ?: plotDefaults.observation,
            outlier = prefs.getString(KEY_PLOT_OUTLIER, plotDefaults.outlier) ?: plotDefaults.outlier,
            center = prefs.getString(KEY_PLOT_CENTER, plotDefaults.center) ?: plotDefaults.center,
            accent = prefs.getString(KEY_PLOT_ACCENT, plotDefaults.accent) ?: plotDefaults.accent,
            selection = prefs.getString(KEY_PLOT_SELECTION, plotDefaults.selection) ?: plotDefaults.selection,
            tooltipBackground = prefs.getString(KEY_PLOT_TOOLTIP_BACKGROUND, plotDefaults.tooltipBackground) ?: plotDefaults.tooltipBackground,
            tooltipText = prefs.getString(KEY_PLOT_TOOLTIP_TEXT, plotDefaults.tooltipText) ?: plotDefaults.tooltipText,
            tooltipBorder = prefs.getString(KEY_PLOT_TOOLTIP_BORDER, plotDefaults.tooltipBorder) ?: plotDefaults.tooltipBorder,
        )
        val legacyTickerColors = parseTickerColors(
            prefs.getString(KEY_TICKER_COLORS, null) ?: colorsToText(RepoConfig.TICKER_COLORS),
        )
        val initialMappings = BuiltinExamples.defaultColorMappings + legacyTickerColors.flatMap { (value, color) ->
            listOf(
                TableStyleRule(
                    id = "legacy_ticker_${value.lowercase().replace(Regex("[^a-z0-9]+"), "_")}",
                    name = "TICKER · $value",
                    query = "SELECT * WHERE ticker = '${value.replace("'", "''")}'",
                    foregroundScript = "table['MATCHING_ROW']['TICKER'].fore = \"$color\"",
                ),
                TableStyleRule(
                    id = "legacy_category_${value.lowercase().replace(Regex("[^a-z0-9]+"), "_")}",
                    name = "CATEGORY · $value",
                    query = "SELECT * WHERE category = '${value.replace("'", "''")}'",
                    foregroundScript = "table['MATCHING_ROW']['CATEGORY'].fore = \"$color\"",
                ),
            )
        }
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
            tickerColors = legacyTickerColors,
            flaggingRules = parseTableStyleRules(
                prefs.getString(KEY_FLAGGING_RULES, "[]") ?: "[]",
            ),
            colorMappings = parseTableStyleRules(
                prefs.getString(KEY_COLOR_MAPPINGS, null)
                    ?: tableStyleRulesToJson(initialMappings.distinctBy { it.id }),
            ),
            plotColumns = parseColumnList(prefs.getString(KEY_PLOT_COLUMNS, "price") ?: "price"),
            financeColumns = parseColumnList(prefs.getString(KEY_FINANCE_COLUMNS, "price") ?: "price"),
            customMetrics = parseCustomMetrics(prefs.getString(KEY_CUSTOM_METRICS, "[]") ?: "[]"),
            customPlots = parseCustomPlots(prefs.getString(KEY_CUSTOM_PLOTS, "[]") ?: "[]"),
            scriptGroups = parseScriptGroups(prefs.getString(KEY_SCRIPT_GROUPS, "[]") ?: "[]").let { groups ->
                (listOf(ScriptGroupDefinition(DEFAULT_SCRIPT_GROUP_ID, "Default")) + groups.filterNot { it.id == DEFAULT_SCRIPT_GROUP_ID }).distinctBy { it.id }
            },
            environmentVariables = parseEnvironmentVariables(prefs.getString(KEY_ENVIRONMENT_VARIABLES, "[]") ?: "[]"),
            notificationRules = parseNotificationRules(prefs.getString(KEY_NOTIFICATION_RULES, "[]") ?: "[]"),
            schemaRules = parseSchemaRules(prefs.getString(KEY_SCHEMA_RULES, "[]") ?: "[]"),
            metricColorMappings = parseMetricColorRules(prefs.getString(KEY_METRIC_COLOR_MAPPINGS, "[]") ?: "[]"),
            customMetricInputs = parseStringMap(prefs.getString(KEY_CUSTOM_METRIC_INPUTS, "{}") ?: "{}"),
            fileScripts = parseFileScripts(prefs.getString(KEY_FILE_SCRIPTS, "[]") ?: "[]"),
            imaginaryFields = parseImaginaryFields(prefs.getString(KEY_IMAGINARY_FIELDS, "[]") ?: "[]"),
            uiScale = prefs.getString(KEY_UI_SCALE, "1.0")?.toDoubleOrNull()?.coerceIn(0.70, 1.60) ?: 1.0,
            textScale = prefs.getString(KEY_TEXT_SCALE, "1.0")?.toDoubleOrNull()?.coerceIn(0.70, 1.80) ?: 1.0,
            iconMode = UiIconMode.fromId(prefs.getString(KEY_ICON_MODE, UiIconMode.ICON_AND_TEXT.id)),
            rowsPerPage = prefs.getString(KEY_ROWS_PER_PAGE, "25")?.toIntOrNull()?.coerceIn(1, 500) ?: 25,
            undoHistoryLimit = prefs.getString(KEY_UNDO_HISTORY_LIMIT, "50")?.toIntOrNull()?.coerceIn(1, 50) ?: 50,
            automaticAmend = prefs.getBoolean(KEY_AUTOMATIC_AMEND, true),
            themePreset = preset,
            palette = palette,
            plotTheme = plotTheme,
            customUiThemes = customUiThemes,
            activeUiThemeId = prefs.getString(KEY_ACTIVE_UI_THEME, "builtin:${preset.id}") ?: "builtin:${preset.id}",
            customPlotThemes = customPlotThemes,
            activePlotThemeId = prefs.getString(KEY_ACTIVE_PLOT_THEME, "builtin:black") ?: "builtin:black",
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
            .putString(KEY_FLAGGING_RULES, tableStyleRulesToJson(settings.flaggingRules))
            .putString(KEY_COLOR_MAPPINGS, tableStyleRulesToJson(settings.colorMappings))
            .putString(KEY_PLOT_COLUMNS, settings.plotColumns.joinToString(", "))
            .putString(KEY_FINANCE_COLUMNS, settings.financeColumns.joinToString(", "))
            .putString(KEY_CUSTOM_METRICS, customMetricsToJson(settings.customMetrics))
            .putString(KEY_CUSTOM_PLOTS, customPlotsToJson(settings.customPlots))
            .putString(KEY_SCRIPT_GROUPS, scriptGroupsToJson(settings.scriptGroups.filterNot { it.id == DEFAULT_SCRIPT_GROUP_ID }))
            .putString(KEY_ENVIRONMENT_VARIABLES, environmentVariablesToJson(settings.environmentVariables))
            .putString(KEY_NOTIFICATION_RULES, notificationRulesToJson(settings.notificationRules))
            .putString(KEY_SCHEMA_RULES, schemaRulesToJson(settings.schemaRules))
            .putString(KEY_METRIC_COLOR_MAPPINGS, metricColorRulesToJson(settings.metricColorMappings))
            .putString(KEY_CUSTOM_METRIC_INPUTS, JSONObject(settings.customMetricInputs).toString())
            .putString(KEY_FILE_SCRIPTS, fileScriptsToJson(settings.fileScripts))
            .putString(KEY_IMAGINARY_FIELDS, imaginaryFieldsToJson(settings.imaginaryFields))
            .putString(KEY_UI_SCALE, settings.uiScale.toString())
            .putString(KEY_TEXT_SCALE, settings.textScale.toString())
            .putString(KEY_ICON_MODE, settings.iconMode.id)
            .putString(KEY_ROWS_PER_PAGE, settings.rowsPerPage.coerceIn(1, 500).toString())
            .putString(KEY_UNDO_HISTORY_LIMIT, settings.undoHistoryLimit.coerceIn(1, 50).toString())
            .putBoolean(KEY_AUTOMATIC_AMEND, settings.automaticAmend)
            .putString(KEY_THEME_PRESET, settings.themePreset.id)
            .putString(KEY_PRIMARY, settings.palette.primary)
            .putString(KEY_SECONDARY, settings.palette.secondary)
            .putString(KEY_TERTIARY, settings.palette.tertiary)
            .putString(KEY_QUATERNARY, settings.palette.quaternary)
            .putString(KEY_QUINARY, settings.palette.quinary)
            .putString(KEY_SENARY, settings.palette.senary)
            .putString(KEY_PLOT_BACKGROUND, settings.plotTheme.background)
            .putString(KEY_PLOT_SURFACE, settings.plotTheme.surface)
            .putString(KEY_PLOT_TEXT, settings.plotTheme.text)
            .putString(KEY_PLOT_MUTED, settings.plotTheme.muted)
            .putString(KEY_PLOT_GRID, settings.plotTheme.grid)
            .putString(KEY_PLOT_AXIS, settings.plotTheme.axis)
            .putString(KEY_PLOT_POSITIVE, settings.plotTheme.positive)
            .putString(KEY_PLOT_NEGATIVE, settings.plotTheme.negative)
            .putString(KEY_PLOT_OBSERVATION, settings.plotTheme.observation)
            .putString(KEY_PLOT_OUTLIER, settings.plotTheme.outlier)
            .putString(KEY_PLOT_CENTER, settings.plotTheme.center)
            .putString(KEY_PLOT_ACCENT, settings.plotTheme.accent)
            .putString(KEY_PLOT_SELECTION, settings.plotTheme.selection)
            .putString(KEY_PLOT_TOOLTIP_BACKGROUND, settings.plotTheme.tooltipBackground)
            .putString(KEY_PLOT_TOOLTIP_TEXT, settings.plotTheme.tooltipText)
            .putString(KEY_PLOT_TOOLTIP_BORDER, settings.plotTheme.tooltipBorder)
            .putString(KEY_CUSTOM_UI_THEMES, customUiThemesToJson(settings.customUiThemes))
            .putString(KEY_ACTIVE_UI_THEME, settings.activeUiThemeId)
            .putString(KEY_CUSTOM_PLOT_THEMES, customPlotThemesToJson(settings.customPlotThemes))
            .putString(KEY_ACTIVE_PLOT_THEME, settings.activePlotThemeId)
            .apply()
    }

    fun repoInitializationAsked(): Boolean = prefs.getBoolean(KEY_REPO_INIT_ASKED, false)
    fun setRepoInitializationAsked(value: Boolean) { prefs.edit().putBoolean(KEY_REPO_INIT_ASKED, value).apply() }

    // Developer mode is intentionally ON for a fresh install. Triple-tapping the Exvia title toggles it.
    fun developerModeEnabled(): Boolean = prefs.getBoolean(KEY_DEVELOPER_MODE, true)
    fun setDeveloperModeEnabled(value: Boolean) { prefs.edit().putBoolean(KEY_DEVELOPER_MODE, value).apply() }

    fun saveRowsPerPage(value: Int) {
        prefs.edit().putString(KEY_ROWS_PER_PAGE, value.coerceIn(1, 500).toString()).apply()
    }

    fun loadFilterSnippets(): List<FilterSnippet> = parseFilterSnippets(prefs.getString(KEY_FILTER_SNIPPETS, "[]") ?: "[]")
    fun saveFilterSnippets(items: List<FilterSnippet>) { prefs.edit().putString(KEY_FILTER_SNIPPETS, filterSnippetsToJson(items)).apply() }

    companion object {
        private const val PREFS = "exvia_settings"
        private const val LEGACY_PREFS = "exp_tracker_settings"
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
        private const val KEY_FLAGGING_RULES = "flagging_rules"
        private const val KEY_COLOR_MAPPINGS = "color_mappings"
        private const val KEY_PLOT_COLUMNS = "plot_columns"
        private const val KEY_FINANCE_COLUMNS = "finance_columns"
        private const val KEY_CUSTOM_METRICS = "custom_metrics"
        private const val KEY_CUSTOM_PLOTS = "custom_plots"
        private const val KEY_SCRIPT_GROUPS = "script_groups"
        private const val KEY_ENVIRONMENT_VARIABLES = "environment_variables"
        private const val KEY_NOTIFICATION_RULES = "notification_rules"
        private const val KEY_SCHEMA_RULES = "schema_rules"
        private const val KEY_METRIC_COLOR_MAPPINGS = "metric_color_mappings"
        private const val KEY_CUSTOM_METRIC_INPUTS = "custom_metric_inputs"
        private const val KEY_IMAGINARY_FIELDS = "imaginary_fields"
        private const val KEY_FILTER_SNIPPETS = "filter_snippets"
        private const val KEY_REPO_INIT_ASKED = "repo_initialization_asked"
        private const val KEY_DEVELOPER_MODE = "developer_mode"
        private const val KEY_UI_SCALE = "ui_scale"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_ICON_MODE = "icon_mode"
        private const val KEY_ROWS_PER_PAGE = "rows_per_page"
        private const val KEY_UNDO_HISTORY_LIMIT = "undo_history_limit"
        private const val KEY_AUTOMATIC_AMEND = "automatic_amend"
        private const val KEY_FILE_SCRIPTS = "file_scripts"
        private const val KEY_THEME_PRESET = "theme_preset"
        private const val KEY_PRIMARY = "theme_primary"
        private const val KEY_SECONDARY = "theme_secondary"
        private const val KEY_TERTIARY = "theme_tertiary"
        private const val KEY_QUATERNARY = "theme_quaternary"
        private const val KEY_QUINARY = "theme_quinary"
        private const val KEY_SENARY = "theme_senary"
        private const val KEY_PLOT_BACKGROUND = "plot_theme_background"
        private const val KEY_PLOT_SURFACE = "plot_theme_surface"
        private const val KEY_PLOT_TEXT = "plot_theme_text"
        private const val KEY_PLOT_MUTED = "plot_theme_muted"
        private const val KEY_PLOT_GRID = "plot_theme_grid"
        private const val KEY_PLOT_AXIS = "plot_theme_axis"
        private const val KEY_PLOT_POSITIVE = "plot_theme_positive"
        private const val KEY_PLOT_NEGATIVE = "plot_theme_negative"
        private const val KEY_PLOT_OBSERVATION = "plot_theme_observation"
        private const val KEY_PLOT_OUTLIER = "plot_theme_outlier"
        private const val KEY_PLOT_CENTER = "plot_theme_center"
        private const val KEY_PLOT_ACCENT = "plot_theme_accent"
        private const val KEY_PLOT_SELECTION = "plot_theme_selection"
        private const val KEY_PLOT_TOOLTIP_BACKGROUND = "plot_theme_tooltip_background"
        private const val KEY_PLOT_TOOLTIP_TEXT = "plot_theme_tooltip_text"
        private const val KEY_PLOT_TOOLTIP_BORDER = "plot_theme_tooltip_border"
        private const val KEY_CUSTOM_UI_THEMES = "custom_ui_themes"
        private const val KEY_ACTIVE_UI_THEME = "active_ui_theme"
        private const val KEY_CUSTOM_PLOT_THEMES = "custom_plot_themes"
        private const val KEY_ACTIVE_PLOT_THEME = "active_plot_theme"

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

        fun colorsToText(colors: Map<String, String>): String = colors.entries.joinToString("\n") { "${it.key}=${it.value}" }

        /** Serializes sync-safe application configuration. The GitHub PAT is intentionally excluded. */
        fun settingsToConfigJson(
            settings: RepoSettings,
            filterSnippets: List<FilterSnippet>,
            developerMode: Boolean,
        ): String = JSONObject().apply {
            put("format", "exvia-settings-v3")
            put("developerMode", developerMode)
            put("github", JSONObject().apply {
                put("owner", settings.owner)
                put("repo", settings.repo)
                put("branch", settings.branch)
                put("folder", settings.folder)
                put("defaultFile", settings.defaultJson)
                put("reportRepo", "Exvia")
            })
            put("schema", JSONObject().apply {
                put("arrayKey", settings.arrayKey)
                put("dateKey", settings.dateKeyOverride)
                put("moneyKey", settings.moneyKeyOverride)
                put("tickerKey", settings.tickerKeyOverride)
                put("tagsKey", settings.tagsKeyOverride)
            })
            put("display", JSONObject().apply {
                put("legacyTickerColors", JSONObject(settings.tickerColors))
                put("plotColumns", JSONArray(settings.plotColumns))
                put("financeColumns", JSONArray(settings.financeColumns))
                put("uiScale", settings.uiScale)
                put("textScale", settings.textScale)
                put("iconMode", settings.iconMode.id)
                put("rowsPerPage", settings.rowsPerPage)
                put("undoHistoryLimit", settings.undoHistoryLimit)
                put("automaticAmend", settings.automaticAmend)
            })
            put("theme", JSONObject().apply {
                put("preset", settings.themePreset.id)
                put("activeThemeId", settings.activeUiThemeId)
                put("primary", settings.palette.primary)
                put("secondary", settings.palette.secondary)
                put("tertiary", settings.palette.tertiary)
                put("quaternary", settings.palette.quaternary)
                put("quinary", settings.palette.quinary)
                put("senary", settings.palette.senary)
                put("customThemes", JSONArray(customUiThemesToJson(settings.customUiThemes)))
            })
            put("plotTheme", JSONObject().apply {
                put("activeThemeId", settings.activePlotThemeId)
                put("background", settings.plotTheme.background)
                put("surface", settings.plotTheme.surface)
                put("text", settings.plotTheme.text)
                put("muted", settings.plotTheme.muted)
                put("grid", settings.plotTheme.grid)
                put("axis", settings.plotTheme.axis)
                put("positive", settings.plotTheme.positive)
                put("negative", settings.plotTheme.negative)
                put("observation", settings.plotTheme.observation)
                put("outlier", settings.plotTheme.outlier)
                put("center", settings.plotTheme.center)
                put("accent", settings.plotTheme.accent)
                put("selection", settings.plotTheme.selection)
                put("tooltipBackground", settings.plotTheme.tooltipBackground)
                put("tooltipText", settings.plotTheme.tooltipText)
                put("tooltipBorder", settings.plotTheme.tooltipBorder)
                put("customThemes", JSONArray(customPlotThemesToJson(settings.customPlotThemes)))
            })
            put("resources", JSONObject().apply {
                put("filteringSnippets", ".exvia/filtering-snippets.json")
                put("flaggingSnippets", ".exvia/flagging-snippets.json")
                put("colorMappings", ".exvia/color-mappings.json")
                put("customMetrics", ".exvia/custom-metrics.json")
                put("customPlots", ".exvia/custom-plots.json")
                put("fileScripts", ".exvia/file-scripts.json")
                put("imaginaryFields", ".exvia/imaginary-fields.json")
                put("scriptGroups", ".exvia/script-groups.json")
                put("environmentVariables", ".exvia/environment-variables.json")
                put("notifications", ".exvia/notification-rules.json")
                put("schemaRules", ".exvia/schema-rules.json")
                put("metricColorMappings", ".exvia/metric-color-mappings.json")
                put("customMetricInputs", ".exvia/custom-metric-inputs.json")
            })
        }.toString(2) + "\n"

        fun filterSnippetsFileJson(items: List<FilterSnippet>): String = JSONObject().apply {
            put("format", "exvia-filtering-snippets-v1")
            put("items", JSONArray(filterSnippetsToJson(items)))
        }.toString(2) + "\n"

        fun flaggingRulesFileJson(items: List<TableStyleRule>): String = JSONObject().apply {
            put("format", "exvia-flagging-snippets-v1")
            put("items", JSONArray(tableStyleRulesToJson(items)))
        }.toString(2) + "\n"

        fun colorMappingsFileJson(items: List<TableStyleRule>): String = JSONObject().apply {
            put("format", "exvia-color-mappings-v1")
            put("items", JSONArray(tableStyleRulesToJson(items)))
        }.toString(2) + "\n"

        fun customMetricsFileJson(items: List<CustomMetricDefinition>): String = JSONObject().apply {
            put("format", "exvia-custom-metrics-v1")
            put("items", JSONArray(customMetricsToJson(items)))
        }.toString(2) + "\n"

        fun customPlotsFileJson(items: List<CustomPlotDefinition>): String = JSONObject().apply {
            put("format", "exvia-custom-plots-v1")
            put("items", JSONArray(customPlotsToJson(items)))
        }.toString(2) + "\n"

        fun fileScriptsFileJson(items: List<FileScriptDefinition>): String = JSONObject().apply {
            put("format", "exvia-file-scripts-v1")
            put("items", JSONArray(fileScriptsToJson(items)))
        }.toString(2) + "\n"

        fun imaginaryFieldsFileJson(items: List<ImaginaryFieldDefinition>): String = JSONObject().apply {
            put("format", "exvia-imaginary-fields-v1")
            put("items", JSONArray(imaginaryFieldsToJson(items)))
        }.toString(2) + "\n"

        fun tableRulesToJson(settings: RepoSettings): String = JSONObject().apply {
            put("format", "exvia-table-rules-v1")
            put("flaggingRules", JSONArray(tableStyleRulesToJson(settings.flaggingRules)))
            put("colorMappings", JSONArray(tableStyleRulesToJson(settings.colorMappings)))
        }.toString(2) + "\n"

        fun parseColumnList(text: String): List<String> = text.split(',', '\n')
            .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }

        private fun parseFileScripts(text: String): List<FileScriptDefinition> = try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                if (name.isBlank()) return@mapNotNull null
                FileScriptDefinition(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    script = obj.optString("script"),
                    enabled = obj.optBoolean("enabled", true),
                )
            }
        } catch (_: Exception) { emptyList() }

        private fun fileScriptsToJson(items: List<FileScriptDefinition>): String = JSONArray().apply {
            items.forEach { item -> put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("script", item.script)
                put("enabled", item.enabled)
            }) }
        }.toString()

        private fun parseImaginaryFields(text: String): List<ImaginaryFieldDefinition> = try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                if (name.isBlank()) return@mapNotNull null
                val manual = linkedMapOf<String, String>()
                obj.optJSONObject("manualValues")?.let { values ->
                    val iterator = values.keys()
                    while (iterator.hasNext()) {
                        val key = iterator.next()
                        manual[key] = values.optString(key)
                    }
                }
                ImaginaryFieldDefinition(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    expression = obj.optString("expression"),
                    manualValues = manual,
                    enabled = obj.optBoolean("enabled", true),
                )
            }
        } catch (_: Exception) { emptyList() }

        private fun imaginaryFieldsToJson(items: List<ImaginaryFieldDefinition>): String = JSONArray().apply {
            items.forEach { item -> put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("expression", item.expression)
                put("manualValues", JSONObject(item.manualValues))
                put("enabled", item.enabled)
            }) }
        }.toString()


        fun scriptGroupsFileJson(items: List<ScriptGroupDefinition>): String = JSONObject().apply {
            put("format", "exvia-script-groups-v1")
            put("items", JSONArray(scriptGroupsToJson(items.filterNot { it.id == DEFAULT_SCRIPT_GROUP_ID })))
        }.toString(2) + "\n"

        fun environmentVariablesFileJson(items: List<EnvironmentVariableDefinition>): String = JSONObject().apply {
            put("format", "exvia-environment-variables-v1")
            put("items", JSONArray(environmentVariablesToJson(items)))
        }.toString(2) + "\n"

        fun notificationRulesFileJson(items: List<NotificationRule>): String = JSONObject().apply {
            put("format", "exvia-notification-rules-v1")
            put("items", JSONArray(notificationRulesToJson(items)))
        }.toString(2) + "\n"

        fun schemaRulesFileJson(items: List<SchemaRuleDefinition>): String = JSONObject().apply {
            put("format", "exvia-schema-rules-v1")
            put("items", JSONArray(schemaRulesToJson(items)))
        }.toString(2) + "\n"

        fun metricColorMappingsFileJson(items: List<MetricColorRule>): String = JSONObject().apply {
            put("format", "exvia-metric-color-mappings-v1")
            put("items", JSONArray(metricColorRulesToJson(items)))
        }.toString(2) + "\n"

        fun customMetricInputsFileJson(items: Map<String, String>): String = JSONObject().apply {
            put("format", "exvia-custom-metric-inputs-v1")
            put("items", JSONObject(items))
        }.toString(2) + "\n"

        fun environmentPayload(items: List<EnvironmentVariableDefinition>): JSONObject = JSONObject().apply {
            items.filter { it.enabled && it.name.isNotBlank() }.forEach { item ->
                put(item.name, JSONObject().apply {
                    put("script", item.initializerScript)
                    put("valueJson", item.valueJson)
                })
            }
        }

        private fun parseScriptGroups(text: String): List<ScriptGroupDefinition> = try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                if (name.isBlank()) return@mapNotNull null
                ScriptGroupDefinition(obj.optString("id").ifBlank { UUID.randomUUID().toString() }, name)
            }
        } catch (_: Exception) { emptyList() }

        private fun scriptGroupsToJson(items: List<ScriptGroupDefinition>): String = JSONArray().apply {
            items.forEach { put(JSONObject().apply { put("id", it.id); put("name", it.name) }) }
        }.toString()

        private fun parseEnvironmentVariables(text: String): List<EnvironmentVariableDefinition> = try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                if (name.isBlank()) return@mapNotNull null
                EnvironmentVariableDefinition(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    initializerScript = obj.optString("initializerScript", "return null;"),
                    valueJson = obj.optString("valueJson", "null"),
                    enabled = obj.optBoolean("enabled", true),
                )
            }
        } catch (_: Exception) { emptyList() }

        private fun environmentVariablesToJson(items: List<EnvironmentVariableDefinition>): String = JSONArray().apply {
            items.forEach { item -> put(JSONObject().apply {
                put("id", item.id); put("name", item.name); put("initializerScript", item.initializerScript)
                put("valueJson", item.valueJson); put("enabled", item.enabled)
            }) }
        }.toString()

        private fun parseNotificationRules(text: String): List<NotificationRule> = try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim(); val event = obj.optString("eventName").trim(); val script = obj.optString("script")
                if (name.isBlank() || event.isBlank() || script.isBlank()) return@mapNotNull null
                NotificationRule(obj.optString("id").ifBlank { UUID.randomUUID().toString() }, name, event, script, obj.optBoolean("enabled", true))
            }
        } catch (_: Exception) { emptyList() }

        private fun notificationRulesToJson(items: List<NotificationRule>): String = JSONArray().apply {
            items.forEach { item -> put(JSONObject().apply { put("id",item.id); put("name",item.name); put("eventName",item.eventName); put("script",item.script); put("enabled",item.enabled) }) }
        }.toString()

        private fun parseSchemaRules(text: String): List<SchemaRuleDefinition> = try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val obj=arr.optJSONObject(i)?:return@mapNotNull null; val name=obj.optString("name").trim(); val script=obj.optString("script")
                if(name.isBlank()||script.isBlank()) return@mapNotNull null
                SchemaRuleDefinition(obj.optString("id").ifBlank { UUID.randomUUID().toString() },name,script,obj.optBoolean("enabled",true))
            }
        } catch (_: Exception) { emptyList() }

        private fun schemaRulesToJson(items: List<SchemaRuleDefinition>): String = JSONArray().apply {
            items.forEach { item -> put(JSONObject().apply { put("id",item.id); put("name",item.name); put("script",item.script); put("enabled",item.enabled) }) }
        }.toString()

        private fun parseMetricColorRules(text: String): List<MetricColorRule> = try {
            val arr=JSONArray(text)
            (0 until arr.length()).mapNotNull { i -> val obj=arr.optJSONObject(i)?:return@mapNotNull null; val name=obj.optString("name").trim(); val metric=obj.optString("metricName").trim(); val script=obj.optString("script"); if(name.isBlank()||metric.isBlank()||script.isBlank()) return@mapNotNull null; MetricColorRule(obj.optString("id").ifBlank { UUID.randomUUID().toString() },name,metric,script,obj.optBoolean("enabled",true)) }
        } catch (_: Exception) { emptyList() }

        private fun metricColorRulesToJson(items: List<MetricColorRule>): String = JSONArray().apply {
            items.forEach { item -> put(JSONObject().apply { put("id",item.id); put("name",item.name); put("metricName",item.metricName); put("script",item.script); put("enabled",item.enabled) }) }
        }.toString()

        private fun parseStringMap(text: String): Map<String,String> = try {
            val obj=JSONObject(text); val out=linkedMapOf<String,String>(); val keys=obj.keys(); while(keys.hasNext()){ val k=keys.next(); out[k]=obj.optString(k) }; out
        } catch (_: Exception) { emptyMap() }

        private fun parseCustomUiThemes(text: String): List<NamedUiTheme> = try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                val colors = obj.optJSONObject("palette") ?: return@mapNotNull null
                val palette = ThemePalette(
                    primary = colors.optString("primary", "#F72323"),
                    secondary = colors.optString("secondary", "#CC0000"),
                    tertiary = colors.optString("tertiary", "#000000"),
                    quaternary = colors.optString("quaternary", "#1F1F1F"),
                    quinary = colors.optString("quinary", "#7D7D7D"),
                    senary = colors.optString("senary", "#EDEDED"),
                )
                if (name.isBlank() || listOf(
                        palette.primary, palette.secondary, palette.tertiary,
                        palette.quaternary, palette.quinary, palette.senary,
                    ).any { !ThemePalette.isValidHex(it) }) return@mapNotNull null
                NamedUiTheme(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    palette = palette,
                )
            }
        } catch (_: Exception) { emptyList() }

        private fun customUiThemesToJson(items: List<NamedUiTheme>): String = JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("palette", JSONObject().apply {
                        put("primary", item.palette.primary)
                        put("secondary", item.palette.secondary)
                        put("tertiary", item.palette.tertiary)
                        put("quaternary", item.palette.quaternary)
                        put("quinary", item.palette.quinary)
                        put("senary", item.palette.senary)
                    })
                })
            }
        }.toString()

        private fun parseCustomPlotThemes(text: String): List<NamedPlotTheme> = try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                val colors = obj.optJSONObject("theme") ?: return@mapNotNull null
                val defaults = PlotTheme.default()
                val theme = PlotTheme(
                    background = colors.optString("background", defaults.background),
                    surface = colors.optString("surface", defaults.surface),
                    text = colors.optString("text", defaults.text),
                    muted = colors.optString("muted", defaults.muted),
                    grid = colors.optString("grid", defaults.grid),
                    axis = colors.optString("axis", defaults.axis),
                    positive = colors.optString("positive", defaults.positive),
                    negative = colors.optString("negative", defaults.negative),
                    observation = colors.optString("observation", defaults.observation),
                    outlier = colors.optString("outlier", defaults.outlier),
                    center = colors.optString("center", defaults.center),
                    accent = colors.optString("accent", defaults.accent),
                    selection = colors.optString("selection", defaults.selection),
                    tooltipBackground = colors.optString("tooltipBackground", defaults.tooltipBackground),
                    tooltipText = colors.optString("tooltipText", defaults.tooltipText),
                    tooltipBorder = colors.optString("tooltipBorder", defaults.tooltipBorder),
                )
                val values = listOf(
                    theme.background, theme.surface, theme.text, theme.muted, theme.grid, theme.axis,
                    theme.positive, theme.negative, theme.observation, theme.outlier, theme.center,
                    theme.accent, theme.selection, theme.tooltipBackground, theme.tooltipText,
                    theme.tooltipBorder,
                )
                if (name.isBlank() || values.any { !PlotTheme.isValid(it) }) return@mapNotNull null
                NamedPlotTheme(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    theme = theme,
                )
            }
        } catch (_: Exception) { emptyList() }

        private fun customPlotThemesToJson(items: List<NamedPlotTheme>): String = JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("theme", item.theme.toJson())
                })
            }
        }.toString()

        private fun parseCustomMetrics(text: String): List<CustomMetricDefinition> = try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                val script = obj.optString("script")
                if (name.isBlank() || script.isBlank()) return@mapNotNull null
                CustomMetricDefinition(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    script = script,
                    enabled = obj.optBoolean("enabled", true),
                    groupId = obj.optString("groupId", DEFAULT_SCRIPT_GROUP_ID).ifBlank { DEFAULT_SCRIPT_GROUP_ID },
                )
            }
        } catch (_: Exception) { emptyList() }

        private fun customMetricsToJson(items: List<CustomMetricDefinition>): String = JSONArray().apply {
            items.forEach { item -> put(JSONObject().apply {
                put("id", item.id); put("name", item.name); put("script", item.script); put("enabled", item.enabled); put("groupId", item.groupId)
            }) }
        }.toString()


        private fun parseCustomPlots(text: String): List<CustomPlotDefinition> = try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                var script = obj.optString("script")
                val engine = obj.optString("engine", "auto").ifBlank { "auto" }
                // Migration from Exvia <= 1.11 axis-source custom plots.
                if (script.isBlank()) {
                    val xSource = obj.optString("xSource").trim()
                    val ySource = obj.optString("ySource").trim()
                    if (xSource.isNotBlank() && ySource.isNotBlank()) {
                        script = legacyAxisPlotScript(xSource, ySource)
                    }
                }
                if (name.isBlank() || script.isBlank()) return@mapNotNull null
                CustomPlotDefinition(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    script = script,
                    engine = engine,
                    enabled = obj.optBoolean("enabled", true),
                    groupId = obj.optString("groupId", DEFAULT_SCRIPT_GROUP_ID).ifBlank { DEFAULT_SCRIPT_GROUP_ID },
                )
            }
        } catch (_: Exception) { emptyList() }

        private fun customPlotsToJson(items: List<CustomPlotDefinition>): String = JSONArray().apply {
            items.forEach { item -> put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("script", item.script)
                put("engine", item.engine)
                put("enabled", item.enabled)
                put("groupId", item.groupId)
            }) }
        }.toString()

        private fun legacyAxisPlotScript(xSource: String, ySource: String): String = """
            const rows = JSON.parse(jsonFile.content);
            const xKey = ${JSONObject.quote(xSource.substringAfter(':'))};
            const yKey = ${JSONObject.quote(ySource.substringAfter(':'))};
            const clean = rows.map((row, index) => ({
              x: row[xKey] ?? index,
              y: helpers.number(row[yKey])
            })).filter(d => Number.isFinite(d.y));
            const chart = Plot.plot({
              width: context.width,
              height: context.height,
              style: helpers.plotStyle(theme),
              x: {grid: true}, y: {grid: true},
              marks: [
                Plot.line(clean, {x: "x", y: "y", stroke: theme.observation}),
                Plot.dot(clean, {x: "x", y: "y", fill: theme.observation, r: 3, tip: true})
              ]
            });
            return chart;
        """.trimIndent()

        private fun parseTableStyleRules(text: String): List<TableStyleRule> = try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                val query = obj.optString("query").trim()
                if (name.isBlank() || query.isBlank()) return@mapNotNull null
                TableStyleRule(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    query = query,
                    foregroundScript = obj.optString("foregroundScript"),
                    backgroundScript = obj.optString("backgroundScript"),
                    contentScript = obj.optString("contentScript"),
                    enabled = obj.optBoolean("enabled", true),
                )
            }
        } catch (_: Exception) { emptyList() }

        private fun tableStyleRulesToJson(items: List<TableStyleRule>): String = JSONArray().apply {
            items.forEach { item -> put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("query", item.query)
                put("foregroundScript", item.foregroundScript)
                put("backgroundScript", item.backgroundScript)
                put("contentScript", item.contentScript)
                put("enabled", item.enabled)
            }) }
        }.toString()

        private fun parseFilterSnippets(text: String): List<FilterSnippet> = try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                val query = obj.optString("query").trim()
                if (name.isBlank() || query.isBlank()) return@mapNotNull null
                FilterSnippet(obj.optString("id").ifBlank { UUID.randomUUID().toString() }, name, query)
            }
        } catch (_: Exception) { emptyList() }

        private fun filterSnippetsToJson(items: List<FilterSnippet>): String = JSONArray().apply {
            items.forEach { item -> put(JSONObject().apply { put("id", item.id); put("name", item.name); put("query", item.query) }) }
        }.toString()
    }
}
