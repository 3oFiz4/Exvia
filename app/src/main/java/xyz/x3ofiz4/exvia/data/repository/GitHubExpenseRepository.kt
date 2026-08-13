package xyz.x3ofiz4.exvia.data.repository

import org.json.JSONArray
import org.json.JSONObject
import xyz.x3ofiz4.exvia.data.local.ExviaFileCache
import xyz.x3ofiz4.exvia.data.local.LocalStagingStore
import xyz.x3ofiz4.exvia.data.local.SelectedFileStore
import xyz.x3ofiz4.exvia.data.local.TokenStore
import xyz.x3ofiz4.exvia.data.remote.GitHubApi
import xyz.x3ofiz4.exvia.data.sql.FileSqlScriptEngine
import xyz.x3ofiz4.exvia.domain.model.repository.CommitPage
import xyz.x3ofiz4.exvia.domain.model.repository.RepoFile
import xyz.x3ofiz4.exvia.domain.model.settings.RepoSettings
import xyz.x3ofiz4.exvia.domain.model.table.DynamicRow
import xyz.x3ofiz4.exvia.domain.model.table.TableData
import xyz.x3ofiz4.exvia.domain.repository.DataSource
import xyz.x3ofiz4.exvia.domain.repository.ExpenseRepository
import xyz.x3ofiz4.exvia.domain.repository.WorkspaceSnapshot
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class GitHubExpenseRepository(
    private val tokenStore: TokenStore,
    private val cache: ExviaFileCache,
    private val selectedFileStore: SelectedFileStore,
    private val stagingStore: LocalStagingStore,
) : ExpenseRepository {

    override fun loadInitial(settings: RepoSettings): WorkspaceSnapshot {
        val token = requireToken(settings)
        val cachedFiles = cache.loadFiles(settings) ?: return synchronize(settings, discardStaged = false)
        val selected = chooseSelected(settings, cachedFiles)
        if (selected == null) {
            selectedFileStore.clear()
            return WorkspaceSnapshot(cachedFiles, null, emptyTable(), DataSource.EMPTY, false, stagingStore.hasAny(settings))
        }
        val api = GitHubApi(token, settings)
        stagingStore.load(settings, selected.path)?.let { staged ->
            selectedFileStore.save(selected.path)
            return WorkspaceSnapshot(
                cachedFiles,
                selected.path,
                stagedTable(api, staged.rows),
                DataSource.STAGED,
                true,
                true,
            )
        }
        val cachedFile = cache.loadFile(settings, selected.path)
        val matchingSha = cachedFile != null && (selected.sha.isBlank() || cachedFile.sha == selected.sha)
        if (!matchingSha) return loadSelected(settings, cachedFiles, selected.path, forceNetwork = true)
        return try {
            selectedFileStore.save(selected.path)
            WorkspaceSnapshot(
                cachedFiles,
                selected.path,
                api.parseCachedTable(cachedFile!!.text),
                DataSource.CACHE,
                false,
                stagingStore.hasAny(settings),
            )
        } catch (_: Exception) {
            cache.removeFile(settings, selected.path)
            loadSelected(settings, cachedFiles, selected.path, forceNetwork = true)
        }
    }

    override fun synchronize(settings: RepoSettings, discardStaged: Boolean): WorkspaceSnapshot {
        if (discardStaged) stagingStore.clearRepository(settings)
        val api = GitHubApi(requireToken(settings), settings)
        val files = api.listExpenseFiles()
        val selected = chooseSelected(settings, files)
        val fetched = selected?.let { api.fetchTableFile(it.path) }
        cache.saveFiles(settings, files)
        fetched?.first?.let { cache.saveFile(settings, it) }
        selectedFileStore.save(selected?.path)

        if (!discardStaged && selected != null) {
            stagingStore.load(settings, selected.path)?.let { staged ->
                return WorkspaceSnapshot(files, selected.path, stagedTable(api, staged.rows), DataSource.STAGED, true, true)
            }
        }
        return WorkspaceSnapshot(
            files,
            selected?.path,
            fetched?.second ?: emptyTable(),
            if (selected == null) DataSource.EMPTY else DataSource.NETWORK,
            false,
            stagingStore.hasAny(settings),
        )
    }

    override fun loadSelected(
        settings: RepoSettings,
        files: List<RepoFile>,
        path: String,
        forceNetwork: Boolean,
    ): WorkspaceSnapshot {
        val token = requireToken(settings)
        val api = GitHubApi(token, settings)
        // A selected local working tree always wins until the user explicitly Pulls/Re-syncs
        // with discard confirmation or Amends it to GitHub.
        stagingStore.load(settings, path)?.let { staged ->
            selectedFileStore.save(path)
            return WorkspaceSnapshot(files, path, stagedTable(api, staged.rows), DataSource.STAGED, true, true)
        }
        if (!forceNetwork) {
            cache.loadFile(settings, path)?.takeIf { cached ->
                val indexedSha = files.firstOrNull { it.path == path }?.sha.orEmpty()
                indexedSha.isBlank() || indexedSha == cached.sha
            }?.let { cached ->
                try {
                    selectedFileStore.save(path)
                    return WorkspaceSnapshot(files, path, api.parseCachedTable(cached.text), DataSource.CACHE, false, stagingStore.hasAny(settings))
                } catch (_: Exception) {
                    cache.removeFile(settings, path)
                }
            }
        }
        val fetched = api.fetchTableFile(path)
        cache.saveFile(settings, fetched.first)
        val updatedFiles = files.map { if (it.path == path) it.copy(sha = fetched.first.sha) else it }
        cache.saveFiles(settings, updatedFiles)
        selectedFileStore.save(path)
        return WorkspaceSnapshot(updatedFiles, path, fetched.second, DataSource.NETWORK, false, stagingStore.hasAny(settings))
    }

    override fun appendRow(
        settings: RepoSettings,
        files: List<RepoFile>,
        path: String,
        values: Map<String, String>,
    ): Pair<WorkspaceSnapshot, String> {
        val api = GitHubApi(requireToken(settings), settings)
        if (settings.automaticAmend) {
            ensureNoStagedForAutomatic(settings, path)
            val date = api.appendRow(path, values)
            return refreshedMutation(settings, api, files, path) to date
        }

        val table = effectiveTable(settings, api, files, path)
        val allKeys = (table.keys + values.keys).distinctBy { it.lowercase() }
        val dateKey = settings.detectDateKey(allKeys)
        val date = dateKey?.let { values[it]?.trim() }.orEmpty().ifBlank { currentDateTime() }
        val row = linkedMapOf<String, String>()
        allKeys.forEach { key ->
            val value = if (key == dateKey && values[key].isNullOrBlank()) date else values[key]?.trim().orEmpty()
            if (value.isNotBlank()) row[key] = value
        }
        val rows = orderedRows(table).toMutableList().apply { add(row) }
        val snapshot = stageMutation(
            settings, api, files, path, rows,
            buildExpenseMessage(settings, allKeys, date, row, path),
        )
        return snapshot to date
    }

    override fun updateRow(
        settings: RepoSettings,
        files: List<RepoFile>,
        path: String,
        row: DynamicRow,
        values: Map<String, String>,
    ): WorkspaceSnapshot {
        val api = GitHubApi(requireToken(settings), settings)
        if (settings.automaticAmend) {
            ensureNoStagedForAutomatic(settings, path)
            api.updateRow(path, row, values)
            return refreshedMutation(settings, api, files, path)
        }
        val table = effectiveTable(settings, api, files, path)
        val rows = orderedRows(table).toMutableList()
        val index = row.originalIndex
        require(index in rows.indices) { "The local row changed. Re-open the file before editing it." }
        rows[index] = LinkedHashMap(values.filterValues { it.isNotBlank() })
        return stageMutation(settings, api, files, path, rows, "Update expense row ${index + 1}")
    }

    override fun deleteRow(
        settings: RepoSettings,
        files: List<RepoFile>,
        path: String,
        row: DynamicRow,
    ): WorkspaceSnapshot {
        val api = GitHubApi(requireToken(settings), settings)
        if (settings.automaticAmend) {
            ensureNoStagedForAutomatic(settings, path)
            api.deleteRow(path, row)
            return refreshedMutation(settings, api, files, path)
        }
        val table = effectiveTable(settings, api, files, path)
        val rows = orderedRows(table).toMutableList()
        require(row.originalIndex in rows.indices) { "The local row changed. Re-open the file before deleting it." }
        rows.removeAt(row.originalIndex)
        return stageMutation(settings, api, files, path, rows, "Remove expense row ${row.originalIndex + 1}")
    }

    override fun replaceRows(
        settings: RepoSettings,
        files: List<RepoFile>,
        path: String,
        rows: List<Map<String, String>>,
        message: String,
    ): WorkspaceSnapshot {
        val api = GitHubApi(requireToken(settings), settings)
        if (!settings.automaticAmend) {
            return stageMutation(settings, api, files, path, rows, message)
        }
        ensureNoStagedForAutomatic(settings, path)
        api.replaceRows(path, rows, message)
        return refreshedMutation(settings, api, files, path)
    }

    override fun createFile(settings: RepoSettings, name: String): WorkspaceSnapshot {
        val api = GitHubApi(requireToken(settings), settings)
        val created = api.createExpenseFile(name)
        val files = api.listExpenseFiles()
        val fetched = api.fetchTableFile(created.path)
        cache.saveFiles(settings, files)
        cache.saveFile(settings, fetched.first)
        selectedFileStore.save(created.path)
        return WorkspaceSnapshot(files, created.path, fetched.second, DataSource.NETWORK, false, stagingStore.hasAny(settings))
    }

    override fun deleteFile(
        settings: RepoSettings,
        files: List<RepoFile>,
        file: RepoFile,
    ): WorkspaceSnapshot {
        val api = GitHubApi(requireToken(settings), settings)
        stagingStore.clear(settings, file.path)
        api.deleteExpenseFile(file)
        val loadedFiles = api.listExpenseFiles()
        cache.removeFile(settings, file.path)
        cache.saveFiles(settings, loadedFiles)
        val next = loadedFiles.firstOrNull()
        val data = if (next == null) {
            emptyTable()
        } else {
            stagingStore.load(settings, next.path)?.let { stagedTable(api, it.rows) }
                ?: cache.loadFile(settings, next.path)?.let { cached -> api.parseCachedTable(cached.text) }
                ?: api.fetchTableFile(next.path).also { cache.saveFile(settings, it.first) }.second
        }
        selectedFileStore.save(next?.path)
        return WorkspaceSnapshot(
            loadedFiles,
            next?.path,
            data,
            if (next == null) DataSource.EMPTY else if (stagingStore.has(settings, next.path)) DataSource.STAGED else DataSource.CACHE,
            next != null && stagingStore.has(settings, next.path),
            stagingStore.hasAny(settings),
        )
    }

    override fun executeFileScript(
        settings: RepoSettings,
        files: List<RepoFile>,
        script: String,
        outputFile: String,
    ): WorkspaceSnapshot {
        val api = GitHubApi(requireToken(settings), settings)
        val inputs = files.map { file ->
            val staged = stagingStore.load(settings, file.path)
            val table = staged?.let { stagedTable(api, it.rows) }
                ?: cache.loadFile(settings, file.path)?.let { cached ->
                    try { api.parseCachedTable(cached.text) } catch (_: Exception) { null }
                }
                ?: api.fetchTableFile(file.path).also { cache.saveFile(settings, it.first) }.second
            FileSqlScriptEngine.Input(file, table)
        }
        val result = FileSqlScriptEngine().execute(inputs, script)
        val created = api.upsertExpenseRows(outputFile, result.rows, "Execute Exvia SQLite file script: $outputFile")
        val loadedFiles = api.listExpenseFiles()
        val fetched = api.fetchTableFile(created.path)
        cache.saveFiles(settings, loadedFiles)
        cache.saveFile(settings, fetched.first)
        stagingStore.clear(settings, created.path)
        selectedFileStore.save(created.path)
        return WorkspaceSnapshot(loadedFiles, created.path, fetched.second, DataSource.NETWORK, false, stagingStore.hasAny(settings))
    }

    override fun hasStagedChanges(settings: RepoSettings, path: String?): Boolean =
        if (path == null) stagingStore.hasAny(settings) else stagingStore.has(settings, path)

    override fun amendStaged(
        settings: RepoSettings,
        files: List<RepoFile>,
        path: String,
    ): WorkspaceSnapshot {
        val staged = stagingStore.load(settings, path)
            ?: throw IllegalStateException("There are no staged Table changes for ${path.substringAfterLast('/')}.")
        val api = GitHubApi(requireToken(settings), settings)
        val remote = api.fetchTableFile(path).first
        if (staged.baseSha.isNotBlank() && remote.sha != staged.baseSha) {
            throw IllegalStateException(
                "The GitHub file changed after your local stage was created. Pull/Re-sync first, then re-apply the local change."
            )
        }
        val message = when (staged.messages.size) {
            0 -> "Amend staged Exvia table changes"
            1 -> staged.messages.first()
            else -> "Amend ${staged.messages.size} staged Exvia table changes"
        }
        api.replaceRows(path, staged.rows, message)
        stagingStore.clear(settings, path)
        return refreshedMutation(settings, api, files, path)
    }

    override fun discardStaged(settings: RepoSettings, path: String?) {
        if (path == null) stagingStore.clearRepository(settings) else stagingStore.clear(settings, path)
    }

    override fun listCommits(settings: RepoSettings, page: Int, perPage: Int): CommitPage =
        GitHubApi(requireToken(settings), settings).listCommits(page, perPage)

    override fun revertToCommit(settings: RepoSettings, commitSha: String): WorkspaceSnapshot {
        val api = GitHubApi(requireToken(settings), settings)
        stagingStore.clearRepository(settings)
        api.revertRepositoryToCommit(commitSha)
        // The selected tree may change every cached file SHA, so rebuild the remote index/cache view.
        return synchronize(settings, discardStaged = true)
    }

    private fun stageMutation(
        settings: RepoSettings,
        api: GitHubApi,
        files: List<RepoFile>,
        path: String,
        rows: List<Map<String, String>>,
        message: String,
    ): WorkspaceSnapshot {
        val existing = stagingStore.load(settings, path)
        val baseSha = existing?.baseSha
            ?: files.firstOrNull { it.path == path }?.sha
            ?: cache.loadFile(settings, path)?.sha
            ?: api.fetchTableFile(path).first.also { cache.saveFile(settings, it) }.sha
        val staged = LocalStagingStore.StagedTable(
            path = path,
            baseSha = baseSha,
            rows = rows.map { LinkedHashMap(it) },
            messages = ((existing?.messages ?: emptyList()) + message).takeLast(100),
            updatedAt = System.currentTimeMillis(),
        )
        stagingStore.save(settings, staged)
        selectedFileStore.save(path)
        return WorkspaceSnapshot(files, path, stagedTable(api, staged.rows), DataSource.STAGED, true, true)
    }

    private fun effectiveTable(
        settings: RepoSettings,
        api: GitHubApi,
        files: List<RepoFile>,
        path: String,
    ): TableData {
        stagingStore.load(settings, path)?.let { return stagedTable(api, it.rows) }
        cache.loadFile(settings, path)?.takeIf { cached ->
            val indexed = files.firstOrNull { it.path == path }?.sha.orEmpty()
            indexed.isBlank() || indexed == cached.sha
        }?.let { cached -> return api.parseCachedTable(cached.text) }
        val fetched = api.fetchTableFile(path)
        cache.saveFile(settings, fetched.first)
        return fetched.second
    }

    private fun stagedTable(api: GitHubApi, rows: List<Map<String, String>>): TableData =
        api.parseCachedTable(rowsToJson(rows))

    private fun rowsToJson(rows: List<Map<String, String>>): String = JSONArray().apply {
        rows.forEach { values ->
            put(JSONObject().apply {
                values.forEach { (key, value) -> if (value.isNotBlank()) put(key, value) }
            })
        }
    }.toString(2) + "\n"

    private fun orderedRows(table: TableData): List<Map<String, String>> =
        table.rows.sortedBy { it.originalIndex }.map { LinkedHashMap(it.values.filterValues(String::isNotBlank)) }

    private fun refreshedMutation(
        settings: RepoSettings,
        api: GitHubApi,
        files: List<RepoFile>,
        path: String,
    ): WorkspaceSnapshot {
        val fetched = api.fetchTableFile(path)
        cache.saveFile(settings, fetched.first)
        val updatedFiles = files.map { if (it.path == path) it.copy(sha = fetched.first.sha) else it }
        cache.saveFiles(settings, updatedFiles)
        selectedFileStore.save(path)
        return WorkspaceSnapshot(updatedFiles, path, fetched.second, DataSource.NETWORK, false, stagingStore.hasAny(settings))
    }

    private fun buildExpenseMessage(
        settings: RepoSettings,
        keys: List<String>,
        date: String,
        values: Map<String, String>,
        path: String,
    ): String {
        val money = settings.detectMoneyKey(keys)?.let { values[it]?.trim() }.orEmpty()
        val ticker = settings.detectTickerKey(keys)?.let { values[it]?.trim() }.orEmpty()
        val descriptionKey = values.keys.firstOrNull {
            it.equals("description", true) || it.equals("desc", true) || it.equals("name", true)
        }
        val description = descriptionKey?.let { values[it]?.trim() }.orEmpty()
        return if (money.isNotBlank() || ticker.isNotBlank() || description.isNotBlank()) {
            "Expense at $date: $money, ($ticker) $description"
        } else {
            "Amend ${path.substringAfterLast('/')} at $date"
        }
    }

    private fun ensureNoStagedForAutomatic(settings: RepoSettings, path: String) {
        check(!stagingStore.has(settings, path)) {
            "This file still has a local stage. Use Git → Amend to commit it, or Re-sync/Pull to discard it, before using Automatic amend."
        }
    }

    private fun chooseSelected(settings: RepoSettings, files: List<RepoFile>): RepoFile? {
        val savedPath = selectedFileStore.load()
        val defaultPath = settings.pathFor(settings.defaultJson)
        return files.firstOrNull { it.path == savedPath }
            ?: files.firstOrNull { it.path == defaultPath }
            ?: files.firstOrNull()
    }

    private fun requireToken(settings: RepoSettings): String {
        require(settings.isConfigured()) { "Configure GitHub owner/repository in Settings." }
        return tokenStore.load() ?: throw IllegalStateException("Add a GitHub PAT in Settings.")
    }

    private fun currentDateTime(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d/M/yy @ HH:mm"))

    private fun emptyTable() = TableData(emptyList(), emptyList(), null, null, null, null)
}
