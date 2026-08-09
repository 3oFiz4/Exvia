package xyz.x3ofiz4.exvia.data.repository


import xyz.x3ofiz4.exvia.data.local.ExviaFileCache
import xyz.x3ofiz4.exvia.data.local.SelectedFileStore
import xyz.x3ofiz4.exvia.data.local.TokenStore
import xyz.x3ofiz4.exvia.data.remote.GitHubApi
import xyz.x3ofiz4.exvia.data.sql.FileSqlScriptEngine
import xyz.x3ofiz4.exvia.domain.model.table.DynamicRow
import xyz.x3ofiz4.exvia.domain.model.repository.RepoFile
import xyz.x3ofiz4.exvia.domain.model.settings.RepoSettings
import xyz.x3ofiz4.exvia.domain.model.table.TableData
import xyz.x3ofiz4.exvia.domain.repository.DataSource
import xyz.x3ofiz4.exvia.domain.repository.ExpenseRepository
import xyz.x3ofiz4.exvia.domain.repository.WorkspaceSnapshot

class GitHubExpenseRepository(
    private val tokenStore: TokenStore,
    private val cache: ExviaFileCache,
    private val selectedFileStore: SelectedFileStore,
) : ExpenseRepository {

    override fun loadInitial(settings: RepoSettings): WorkspaceSnapshot {
        val token = requireToken(settings)
        val cachedFiles = cache.loadFiles(settings) ?: return synchronize(settings)
        val selected = chooseSelected(settings, cachedFiles)
        if (selected == null) {
            selectedFileStore.clear()
            return WorkspaceSnapshot(cachedFiles, null, emptyTable(), DataSource.EMPTY)
        }
        val cachedFile = cache.loadFile(settings, selected.path)
        val matchingSha = cachedFile != null && (selected.sha.isBlank() || cachedFile.sha == selected.sha)
        if (!matchingSha) return loadSelected(settings, cachedFiles, selected.path, forceNetwork = true)
        return try {
            selectedFileStore.save(selected.path)
            WorkspaceSnapshot(
                cachedFiles,
                selected.path,
                GitHubApi(token, settings).parseCachedTable(cachedFile!!.text),
                DataSource.CACHE,
            )
        } catch (_: Exception) {
            cache.removeFile(settings, selected.path)
            loadSelected(settings, cachedFiles, selected.path, forceNetwork = true)
        }
    }

    override fun synchronize(settings: RepoSettings): WorkspaceSnapshot {
        val api = GitHubApi(requireToken(settings), settings)
        val files = api.listExpenseFiles()
        val selected = chooseSelected(settings, files)
        val fetched = selected?.let { api.fetchTableFile(it.path) }
        cache.saveFiles(settings, files)
        fetched?.first?.let { cache.saveFile(settings, it) }
        selectedFileStore.save(selected?.path)
        return WorkspaceSnapshot(
            files,
            selected?.path,
            fetched?.second ?: emptyTable(),
            if (selected == null) DataSource.EMPTY else DataSource.NETWORK,
        )
    }

    override fun loadSelected(
        settings: RepoSettings,
        files: List<RepoFile>,
        path: String,
        forceNetwork: Boolean,
    ): WorkspaceSnapshot {
        val token = requireToken(settings)
        if (!forceNetwork) {
            cache.loadFile(settings, path)?.takeIf { cached ->
                val indexedSha = files.firstOrNull { it.path == path }?.sha.orEmpty()
                indexedSha.isBlank() || indexedSha == cached.sha
            }?.let { cached ->
                try {
                    selectedFileStore.save(path)
                    return WorkspaceSnapshot(
                        files,
                        path,
                        GitHubApi(token, settings).parseCachedTable(cached.text),
                        DataSource.CACHE,
                    )
                } catch (_: Exception) {
                    cache.removeFile(settings, path)
                }
            }
        }
        val api = GitHubApi(token, settings)
        val fetched = api.fetchTableFile(path)
        cache.saveFile(settings, fetched.first)
        val updatedFiles = files.map { if (it.path == path) it.copy(sha = fetched.first.sha) else it }
        cache.saveFiles(settings, updatedFiles)
        selectedFileStore.save(path)
        return WorkspaceSnapshot(updatedFiles, path, fetched.second, DataSource.NETWORK)
    }

    override fun appendRow(
        settings: RepoSettings,
        files: List<RepoFile>,
        path: String,
        values: Map<String, String>,
    ): Pair<WorkspaceSnapshot, String> {
        val api = GitHubApi(requireToken(settings), settings)
        val date = api.appendRow(path, values)
        return refreshedMutation(settings, api, files, path) to date
    }

    override fun updateRow(
        settings: RepoSettings,
        files: List<RepoFile>,
        path: String,
        row: DynamicRow,
        values: Map<String, String>,
    ): WorkspaceSnapshot {
        val api = GitHubApi(requireToken(settings), settings)
        api.updateRow(path, row, values)
        return refreshedMutation(settings, api, files, path)
    }

    override fun deleteRow(
        settings: RepoSettings,
        files: List<RepoFile>,
        path: String,
        row: DynamicRow,
    ): WorkspaceSnapshot {
        val api = GitHubApi(requireToken(settings), settings)
        api.deleteRow(path, row)
        return refreshedMutation(settings, api, files, path)
    }

    override fun replaceRows(
        settings: RepoSettings,
        files: List<RepoFile>,
        path: String,
        rows: List<Map<String, String>>,
        message: String,
    ): WorkspaceSnapshot {
        val api = GitHubApi(requireToken(settings), settings)
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
        return WorkspaceSnapshot(files, created.path, fetched.second, DataSource.NETWORK)
    }

    override fun deleteFile(
        settings: RepoSettings,
        files: List<RepoFile>,
        file: RepoFile,
    ): WorkspaceSnapshot {
        val api = GitHubApi(requireToken(settings), settings)
        api.deleteExpenseFile(file)
        val loadedFiles = api.listExpenseFiles()
        cache.removeFile(settings, file.path)
        cache.saveFiles(settings, loadedFiles)
        val next = loadedFiles.firstOrNull()
        val data = if (next == null) {
            emptyTable()
        } else {
            val cached = cache.loadFile(settings, next.path)
            if (cached != null) api.parseCachedTable(cached.text) else api.fetchTableFile(next.path).also {
                cache.saveFile(settings, it.first)
            }.second
        }
        selectedFileStore.save(next?.path)
        return WorkspaceSnapshot(loadedFiles, next?.path, data, if (next == null) DataSource.EMPTY else DataSource.CACHE)
    }

    override fun executeFileScript(
        settings: RepoSettings,
        files: List<RepoFile>,
        script: String,
        outputFile: String,
    ): WorkspaceSnapshot {
        val api = GitHubApi(requireToken(settings), settings)
        val inputs = files.map { file ->
            val table = cache.loadFile(settings, file.path)?.let { cached ->
                try { api.parseCachedTable(cached.text) } catch (_: Exception) { null }
            } ?: api.fetchTableFile(file.path).also { cache.saveFile(settings, it.first) }.second
            FileSqlScriptEngine.Input(file, table)
        }
        val result = FileSqlScriptEngine().execute(inputs, script)
        val created = api.upsertExpenseRows(outputFile, result.rows, "Execute Exvia SQLite file script: $outputFile")
        val loadedFiles = api.listExpenseFiles()
        val fetched = api.fetchTableFile(created.path)
        cache.saveFiles(settings, loadedFiles)
        cache.saveFile(settings, fetched.first)
        selectedFileStore.save(created.path)
        return WorkspaceSnapshot(loadedFiles, created.path, fetched.second, DataSource.NETWORK)
    }

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
        return WorkspaceSnapshot(updatedFiles, path, fetched.second, DataSource.NETWORK)
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

    private fun emptyTable() = TableData(emptyList(), emptyList(), null, null, null, null)
}
