package xyz.x3ofiz4.exvia.data.local


import android.content.Context

/** Persists the currently selected repository path independently of the View. */
class SelectedFileStore(context: Context) {
    private companion object {
        const val PREFS = "exvia_ui"
        const val LEGACY_PREFS = "exp_tracker_ui"
        const val KEY_SELECTED_PATH = "selected_path"
    }

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        if (!preferences.contains(KEY_SELECTED_PATH)) {
            val legacy = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            legacy.getString(KEY_SELECTED_PATH, null)?.let(::save)
        }
    }

    fun load(): String? = preferences.getString(KEY_SELECTED_PATH, null)

    fun save(path: String?) {
        preferences.edit().apply {
            if (path.isNullOrBlank()) remove(KEY_SELECTED_PATH) else putString(KEY_SELECTED_PATH, path)
        }.apply()
    }

    fun clear() = save(null)
}
