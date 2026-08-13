package xyz.x3ofiz4.exvia.domain.model.settings

import xyz.x3ofiz4.exvia.domain.model.custom.CustomMetricDefinition
import xyz.x3ofiz4.exvia.domain.model.custom.EnvironmentVariableDefinition
import xyz.x3ofiz4.exvia.domain.model.custom.NotificationRule
import xyz.x3ofiz4.exvia.domain.model.custom.SchemaRuleDefinition
import xyz.x3ofiz4.exvia.domain.model.custom.MetricColorRule
import xyz.x3ofiz4.exvia.domain.model.custom.ScriptGroupDefinition
import xyz.x3ofiz4.exvia.domain.model.custom.CustomPlotDefinition
import xyz.x3ofiz4.exvia.domain.model.custom.FileScriptDefinition
import xyz.x3ofiz4.exvia.domain.model.custom.NamedPlotTheme
import xyz.x3ofiz4.exvia.domain.model.custom.NamedUiTheme
import xyz.x3ofiz4.exvia.domain.model.custom.TableStyleRule
import xyz.x3ofiz4.exvia.domain.model.theme.PlotTheme
import xyz.x3ofiz4.exvia.domain.model.theme.ThemePalette
import xyz.x3ofiz4.exvia.domain.model.theme.ThemePreset

data class RepoSettings(
    val owner: String,
    val repo: String,
    val branch: String,
    val folder: String,
    val defaultJson: String,
    val arrayKey: String,
    val dateKeyOverride: String,
    val moneyKeyOverride: String,
    val tickerKeyOverride: String,
    val tagsKeyOverride: String,
    /** Legacy value-to-color map retained for migration from <= 1.13.2. */
    val tickerColors: Map<String, String>,
    val flaggingRules: List<TableStyleRule>,
    val colorMappings: List<TableStyleRule>,
    val plotColumns: List<String>,
    val financeColumns: List<String>,
    val customMetrics: List<CustomMetricDefinition>,
    val customPlots: List<CustomPlotDefinition>,
    val scriptGroups: List<ScriptGroupDefinition>,
    val environmentVariables: List<EnvironmentVariableDefinition>,
    val notificationRules: List<NotificationRule>,
    val schemaRules: List<SchemaRuleDefinition>,
    val metricColorMappings: List<MetricColorRule>,
    /** Persisted values for Custom Metric input templates, keyed by `<metricId>:<inputName>`. */
    val customMetricInputs: Map<String, String>,
    val fileScripts: List<FileScriptDefinition>,
    val imaginaryFields: List<xyz.x3ofiz4.exvia.domain.model.custom.ImaginaryFieldDefinition>,
    val uiScale: Double,
    val textScale: Double,
    val iconMode: UiIconMode,
    val rowsPerPage: Int,
    val undoHistoryLimit: Int,
    /** When false, Table CRUD is staged locally until Git → Amend is pressed. */
    val automaticAmend: Boolean,
    val themePreset: ThemePreset,
    val palette: ThemePalette,
    val plotTheme: PlotTheme,
    val customUiThemes: List<NamedUiTheme>,
    val activeUiThemeId: String,
    val customPlotThemes: List<NamedPlotTheme>,
    val activePlotThemeId: String,
) {
    fun pathFor(fileName: String): String = listOf(
        folder.trim('/'),
        fileName.trim('/'),
    ).filter { it.isNotBlank() }.joinToString("/")

    fun isConfigured(): Boolean = owner.isNotBlank() && repo.isNotBlank() &&
        !owner.startsWith("YOUR_") && !repo.startsWith("YOUR_")

    fun detectDateKey(keys: List<String>): String? = detectKey(
        keys,
        dateKeyOverride,
        listOf("date", "datetime", "timestamp", "time", "created_at", "createdat"),
    )

    fun detectMoneyKey(keys: List<String>): String? = detectKey(
        keys,
        moneyKeyOverride,
        listOf("price", "amount", "cost", "expense", "value", "total", "money"),
    )

    fun detectTickerKey(keys: List<String>): String? = detectKey(
        keys,
        tickerKeyOverride,
        listOf("ticker", "category", "code", "type"),
    )

    fun detectTagsKey(keys: List<String>): String? = detectKey(
        keys,
        tagsKeyOverride,
        listOf("tags", "tag", "labels", "label"),
    )

    fun resolvedPlotColumns(keys: List<String>): List<String> = resolveConfiguredColumns(plotColumns, keys)
    fun resolvedFinanceColumns(keys: List<String>): List<String> = resolveConfiguredColumns(financeColumns, keys)

    private fun resolveConfiguredColumns(configured: List<String>, keys: List<String>): List<String> = configured
        .mapNotNull { wanted -> keys.firstOrNull { it.equals(wanted, ignoreCase = true) } }
        .distinct()

    private fun detectKey(keys: List<String>, override: String, aliases: List<String>): String? {
        if (override.isNotBlank()) {
            keys.firstOrNull { it.equals(override.trim(), ignoreCase = true) }?.let { return it }
        }
        for (alias in aliases) {
            keys.firstOrNull { normalizeKey(it) == normalizeKey(alias) }?.let { return it }
        }
        return null
    }

    private fun normalizeKey(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() || it == '_' }
}
