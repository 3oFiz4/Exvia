package xyz.x3ofiz4.exvia.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import xyz.x3ofiz4.exvia.R
import xyz.x3ofiz4.exvia.app.ExviaApplication

/**
 * AppWidgetProvider rendering the home screen widget according to prototype/widget_idea.html.
 * Uses native Android RemoteViews with scrollable ListView for all available fields,
 * non-radial outer border, and connects to Exvia's MVVM architecture.
 */
class ExpenseAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = buildRemoteViews(context, appWidgetId, null)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_fields_list)
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
                    for (appWidgetId in appWidgetIds) {
                        val views = buildRemoteViews(context, appWidgetId, statusMessage)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_fields_list)
                    }
                }
            } catch (_: Exception) {}
        }

        fun buildRemoteViews(context: Context, appWidgetId: Int, statusMessage: String?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_expense_layout)

            // Resolve target file name from settings
            val container = (context.applicationContext as? ExviaApplication)?.container
            val settings = container?.settingsStore?.load()
            val targetFileName = settings?.defaultJson?.ifBlank { "expenses.json" } ?: "expenses.json"

            // Target file text: --> <filename> (slate-300 / #CBD5E1)
            views.setTextViewText(R.id.widget_target_file, "--> $targetFileName")

            // Set up RemoteViewsService intent for scrollable ListView of all possible fields
            val serviceIntent = Intent(context, ExpenseWidgetRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setRemoteAdapter(appWidgetId, R.id.widget_fields_list, serviceIntent)
            } else {
                @Suppress("DEPRECATION")
                views.setRemoteAdapter(R.id.widget_fields_list, serviceIntent)
            }
            views.setEmptyView(R.id.widget_fields_list, R.id.widget_empty_text)

            // Set up PendingIntent template for ListView item clicks
            val itemClickIntent = Intent(context, WidgetEntryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mutableFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val itemClickPendingIntent = PendingIntent.getActivity(
                context,
                101,
                itemClickIntent,
                mutableFlags,
            )
            views.setPendingIntentTemplate(R.id.widget_fields_list, itemClickPendingIntent)

            // Amend button pending intent
            val amendIntent = createPendingIntent(context, "price")
            views.setOnClickPendingIntent(R.id.widget_btn_amend, amendIntent)

            // Target file header click opens entry activity
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
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                flags,
            )
        }
    }
}
