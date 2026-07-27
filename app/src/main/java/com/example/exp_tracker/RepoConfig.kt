package com.example.exp_tracker

/**
<<<<<<< HEAD
 * Edit the repository values and optional schema/color choices here.
=======
 * First-run defaults only. All values can be changed later from the in-app
 * Settings drawer; no rebuild is required after that.
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
 */
object RepoConfig {
    const val OWNER = "YOUR_GITHUB_USERNAME_OR_ORG"
    const val REPO = "YOUR_REPOSITORY"
    const val BRANCH = "main"
<<<<<<< HEAD

    // Only JSON files directly under this folder are shown in Files.
    const val EXPENSE_FOLDER = "Financial"

    // Used on first launch if this file exists; otherwise the first JSON file is selected.
    const val DEFAULT_JSON = "expenses.json"

    // Supported JSON roots:
    //   [ { ... }, { ... } ]
    // or
    //   { "expenses": [ { ... }, { ... } ] }
    const val ARRAY_KEY = "expenses"

    const val DATE_KEY = "date"
    const val PRICE_KEY = "price"
    const val TICKER_KEY = "ticker"
    const val DESCRIPTION_KEY = "description"
    const val TAGS_KEY = "tags"

    // Configure ticker/category colors here. Keys are case-insensitive in the UI.
    // Use any Android-compatible hex color, e.g. #RRGGBB or #AARRGGBB.
    val TICKER_COLORS = mapOf(
        "FD" to "#FFB300",   // food
        "BVG" to "#29B6F6",  // beverage
    )
    const val DEFAULT_TICKER_COLOR = "#FFFFFF"

    fun pathFor(fileName: String): String = listOf(
        EXPENSE_FOLDER.trim('/'),
        fileName.trim('/'),
    ).filter { it.isNotBlank() }.joinToString("/")
=======
    const val EXPENSE_FOLDER = "Financial"
    const val DEFAULT_JSON = "expenses.json"
    const val ARRAY_KEY = "expenses"

    val TICKER_COLORS = mapOf(
        "FD" to "#FFB300",
        "BVG" to "#29B6F6",
    )
    const val DEFAULT_TICKER_COLOR = "#FFFFFF"
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
}
