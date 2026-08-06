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

    fun loadInitial(settings: RepoSettings) = launch("Loading Exvia data…", "Initial load failed") {
        automaticMappings = settings.colorMappings
        val snapshot = repository.loadInitial(settings)
        publishSnapshot(snapshot, initialStatus(snapshot))
    }

    fun synchronize(settings: RepoSettings) = launch(
        "Re-syncing ${settings.folder.ifBlank { "/" }}/ with GitHub…",
        "Automatic GitHub sync failed",
    ) {
        automaticMappings = settings.colorMappings
        val snapshot = repository.synchronize(settings)
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
        val source = current.sourceData.copy(
            keys = newKeys,
            dateKey = settings.detectDateKey(newKeys),
            moneyKey = settings.detectMoneyKey(newKeys),
            tickerKey = settings.detectTickerKey(newKeys),
            tagsKey = settings.detectTagsKey(newKeys),
        )
        publishSource(source, current.status)
    }

    fun amend(settings: RepoSettings, values: Map<String, String>) {
        val path = state.value.selectedPath ?: run {
            effects.emit(MainEffect.ToastMessage("Select or create a JSON file first."))
            return
        }
        launch("Committing to ${path.substringAfterLast('/')}…", "Amend failed") {
            automaticMappings = settings.colorMappings
            val (snapshot, date) = repository.appendRow(settings, state.value.files, path, values)
            publishSnapshot(snapshot, "Committed and cached at $date.")
        }
    }

    fun updateRow(settings: RepoSettings, row: DynamicRow, values: Map<String, String>) {
        val path = state.value.selectedPath ?: return
        launch("Updating row…", "Update failed") {
            automaticMappings = settings.colorMappings
            publishSnapshot(repository.updateRow(settings, state.value.files, path, row, values), "Row updated and cached.")
        }
    }

    fun deleteRow(settings: RepoSettings, row: DynamicRow) {
        val path = state.value.selectedPath ?: return
        launch("Removing row…", "Remove row failed") {
            automaticMappings = settings.colorMappings
            publishSnapshot(repository.deleteRow(settings, state.value.files, path, row), "Row removed and cached.")
        }
    }

    fun createFile(settings: RepoSettings, name: String) = launch("Creating file…", "Create file failed") {
        automaticMappings = settings.colorMappings
        val snapshot = repository.createFile(settings, name)
        publishSnapshot(snapshot, "Created and selected ${snapshot.selectedPath?.substringAfterLast('/')}.")
    }

    fun deleteFile(settings: RepoSettings, file: RepoFile) = launch("Removing ${file.name}…", "Remove file failed") {
        automaticMappings = settings.colorMappings
        val snapshot = repository.deleteFile(settings, state.value.files, file)
        publishSnapshot(snapshot, if (snapshot.selectedPath == null) {
            "Removed ${file.name}. No JSON files remain."
        } else {
            "Removed ${file.name}. Selected ${snapshot.selectedPath.substringAfterLast('/')}."
        })
    }

    private fun publishSnapshot(snapshot: WorkspaceSnapshot, status: String) {
        val current = state.value
        val filtered = filter(snapshot.tableData, current.filterEnabled, current.filterQuery)
        val next = current.copy(
            files = snapshot.files,
            selectedPath = snapshot.selectedPath,
            sourceData = snapshot.tableData,
            visibleData = filtered.first,
            filterError = filtered.second,
            busy = false,
            status = status,
            revision = current.revision + 1,
        )
        state.set(next.copy(tableStyles = resolveStyles(next.visibleData, next)))
    }

    private fun publishSource(source: TableData, status: String) {
        val current = state.value
        val filtered = filter(source, current.filterEnabled, current.filterQuery)
        val next = current.copy(
            sourceData = source,
            visibleData = filtered.first,
            filterError = filtered.second,
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
