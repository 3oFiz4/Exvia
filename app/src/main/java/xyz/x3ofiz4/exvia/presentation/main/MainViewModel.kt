package xyz.x3ofiz4.exvia.presentation.main

import xyz.x3ofiz4.exvia.core.observable.EventStream
import xyz.x3ofiz4.exvia.core.observable.ObservableState
import xyz.x3ofiz4.exvia.domain.model.custom.TableQueryMode
import xyz.x3ofiz4.exvia.domain.model.custom.TableStyleRule
import xyz.x3ofiz4.exvia.domain.model.table.DynamicRow
import xyz.x3ofiz4.exvia.domain.model.repository.RepoFile
import xyz.x3ofiz4.exvia.domain.model.settings.RepoSettings
import xyz.x3ofiz4.exvia.domain.model.table.TableData
import xyz.x3ofiz4.exvia.domain.repository.DataSource
import xyz.x3ofiz4.exvia.domain.repository.ExpenseRepository
import xyz.x3ofiz4.exvia.domain.repository.WorkspaceSnapshot
import xyz.x3ofiz4.exvia.domain.service.SqlLikeFilter
import xyz.x3ofiz4.exvia.domain.service.TableStyleEngine
import java.util.UUID
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Owns repository state, query modes, table styling, and all table/file mutations. */
class MainViewModel(
    private val repository: ExpenseRepository,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) : AutoCloseable {
    val state = ObservableState(MainUiState())
    val effects = EventStream<MainEffect>()
    private var automaticMappings: List<TableStyleRule> = emptyList()
    private var coreData: TableData = TableData(emptyList(), emptyList(), null, null, null, null)

    /** Compact command history: only the changed row is stored, never a whole table snapshot. */
    private data class RowHistoryEntry(
        val path: String,
        val index: Int,
        val before: Map<String, String>?,
        val after: Map<String, String>?,
        val label: String,
    )
    private val undoStack = ArrayDeque<RowHistoryEntry>()
    private val redoStack = ArrayDeque<RowHistoryEntry>()

    fun loadInitial(settings: RepoSettings) = launch("Loading Exvia data…", "Initial load failed") {
        automaticMappings = settings.colorMappings
        val snapshot = repository.loadInitial(settings)
        clearHistory()
        publishSnapshot(snapshot, initialStatus(snapshot))
    }

    fun synchronize(settings: RepoSettings) = launch(
        "Re-syncing ${settings.folder.ifBlank { "/" }}/ with GitHub…",
        "Automatic GitHub sync failed",
    ) {
        automaticMappings = settings.colorMappings
        val snapshot = repository.synchronize(settings)
        clearHistory()
        publishSnapshot(snapshot, if (snapshot.selectedPath == null) {
            "Re-sync complete. No .json files found in ${settings.folder}/."
        } else {
            "Re-synced ${snapshot.tableData.rows.size} row(s) from ${snapshot.selectedPath.substringAfterLast('/')}."
        })
    }

    fun selectFile(settings: RepoSettings, path: String, forceNetwork: Boolean = false) = launch(
        "Fetching ${path.substringAfterLast('/')}…",
        "Could not load selected file",
    ) {
        automaticMappings = settings.colorMappings
        val snapshot = repository.loadSelected(settings, state.value.files, path, forceNetwork)
        clearHistory()
        publishSnapshot(snapshot, "Loaded ${snapshot.tableData.rows.size} row(s) from ${path.substringAfterLast('/')}.")
    }

    fun updateStyleSettings(settings: RepoSettings) {
        automaticMappings = settings.colorMappings
        val current = state.value
        state.set(current.copy(
            tableStyles = resolveStyles(current.visibleData, current),
            revision = current.revision + 1,
        ))
    }

    fun setQueryMode(mode: TableQueryMode) {
        state.update { it.copy(queryMode = mode, revision = it.revision + 1) }
    }

    fun setFilter(enabled: Boolean, query: String, announce: Boolean = true) {
        val current = state.value
        val filtered = filter(current.sourceData, enabled, query)
        val next = current.copy(
            visibleData = filtered.first,
            filterEnabled = enabled,
            filterQuery = query,
            filterError = filtered.second,
            status = if (!announce) current.status else when {
                filtered.second != null -> "Filter error: ${filtered.second}"
                enabled -> "Filter enabled: showing ${filtered.first.rows.size}/${current.sourceData.rows.size} row(s)."
                else -> "Filter disabled: showing ${current.sourceData.rows.size} row(s)."
            },
            revision = current.revision + 1,
        )
        state.set(next.copy(tableStyles = resolveStyles(next.visibleData, next)))
    }

    fun setFlag(enabled: Boolean, query: String, selectedRule: TableStyleRule?, announce: Boolean = true) {
        val current = state.value
        val effectiveRule = selectedRule?.copy(query = query.ifBlank { selectedRule.query }, enabled = true)
            ?: TableStyleRule(
                id = "runtime:${UUID.randomUUID()}",
                name = "Current flagging expression",
                query = query,
                backgroundScript = "table['MATCHING_ROW'].back = \"#F723234D\"",
            )
        val next = current.copy(
            flagEnabled = enabled,
            flagQuery = query,
            activeFlagRule = effectiveRule,
            status = if (!announce) current.status else when {
                !enabled -> "Flagging disabled. Automatic Color Mapping remains active."
                query.isBlank() -> "Flagging enabled, but no matching syntax is selected."
                else -> "Flagging enabled. Matching rows keep their place and receive the selected visual rule."
            },
            revision = current.revision + 1,
        )
        state.set(next.copy(tableStyles = resolveStyles(next.visibleData, next)))
    }

    fun replaceSchema(settings: RepoSettings, newKeys: List<String>) {
        automaticMappings = settings.colorMappings
        val current = state.value
        val source = coreData.copy(
            keys = newKeys,
            dateKey = settings.detectDateKey(newKeys),
            moneyKey = settings.detectMoneyKey(newKeys),
            tickerKey = settings.detectTickerKey(newKeys),
            tagsKey = settings.detectTagsKey(newKeys),
        )
        coreData = source
        publishSource(source, current.status, emptySet())
    }

    fun applyImaginaryValues(
        settings: RepoSettings,
        fieldValues: Map<String, Map<Int, String>>,
    ) {
        val activeFields = settings.imaginaryFields
            .filter { it.enabled && it.name.isNotBlank() }
            .map { it.name }
            .distinctBy { it.lowercase() }
        val keys = (coreData.keys + activeFields).distinctBy { it.lowercase() }
        val rows = coreData.rows.map { row ->
            val next = LinkedHashMap(row.values)
            activeFields.forEach { field ->
                fieldValues[field]?.get(row.originalIndex)?.takeIf { it.isNotBlank() }?.let { next[field] = it }
            }
            row.copy(values = next)
        }
        val source = coreData.copy(
            keys = keys,
            rows = rows,
            dateKey = settings.detectDateKey(keys),
            moneyKey = settings.detectMoneyKey(keys),
            tickerKey = settings.detectTickerKey(keys),
            tagsKey = settings.detectTagsKey(keys),
        )
        publishSource(source, state.value.status, activeFields.toSet())
    }

    fun amend(settings: RepoSettings, values: Map<String, String>) {
        val path = state.value.selectedPath ?: run {
            effects.emit(MainEffect.ToastMessage("Select or create a JSON file first."))
            return
        }
        launch("Committing to ${path.substringAfterLast('/')}…", "Amend failed") {
            automaticMappings = settings.colorMappings
            val (snapshot, date) = repository.appendRow(settings, state.value.files, path, values)
            val appended = snapshot.tableData.rows.maxByOrNull { it.originalIndex }
            if (appended != null) recordHistory(
                RowHistoryEntry(path, appended.originalIndex, null, LinkedHashMap(appended.values), "Amend"),
                settings.undoHistoryLimit,
            )
            publishSnapshot(snapshot, "Committed and cached at $date.")
        }
    }

    fun updateRow(settings: RepoSettings, row: DynamicRow, values: Map<String, String>) {
        val path = state.value.selectedPath ?: return
        val before = coreData.rows.firstOrNull { it.originalIndex == row.originalIndex }?.values?.let(::LinkedHashMap)
            ?: LinkedHashMap(row.values)
        launch("Updating row…", "Update failed") {
            automaticMappings = settings.colorMappings
            val snapshot = repository.updateRow(settings, state.value.files, path, row, values)
            val after = snapshot.tableData.rows.firstOrNull { it.originalIndex == row.originalIndex }?.values?.let(::LinkedHashMap)
            if (after != null) recordHistory(RowHistoryEntry(path, row.originalIndex, before, after, "Edit row"), settings.undoHistoryLimit)
            publishSnapshot(snapshot, "Row updated and cached.")
        }
    }

    fun removeField(settings: RepoSettings, fieldName: String) {
        val path = state.value.selectedPath ?: return
        val rows: List<Map<String, String>> = coreData.rows.map { row ->
            LinkedHashMap(row.values.filterKeys { !it.equals(fieldName, ignoreCase = true) })
        }
        launch("Removing field $fieldName…", "Remove field failed") {
            automaticMappings = settings.colorMappings
            val snapshot = repository.replaceRows(
                settings = settings,
                files = state.value.files,
                path = path,
                rows = rows,
                message = "Remove field $fieldName",
            )
            clearHistory()
            publishSnapshot(snapshot, "Field '$fieldName' removed from the JSON file and cache.")
        }
    }

    fun deleteRow(settings: RepoSettings, row: DynamicRow) {
        val path = state.value.selectedPath ?: return
        val before = coreData.rows.firstOrNull { it.originalIndex == row.originalIndex }?.values?.let(::LinkedHashMap)
            ?: LinkedHashMap(row.values)
        launch("Removing row…", "Remove row failed") {
            automaticMappings = settings.colorMappings
            val snapshot = repository.deleteRow(settings, state.value.files, path, row)
            recordHistory(RowHistoryEntry(path, row.originalIndex, before, null, "Delete row"), settings.undoHistoryLimit)
            publishSnapshot(snapshot, "Row removed and cached.")
        }
    }

    fun undo(settings: RepoSettings) {
        val entry = undoStack.lastOrNull() ?: return
        applyHistory(settings, entry, undo = true)
    }

    fun redo(settings: RepoSettings) {
        val entry = redoStack.lastOrNull() ?: return
        applyHistory(settings, entry, undo = false)
    }

    fun executeFileScript(settings: RepoSettings, script: String, outputFile: String) = launch(
        "Executing SQLite file script…", "File script failed",
    ) {
        val snapshot = repository.executeFileScript(settings, state.value.files, script, outputFile)
        clearHistory()
        publishSnapshot(snapshot, "SQLite script wrote ${snapshot.tableData.rows.size} row(s) to ${snapshot.selectedPath?.substringAfterLast('/')}.")
    }

    fun createFile(settings: RepoSettings, name: String) = launch("Creating file…", "Create file failed") {
        automaticMappings = settings.colorMappings
        val snapshot = repository.createFile(settings, name)
        clearHistory()
        publishSnapshot(snapshot, "Created and selected ${snapshot.selectedPath?.substringAfterLast('/')}.")
    }

    fun deleteFile(settings: RepoSettings, file: RepoFile) = launch("Removing ${file.name}…", "Remove file failed") {
        automaticMappings = settings.colorMappings
        val snapshot = repository.deleteFile(settings, state.value.files, file)
        clearHistory()
        publishSnapshot(snapshot, if (snapshot.selectedPath == null) {
            "Removed ${file.name}. No JSON files remain."
        } else {
            "Removed ${file.name}. Selected ${snapshot.selectedPath.substringAfterLast('/')}."
        })
    }

    private fun recordHistory(entry: RowHistoryEntry, configuredLimit: Int) {
        undoStack.addLast(entry)
        val limit = configuredLimit.coerceIn(1, 50)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        state.update { it.copy(canUndo = false, canRedo = false) }
    }

    private fun applyHistory(settings: RepoSettings, entry: RowHistoryEntry, undo: Boolean) {
        val path = state.value.selectedPath ?: return
        if (path != entry.path) {
            effects.emit(MainEffect.ToastMessage("Undo/Redo history belongs to another file."))
            return
        }
        launch(if (undo) "Undoing ${entry.label}…" else "Redoing ${entry.label}…", if (undo) "Undo failed" else "Redo failed") {
            val rows = coreData.rows.sortedBy { it.originalIndex }.map { LinkedHashMap(it.values) }.toMutableList()
            val index = entry.index.coerceIn(0, rows.size)
            val target = if (undo) entry.before else entry.after
            val opposite = if (undo) entry.after else entry.before
            when {
                target == null && opposite != null -> {
                    require(index < rows.size) { "History row no longer exists at index ${entry.index}." }
                    rows.removeAt(index)
                }
                target != null && opposite == null -> rows.add(index, LinkedHashMap(target))
                target != null -> {
                    require(index < rows.size) { "History row no longer exists at index ${entry.index}." }
                    rows[index] = LinkedHashMap(target)
                }
            }
            val snapshot = repository.replaceRows(
                settings, state.value.files, path, rows,
                if (undo) "Undo Exvia table change: ${entry.label}" else "Redo Exvia table change: ${entry.label}",
            )
            if (undo) { undoStack.removeLast(); redoStack.addLast(entry) } else { redoStack.removeLast(); undoStack.addLast(entry) }
            publishSnapshot(snapshot, if (undo) "Undo complete: ${entry.label}." else "Redo complete: ${entry.label}.")
        }
    }

    private fun publishSnapshot(snapshot: WorkspaceSnapshot, status: String) {
        coreData = snapshot.tableData
        val current = state.value
        val filtered = filter(snapshot.tableData, current.filterEnabled, current.filterQuery)
        val next = current.copy(
            files = snapshot.files,
            selectedPath = snapshot.selectedPath,
            sourceData = snapshot.tableData,
            visibleData = filtered.first,
            filterError = filtered.second,
            imaginaryKeys = emptySet(),
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            busy = false,
            status = status,
            revision = current.revision + 1,
        )
        state.set(next.copy(tableStyles = resolveStyles(next.visibleData, next)))
    }

    private fun publishSource(source: TableData, status: String, imaginaryKeys: Set<String> = state.value.imaginaryKeys) {
        val current = state.value
        val filtered = filter(source, current.filterEnabled, current.filterQuery)
        val next = current.copy(
            sourceData = source,
            visibleData = filtered.first,
            filterError = filtered.second,
            imaginaryKeys = imaginaryKeys,
            status = status,
            revision = current.revision + 1,
        )
        state.set(next.copy(tableStyles = resolveStyles(next.visibleData, next)))
    }

    private fun resolveStyles(data: TableData, current: MainUiState) = TableStyleEngine.resolve(
        data,
        automaticMappings + listOfNotNull(current.activeFlagRule?.takeIf { current.flagEnabled && current.flagQuery.isNotBlank() }),
    )

    private fun filter(data: TableData, enabled: Boolean, query: String): Pair<TableData, String?> {
        if (!enabled || query.isBlank()) return data to null
        val result = SqlLikeFilter.apply(data, query)
        return if (result.error == null) data.copy(rows = result.rows) to null
        else data.copy(rows = emptyList()) to result.error
    }

    private fun launch(message: String, errorPrefix: String, block: () -> Unit) {
        state.update { it.copy(busy = true, status = message) }
        executor.execute {
            try {
                block()
            } catch (error: Throwable) {
                state.update { it.copy(busy = false) }
                effects.emit(MainEffect.Error(errorPrefix, error))
            }
        }
    }

    private fun initialStatus(snapshot: WorkspaceSnapshot): String = when {
        snapshot.selectedPath == null -> "Loaded cached file list. No JSON files are available."
        snapshot.source == DataSource.CACHE -> "Loaded ${snapshot.tableData.rows.size} row(s) from local cache: ${snapshot.selectedPath.substringAfterLast('/')}."
        else -> "Loaded ${snapshot.tableData.rows.size} row(s) from ${snapshot.selectedPath.substringAfterLast('/')}."
    }

    override fun close() {
        executor.shutdownNow()
    }
}
