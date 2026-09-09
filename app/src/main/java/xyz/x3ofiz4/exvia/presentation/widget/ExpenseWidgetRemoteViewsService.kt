package xyz.x3ofiz4.exvia.presentation.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import xyz.x3ofiz4.exvia.R
import xyz.x3ofiz4.exvia.app.ExviaApplication
import xyz.x3ofiz4.exvia.data.remote.GitHubApi
import xyz.x3ofiz4.exvia.domain.model.table.TableData
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * RemoteViewsService providing dynamic, scrollable field rows for the home screen widget ListView.
 * Automatically inspects the default JSON file to list all possible fields.
 */
class ExpenseWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ExpenseWidgetViewsFactory(applicationContext)
    }
}

class ExpenseWidgetViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    data class FieldItem(
        val key: String,
        val placeholder: String,
        val value: String,
    )

    private var items = listOf<FieldItem>()

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        val container = (context.applicationContext as? ExviaApplication)?.container ?: return
        val settings = container.settingsStore.load()
        val targetFileName = settings.defaultJson.ifBlank { "expenses.json" }
        val targetPath = settings.pathFor(targetFileName)

        val tableData: TableData = try {
            val staged = container.stagingStore.load(settings, targetPath)
            val api = GitHubApi(container.tokenStore.load().orEmpty(), settings)
            if (staged != null) {
                val jsonPayload = org.json.JSONObject().apply {
                    put("expenses", org.json.JSONArray(staged.rows.map { org.json.JSONObject(it) }))
                }.toString()
                api.parseCachedTable(jsonPayload)
            } else {
                val cached = container.fileCache.loadFile(settings, targetPath)
                if (cached != null) {
                    api.parseCachedTable(cached.text)
                } else {
                    TableData(emptyList(), emptyList(), null, null, null, null)
                }
            }
        } catch (_: Exception) {
            TableData(emptyList(), emptyList(), null, null, null, null)
        }

        val imaginaryNames = settings.imaginaryFields.map { it.name.lowercase() }
        val hiddenKeys = settings.schemaRules.filter {
            it.script.contains("HIDDEN: true", ignoreCase = true) || it.script.contains("HIDDEN:true", ignoreCase = true)
        }.map { it.name.lowercase() }

        val rawKeys = if (tableData.keys.isNotEmpty()) {
            tableData.keys
        } else {
            listOf("date", "price")
        }

        val validKeys = rawKeys.filterNot { k ->
            imaginaryNames.contains(k.lowercase()) || hiddenKeys.contains(k.lowercase())
        }.ifEmpty { listOf("date", "price") }

        val dateKey = settings.detectDateKey(validKeys) ?: validKeys.firstOrNull { it.contains("date", ignoreCase = true) } ?: "date"
        val moneyKey = settings.detectMoneyKey(validKeys) ?: validKeys.firstOrNull { it.contains("price", ignoreCase = true) || it.contains("amount", ignoreCase = true) } ?: "price"

        val currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d/M/yy @ HH:mm"))

        items = validKeys.map { key ->
            val isDate = key.equals(dateKey, ignoreCase = true)
            val isMoney = key.equals(moneyKey, ignoreCase = true)
            val placeholder = when {
                isMoney -> "0.00"
                isDate -> "date (optional)"
                else -> "$key (optional)"
            }
            val value = if (isDate) currentDate else ""
            FieldItem(key = key, placeholder = placeholder, value = value)
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_column_item)
        if (position in items.indices) {
            val item = items[position]
            rv.setTextViewText(R.id.widget_column_item_text, item.value)
            rv.setCharSequence(R.id.widget_column_item_text, "setHint", item.placeholder)

            val fillInIntent = Intent().apply {
                putExtra(ExpenseAppWidgetProvider.EXTRA_FOCUS_KEY, item.key)
            }
            rv.setOnClickFillInIntent(R.id.widget_column_item_text, fillInIntent)
        }
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
