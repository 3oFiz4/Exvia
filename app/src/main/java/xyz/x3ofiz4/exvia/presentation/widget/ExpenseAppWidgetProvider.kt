package xyz.x3ofiz4.exvia.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import xyz.x3ofiz4.exvia.R
import xyz.x3ofiz4.exvia.app.ExviaApplication
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * AppWidgetProvider rendering the home screen widget according to prototype/widget_idea.html.
 * Uses native Android RemoteViews and connects to Exvia's MVVM architecture.
 */
class ExpenseAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = buildRemoteViews(context, null)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val status = intent.getStringExtra(EXTRA_STATUS)
            updateAllWidgets(context, status)
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "xyz.x3ofiz4.exvia.ACTION_UPDATE_WIDGET"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_FOCUS_KEY = "extra_focus_key"

        fun updateAllWidgets(context: Context, statusMessage: String? = null) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, ExpenseAppWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds.isNotEmpty()) {
                    val views = buildRemoteViews(context, statusMessage)
                    appWidgetManager.updateAppWidget(appWidgetIds, views)
                }
            } catch (_: Exception) {}
        }

        fun buildRemoteViews(context: Context, statusMessage: String?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_expense_layout)

            // Resolve target file name from settings
            val container = (context.applicationContext as? ExviaApplication)?.container
            val settings = container?.settingsStore?.load()
            val targetFileName = settings?.defaultJson?.ifBlank { "expenses.json" } ?: "expenses.json"

            // Target file text: --> <filename> (slate-300 / #CBD5E1)
            views.setTextViewText(R.id.widget_target_file, "--> $targetFileName")

            // Current date formatted: d/M/yy @ HH:mm
            val currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d/M/yy @ HH:mm"))
            views.setTextViewText(R.id.widget_input_date, currentDate)

            // Input price hint
            views.setCharSequence(R.id.widget_input_price, "setHint", "0.00")

            // Dynamic columns if extra keys are present in cache
            views.removeAllViews(R.id.widget_inputs_container)

            // Default date row
            val dateRow = RemoteViews(context.packageName, R.layout.widget_column_item).apply {
                setTextViewText(R.id.widget_column_item_text, currentDate)
                setOnClickPendingIntent(
                    R.id.widget_column_item_text,
                    createPendingIntent(context, "date"),
                )
            }
            views.addView(R.id.widget_inputs_container, dateRow)

            // Price row
            val priceRow = RemoteViews(context.packageName, R.layout.widget_column_item).apply {
                setCharSequence(R.id.widget_column_item_text, "setHint", "0.00")
                setOnClickPendingIntent(
                    R.id.widget_column_item_text,
                    createPendingIntent(context, "price"),
                )
            }
            views.addView(R.id.widget_inputs_container, priceRow)

            // Determine if more columns exist in cached table
            val targetPath = settings?.pathFor(targetFileName) ?: "Financial/$targetFileName"
            val cachedFile = if (container != null && settings != null) container.fileCache.loadFile(settings, targetPath) else null
            if (cachedFile != null) {
                try {
                    val root = org.json.JSONObject(cachedFile.text)
                    val expenses = root.optJSONArray("expenses")
                    if (expenses != null && expenses.length() > 0) {
                        val firstObj = expenses.optJSONObject(0)
                        if (firstObj != null) {
                            val keys = firstObj.keys().asSequence().toList()
                            val extraKeys = keys.filterNot { k ->
                                k.contains("date", ignoreCase = true) ||
                                k.contains("price", ignoreCase = true) ||
                                k.contains("amount", ignoreCase = true)
                            }
                            for (extraKey in extraKeys.take(3)) {
                                val extraRow = RemoteViews(context.packageName, R.layout.widget_column_item).apply {
                                    setCharSequence(R.id.widget_column_item_text, "setHint", "$extraKey (optional)")
                                    setOnClickPendingIntent(
                                        R.id.widget_column_item_text,
                                        createPendingIntent(context, extraKey),
                                    )
                                }
                                views.addView(R.id.widget_inputs_container, extraRow)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Amend button pending intent
            val amendIntent = createPendingIntent(context, "price")
            views.setOnClickPendingIntent(R.id.widget_btn_amend, amendIntent)

            // Container click
            val containerIntent = createPendingIntent(context, null)
            views.setOnClickPendingIntent(R.id.widget_target_file, containerIntent)

            // Status log line
            val logText = if (statusMessage != null) "log: $statusMessage" else "log: Ready"
            views.setTextViewText(R.id.widget_log_text, logText)
            views.setTextColor(R.id.widget_log_text, Color.parseColor("#86EFAC"))

            return views
        }

        private fun createPendingIntent(context: Context, focusKey: String?): PendingIntent {
            val intent = Intent(context, WidgetEntryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (focusKey != null) {
                    putExtra(EXTRA_FOCUS_KEY, focusKey)
                }
            }
            val requestCode = focusKey?.hashCode() ?: 100
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
