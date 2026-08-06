package xyz.x3ofiz4.exvia.presentation.main

import xyz.x3ofiz4.exvia.core.observable.EventStream
import xyz.x3ofiz4.exvia.core.observable.ObservableState
import xyz.x3ofiz4.exvia.domain.model.table.DynamicRow
import xyz.x3ofiz4.exvia.domain.model.repository.RepoFile
import xyz.x3ofiz4.exvia.domain.model.settings.RepoSettings
import xyz.x3ofiz4.exvia.domain.model.table.TableData
import xyz.x3ofiz4.exvia.domain.repository.DataSource
import xyz.x3ofiz4.exvia.domain.repository.ExpenseRepository
import xyz.x3ofiz4.exvia.domain.repository.WorkspaceSnapshot
import xyz.x3ofiz4.exvia.domain.service.SqlLikeFilter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns repository state and all table/file mutations.
 * Android Views only render [state] and emit intents to these methods.
 */
class MainViewModel(
    private val repository: ExpenseRepository,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) : AutoCloseable {
    val state = ObservableState(MainUiState())
    val effects = EventStream<MainEffect>()

    fun loadInitial(settings: RepoSettings) = launch("Loading Exvia data…", "Initial load failed") {
        val snapshot = repository.loadInitial(settings)
        publishSnapshot(snapshot, initialStatus(snapshot))
    }

    fun synchronize(settings: RepoSettings) = launch(
        "Re-syncing ${settings.folder.ifBlank { "/" }}/ with GitHub…",
        "Automatic GitHub sync failed",
    ) {
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
        val snapshot = repository.loadSelected(settings, state.value.files, path, forceNetwork)
        publishSnapshot(snapshot, "Loaded ${snapshot.tableData.rows.size} row(s) from ${path.substringAfterLast('/')}.")
    }

    fun setFilter(enabled: Boolean, query: String, announce: Boolean = true) {
        val current = state.value
        val filtered = filter(current.sourceData, enabled, query)
        state.set(current.copy(
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
        ))
    }

    fun replaceSchema(settings: RepoSettings, newKeys: List<String>) {
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
            val (snapshot, date) = repository.appendRow(settings, state.value.files, path, values)
            publishSnapshot(snapshot, "Committed and cached at $date.")
        }
    }

    fun updateRow(settings: RepoSettings, row: DynamicRow, values: Map<String, String>) {
        val path = state.value.selectedPath ?: return
        launch("Updating row…", "Update failed") {
            publishSnapshot(
                repository.updateRow(settings, state.value.files, path, row, values),
                "Row updated and cached.",
            )
        }
    }

    fun deleteRow(settings: RepoSettings, row: DynamicRow) {
        val path = state.value.selectedPath ?: return
        launch("Removing row…", "Remove row failed") {
            publishSnapshot(
                repository.deleteRow(settings, state.value.files, path, row),
                "Row removed and cached.",
            )
        }
    }

    fun createFile(settings: RepoSettings, name: String) = launch("Creating file…", "Create file failed") {
        val snapshot = repository.createFile(settings, name)
        publishSnapshot(snapshot, "Created and selected ${snapshot.selectedPath?.substringAfterLast('/')}.")
    }

    fun deleteFile(settings: RepoSettings, file: RepoFile) = launch("Removing ${file.name}…", "Remove file failed") {
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
        state.set(current.copy(
            files = snapshot.files,
            selectedPath = snapshot.selectedPath,
            sourceData = snapshot.tableData,
            visibleData = filtered.first,
            filterError = filtered.second,
            busy = false,
            status = status,
            revision = current.revision + 1,
        ))
    }

    private fun publishSource(source: TableData, status: String) {
        val current = state.value
        val filtered = filter(source, current.filterEnabled, current.filterQuery)
        state.set(current.copy(
            sourceData = source,
            visibleData = filtered.first,
            filterError = filtered.second,
            status = status,
            revision = current.revision + 1,
        ))
    }

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
