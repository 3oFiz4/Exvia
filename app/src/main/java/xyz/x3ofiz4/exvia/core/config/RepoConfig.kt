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
    const val CONFIG_FILE_NAME = ".exvia-settings.json"
    const val CONFIG_PATH = ".exvia/.exvia-settings.json"
    const val FILTER_SNIPPETS_PATH = ".exvia/filtering-snippets.json"
    const val FLAGGING_SNIPPETS_PATH = ".exvia/flagging-snippets.json"
    const val COLOR_MAPPINGS_PATH = ".exvia/color-mappings.json"
    const val CUSTOM_METRICS_PATH = ".exvia/custom-metrics.json"
    const val CUSTOM_PLOTS_PATH = ".exvia/custom-plots.json"
    const val FILE_SCRIPTS_PATH = ".exvia/file-scripts.json"
    const val IMAGINARY_FIELDS_PATH = ".exvia/imaginary-fields.json"
    const val TABLE_RULES_PATH = ".exvia/"

    val TICKER_COLORS = mapOf(
        "FD" to "#FFB300",
        "BVG" to "#29B6F6",
    )
    const val DEFAULT_TICKER_COLOR = "#FFFFFF"
}
