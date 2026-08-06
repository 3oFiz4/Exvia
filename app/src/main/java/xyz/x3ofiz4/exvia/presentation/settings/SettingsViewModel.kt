package xyz.x3ofiz4.exvia.presentation.settings


import xyz.x3ofiz4.exvia.core.observable.EventStream
import xyz.x3ofiz4.exvia.core.observable.ObservableState
import xyz.x3ofiz4.exvia.domain.model.custom.FilterSnippet
import xyz.x3ofiz4.exvia.domain.model.settings.RepoSettings
import xyz.x3ofiz4.exvia.domain.repository.ConfigurationRepository
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Owns configuration, credentials metadata, repository bootstrap, and reporting. */
class SettingsViewModel(
    private val repository: ConfigurationRepository,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) : AutoCloseable {
    val state = ObservableState(loadState())
    val effects = EventStream<SettingsEffect>()

    fun reloadLocalState() = state.set(loadState())

    fun setDeveloperMode(enabled: Boolean) {
        repository.setDeveloperModeEnabled(enabled)
        state.update { it.copy(developerMode = enabled) }
    }

    fun markRepositoryInitializationAsked() {
        repository.setRepoInitializationAsked(true)
        state.update { it.copy(repoInitializationAsked = true) }
    }

    fun saveSnippets(snippets: List<FilterSnippet>) {
        repository.saveFilterSnippets(snippets)
        state.update { it.copy(snippets = snippets) }
    }

    fun saveSettings(next: RepoSettings, enteredToken: String?, snippets: List<FilterSnippet>, developerMode: Boolean) {
        repository.saveLocal(next, enteredToken)
        repository.saveFilterSnippets(snippets)
        repository.setDeveloperModeEnabled(developerMode)
        state.set(loadState().copy(settings = next, snippets = snippets, developerMode = developerMode))
        if (!state.value.hasToken || !next.isConfigured()) {
            effects.emit(SettingsEffect.Reload("Settings saved locally. GitHub config sync needs a PAT and configured repository."))
            return
        }
        launch("Saving settings and synchronizing configuration…", "Settings saved locally, but config sync failed") {
            repository.synchronizeConfiguration(next, snippets, developerMode)
            effects.emit(SettingsEffect.Reload("Settings saved and synchronized."))
        }
    }

    fun createRepository(
        current: RepoSettings,
        username: String,
        repo: String,
        branch: String,
        folder: String,
        defaultFile: String,
    ) = launch("Creating private GitHub repository…", "Repository creation failed") {
        val next = repository.createRepository(current, username, repo, branch, folder, defaultFile)
        state.set(loadState().copy(settings = next))
        effects.emit(SettingsEffect.RepositoryCreated(next))
    }

    fun submitReport(
        settings: RepoSettings,
        title: String,
        description: String,
        label: String,
        classification: String,
        developerMode: Boolean,
    ) = launch("Creating issue in ${settings.owner}/${settings.reportRepo}…", "Report submission failed") {
        val url = repository.submitReport(settings, title, description, label, classification, developerMode)
        effects.emit(SettingsEffect.ReportCreated(url))
    }

    fun token(): String? = repository.loadToken()
    fun clearToken() = repository.clearToken()

    fun parseTickerColors(text: String): Map<String, String> =
        xyz.x3ofiz4.exvia.domain.service.SettingsTextParser.parseTickerColors(text)

    fun parseColumnList(text: String): List<String> =
        xyz.x3ofiz4.exvia.domain.service.SettingsTextParser.parseColumnList(text)

    fun tickerColorsToText(colors: Map<String, String>): String =
        colors.entries.joinToString("\n") { "${it.key}=${it.value}" }

    private fun launch(message: String, errorPrefix: String, block: () -> Unit) {
        state.update { it.copy(busy = true, status = message) }
        executor.execute {
            try {
                block()
                state.update { it.copy(busy = false) }
            } catch (error: Throwable) {
                state.update { it.copy(busy = false) }
                effects.emit(SettingsEffect.Error(errorPrefix, error))
            }
        }
    }

    private fun loadState(): SettingsUiState = SettingsUiState(
        settings = repository.loadSettings(),
        snippets = repository.loadFilterSnippets(),
        developerMode = repository.developerModeEnabled(),
        hasToken = repository.loadToken() != null,
        repoInitializationAsked = repository.repoInitializationAsked(),
    )

    override fun close() {
        executor.shutdownNow()
    }
}
