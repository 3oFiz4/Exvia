package com.example.exp_tracker

<<<<<<< HEAD
data class ExpenseRow(
    val price: String,
    val ticker: String,
    val description: String,
    val tags: String,
    val date: String,
    val originalIndex: Int,
=======
data class DynamicRow(
    val values: LinkedHashMap<String, String>,
    val originalIndex: Int,
    val originalJson: String,
)

data class TableData(
    val keys: List<String>,
    val rows: List<DynamicRow>,
    val dateKey: String?,
    val moneyKey: String?,
    val tickerKey: String?,
    val tagsKey: String?,
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
)

data class GitHubFile(
    val path: String,
    val sha: String,
    val text: String,
)

data class RepoFile(
    val name: String,
    val path: String,
    val sha: String,
)

<<<<<<< HEAD
=======
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
    val tickerColors: Map<String, String>,
    val themePreset: ThemePreset,
    val palette: ThemePalette,
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

    private fun detectKey(keys: List<String>, override: String, aliases: List<String>): String? {
        if (override.isNotBlank()) {
            keys.firstOrNull { it.equals(override.trim(), ignoreCase = true) }?.let { return it }
        }
        for (alias in aliases) {
            keys.firstOrNull { normalizeKey(it) == normalizeKey(alias) }?.let { return it }
        }
        return null
    }

    private fun normalizeKey(value: String): String = value.lowercase().filter { it.isLetterOrDigit() || it == '_' }
}

>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
class GitHubHttpException(
    val statusCode: Int,
    message: String,
) : Exception(message)
