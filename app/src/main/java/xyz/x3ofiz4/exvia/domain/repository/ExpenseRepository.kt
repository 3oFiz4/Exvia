package xyz.x3ofiz4.exvia.domain.repository


import xyz.x3ofiz4.exvia.domain.model.table.DynamicRow
import xyz.x3ofiz4.exvia.domain.model.repository.RepoFile
import xyz.x3ofiz4.exvia.domain.model.settings.RepoSettings
import xyz.x3ofiz4.exvia.domain.model.table.TableData

data class WorkspaceSnapshot(
    val files: List<RepoFile>,
    val selectedPath: String?,
    val tableData: TableData,
    val source: DataSource,
)

enum class DataSource { CACHE, NETWORK, EMPTY }

interface ExpenseRepository {
    fun loadInitial(settings: RepoSettings): WorkspaceSnapshot
    fun synchronize(settings: RepoSettings): WorkspaceSnapshot
    fun loadSelected(settings: RepoSettings, files: List<RepoFile>, path: String, forceNetwork: Boolean): WorkspaceSnapshot
    fun appendRow(settings: RepoSettings, files: List<RepoFile>, path: String, values: Map<String, String>): Pair<WorkspaceSnapshot, String>
    fun updateRow(settings: RepoSettings, files: List<RepoFile>, path: String, row: DynamicRow, values: Map<String, String>): WorkspaceSnapshot
    fun deleteRow(settings: RepoSettings, files: List<RepoFile>, path: String, row: DynamicRow): WorkspaceSnapshot
    fun replaceRows(settings: RepoSettings, files: List<RepoFile>, path: String, rows: List<Map<String, String>>, message: String): WorkspaceSnapshot
    fun createFile(settings: RepoSettings, name: String): WorkspaceSnapshot
    fun deleteFile(settings: RepoSettings, files: List<RepoFile>, file: RepoFile): WorkspaceSnapshot
    fun executeFileScript(settings: RepoSettings, files: List<RepoFile>, script: String, outputFile: String): WorkspaceSnapshot
}
