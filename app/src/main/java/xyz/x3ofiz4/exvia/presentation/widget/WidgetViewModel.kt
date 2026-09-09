package xyz.x3ofiz4.exvia.presentation.widget

import android.content.Context
import xyz.x3ofiz4.exvia.app.ExviaContainer
import xyz.x3ofiz4.exvia.core.observable.ObservableState
import xyz.x3ofiz4.exvia.data.remote.GitHubApi
import xyz.x3ofiz4.exvia.domain.model.settings.RepoSettings
import xyz.x3ofiz4.exvia.domain.model.table.TableData
import xyz.x3ofiz4.exvia.presentation.notification.NotificationDispatcher
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * ViewModel managing UI state and repository mutations for the Exvia App Widget and Quick Amend dialog.
 * Conforms strictly to Exvia's MVVM architecture.
 */
class WidgetViewModel(
    private val container: ExviaContainer,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) : AutoCloseable {

    val state = ObservableState(WidgetUiState())
    private var cachedSettings: RepoSettings? = null
    private var cachedTableData: TableData = TableData(emptyList(), emptyList(), null, null, null, null)

    fun loadInitial() {
        executor.execute {
            val settings = container.settingsStore.load()
            cachedSettings = settings
            val targetFileName = settings.defaultJson.ifBlank { "expenses.json" }
            val targetPath = settings.pathFor(targetFileName)

            val files = container.fileCache.loadFiles(settings) ?: emptyList()
            val token = container.tokenStore.load().orEmpty()
            val api = GitHubApi(token, settings)

            val tableData = try {
                val staged = container.stagingStore.load(settings, targetPath)
                if (staged != null) {
                    api.parseCachedTable(staged.rows.let { rows ->
                        org.json.JSONObject().apply {
                            put("expenses", org.json.JSONArray(rows.map { org.json.JSONObject(it) }))
                        }.toString()
                    })
                } else {
                    val cached = container.fileCache.loadFile(settings, targetPath)
                    if (cached != null) {
                        api.parseCachedTable(cached.text)
                    } else if (token.isNotBlank() && settings.isConfigured()) {
                        val fetched = api.fetchTableFile(targetPath)
                        container.fileCache.saveFile(settings, fetched.first)
                        fetched.second
                    } else {
                        TableData(emptyList(), emptyList(), null, null, null, null)
                    }
                }
            } catch (_: Exception) {
                TableData(emptyList(), emptyList(), null, null, null, null)
            }

            cachedTableData = tableData

            val imaginaryNames = settings.imaginaryFields.map { it.name.lowercase() }
            val hiddenKeys = settings.schemaRules.filter { it.script.contains("HIDDEN: true", ignoreCase = true) || it.script.contains("HIDDEN:true", ignoreCase = true) }
                .map { it.name.lowercase() }

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
            val tagsKey = settings.detectTagsKey(validKeys) ?: validKeys.firstOrNull { it.contains("tag", ignoreCase = true) }

            val suggestionsMap = mutableMapOf<String, List<String>>()
            for (key in validKeys) {
                suggestionsMap[key] = computeSuggestions(key, tagsKey, tableData)
            }

            val currentDate = currentFormattedDateTime()

            state.set(
                WidgetUiState(
                    targetFile = targetFileName,
                    targetPath = targetPath,
                    keys = validKeys,
                    dateKey = dateKey,
                    moneyKey = moneyKey,
                    tagsKey = tagsKey,
                    currentDate = currentDate,
                    suggestions = suggestionsMap,
                    logMessage = "Ready",
                    isLogSuccess = true,
                    isBusy = false,
                )
            )
        }
    }

    fun amend(
        context: Context,
        rawValues: Map<String, String>,
        onComplete: (Boolean, String) -> Unit,
    ) {
        state.set(state.value.copy(isBusy = true, logMessage = "Amending…"))
        executor.execute {
            try {
                val settings = cachedSettings ?: container.settingsStore.load()
                val targetPath = state.value.targetPath
                val files = container.fileCache.loadFiles(settings) ?: emptyList()

                val transformedValues = LinkedHashMap(rawValues)
                val dateKey = state.value.dateKey
                if (dateKey != null && transformedValues[dateKey].isNullOrBlank()) {
                    transformedValues[dateKey] = currentFormattedDateTime()
                }

                // Boolean 0/1 transforms from schema rules if applicable
                for (rule in settings.schemaRules) {
                    if (rule.script.contains("BOOLEAN_01: true", ignoreCase = true) || rule.script.contains("BOOLEAN_01:true", ignoreCase = true)) {
                        val current = transformedValues[rule.name]?.trim()
                        if (current == "1") transformedValues[rule.name] = "true"
                        else if (current == "0") transformedValues[rule.name] = "false"
                    }
                }

                val (snapshot, date) = container.expenseRepository.appendRow(
                    settings = settings,
                    files = files,
                    path = targetPath,
                    values = transformedValues,
                )

                // Trigger automation notification if rule matches
                val moneyKey = snapshot.tableData.moneyKey ?: settings.moneyKeyOverride.ifBlank { "PRICE" }
                val amount = transformedValues[moneyKey].orEmpty()
                val desc = transformedValues["description"] ?: transformedValues["desc"] ?: transformedValues["DESCRIPTION"] ?: ""
                evaluateNotificationRules(context, settings, date, amount, desc, state.value.targetFile)

                // Broadcast update to Home Screen widgets
                ExpenseAppWidgetProvider.updateAllWidgets(context.applicationContext, "Data amended")

                state.set(
                    state.value.copy(
                        isBusy = false,
                        logMessage = "Data amended",
                        isLogSuccess = true,
                    )
                )
                onComplete(true, "Data amended")
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Amend failed"
                state.set(
                    state.value.copy(
                        isBusy = false,
                        logMessage = "Error: $errorMsg",
                        isLogSuccess = false,
                    )
                )
                onComplete(false, errorMsg)
            }
        }
    }

    private fun evaluateNotificationRules(
        context: Context,
        settings: RepoSettings,
        date: String,
        amount: String,
        description: String,
        fileName: String,
    ) {
        val rules = settings.notificationRules.filter { it.enabled && it.eventName == "event.amend" }
        if (rules.isEmpty()) return

        try {
            if (context is android.app.Activity) {
                val dispatcher = NotificationDispatcher(context)
                for (rule in rules) {
                    dispatcher.post(
                        title = "Exvia · Data amended",
                        body = "Amended row in $fileName at $date ($amount $description)",
                    )
                }
            }
        } catch (_: Exception) {}
    }

    private fun computeSuggestions(key: String, tagsKey: String?, data: TableData): List<String> {
        val values = if (key == tagsKey) {
            data.rows.flatMap { it.values[key].orEmpty().split(',').map(String::trim).filter(String::isNotBlank) }
        } else {
            data.rows.map { it.values[key].orEmpty().trim() }.filter { it.isNotBlank() }
        }
        val counts = linkedMapOf<String, Pair<String, Int>>()
        for (value in values) {
            val normalized = value.lowercase()
            val old = counts[normalized]
            counts[normalized] = value to ((old?.second ?: 0) + 1)
        }
        return counts.values.sortedWith(
            compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() }
        ).map { it.first }
    }

    private fun currentFormattedDateTime(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("d/M/yy @ HH:mm"))

    override fun close() {
        executor.shutdown()
    }
}
