package xyz.x3ofiz4.exvia.presentation.widget

data class WidgetUiState(
    val targetFile: String = "expenses.json",
    val targetPath: String = "Financial/expenses.json",
    val keys: List<String> = listOf("date", "price"),
    val dateKey: String? = "date",
    val moneyKey: String? = "price",
    val tagsKey: String? = null,
    val currentDate: String = "",
    val suggestions: Map<String, List<String>> = emptyMap(),
    val logMessage: String = "Ready",
    val isLogSuccess: Boolean = true,
    val isBusy: Boolean = false,
)
