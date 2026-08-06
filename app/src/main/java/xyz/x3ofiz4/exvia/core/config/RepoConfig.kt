package xyz.x3ofiz4.exvia.core.config


/**
 * First-run defaults only. All values can be changed later from the in-app
 * Settings drawer; no rebuild is required after that.
 */
object RepoConfig {
    const val OWNER = "YOUR_GITHUB_USERNAME_OR_ORG"
    const val REPO = "YOUR_REPOSITORY"
    const val BRANCH = "main"
    const val EXPENSE_FOLDER = "Financial"
    const val DEFAULT_JSON = "expenses.json"
    const val ARRAY_KEY = "expenses"
    const val EXVIA_FOLDER = ".exvia"
    const val CONFIG_FILE_NAME = "config.json"
    const val CONFIG_PATH = ".exvia/config.json"
    const val TABLE_RULES_FILE_NAME = "table-rules.json"
    const val TABLE_RULES_PATH = ".exvia/table-rules.json"

    val TICKER_COLORS = mapOf(
        "FD" to "#FFB300",
        "BVG" to "#29B6F6",
    )
    const val DEFAULT_TICKER_COLOR = "#FFFFFF"
}
