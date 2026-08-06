package xyz.x3ofiz4.exvia.data.repository


import xyz.x3ofiz4.exvia.data.local.SelectedFileStore
import xyz.x3ofiz4.exvia.data.local.SettingsStore
import xyz.x3ofiz4.exvia.data.local.TokenStore
import xyz.x3ofiz4.exvia.data.remote.GitHubApi
import xyz.x3ofiz4.exvia.domain.model.custom.FilterSnippet
import xyz.x3ofiz4.exvia.domain.model.settings.RepoSettings
import xyz.x3ofiz4.exvia.domain.repository.ConfigurationRepository

class ConfigurationRepositoryImpl(
    private val settingsStore: SettingsStore,
    private val tokenStore: TokenStore,
    private val selectedFileStore: SelectedFileStore,
) : ConfigurationRepository {
    override fun loadSettings(): RepoSettings = settingsStore.load()

    override fun saveLocal(settings: RepoSettings, token: String?) {
        settingsStore.save(settings)
        token?.trim()?.takeIf { it.isNotBlank() }?.let(tokenStore::save)
        selectedFileStore.clear()
    }

    override fun saveTableRulesLocal(settings: RepoSettings) {
        settingsStore.save(settings)
    }

    override fun loadToken(): String? = tokenStore.load()
    override fun clearToken() = tokenStore.clear()
    override fun loadFilterSnippets(): List<FilterSnippet> = settingsStore.loadFilterSnippets()
    override fun saveFilterSnippets(snippets: List<FilterSnippet>) = settingsStore.saveFilterSnippets(snippets)
    override fun developerModeEnabled(): Boolean = settingsStore.developerModeEnabled()
    override fun setDeveloperModeEnabled(enabled: Boolean) = settingsStore.setDeveloperModeEnabled(enabled)
    override fun repoInitializationAsked(): Boolean = settingsStore.repoInitializationAsked()
    override fun setRepoInitializationAsked(asked: Boolean) = settingsStore.setRepoInitializationAsked(asked)

    override fun synchronizeConfiguration(settings: RepoSettings, snippets: List<FilterSnippet>, developerMode: Boolean) {
        val token = tokenStore.load() ?: throw IllegalStateException("GitHub PAT is required.")
        GitHubApi(token, settings).upsertTextFile(
            GitHubApi.CONFIG_PATH,
            SettingsStore.settingsToConfigJson(settings, snippets, developerMode),
            "Update Exvia configuration",
        )
    }

    override fun synchronizeTableRules(settings: RepoSettings) {
        val token = tokenStore.load() ?: throw IllegalStateException("GitHub PAT is required.")
        GitHubApi(token, settings).upsertTextFile(
            GitHubApi.TABLE_RULES_PATH,
            SettingsStore.tableRulesToJson(settings),
            "Update Exvia table rules",
        )
    }

    override fun createRepository(
        settings: RepoSettings,
        username: String,
        repo: String,
        branch: String,
        folder: String,
        defaultFile: String,
    ): RepoSettings {
        val token = tokenStore.load() ?: throw IllegalStateException("GitHub PAT is required.")
        GitHubApi(token, settings).createAndInitializeRepository(username, repo, branch, folder, defaultFile)
        val next = settings.copy(owner = username, repo = repo, branch = branch, folder = folder, defaultJson = defaultFile)
        settingsStore.save(next)
        settingsStore.setRepoInitializationAsked(true)
        return next
    }

    override fun submitReport(
        settings: RepoSettings,
        title: String,
        description: String,
        label: String,
        classification: String,
        developerMode: Boolean,
    ): String {
        val token = tokenStore.load() ?: throw IllegalStateException("GitHub PAT is required.")
        val body = buildString {
            append(description)
            append("\n\n---\n")
            append("Submitted from Exvia 1.13.3\n")
            append("Classification: $classification ($label)\n")
            append("Data repository: ${settings.owner}/${settings.repo}\n")
            append("Branch: ${settings.branch}\n")
            append("Developer Options: ${if (developerMode) "enabled" else "hidden"}\n")
        }
        return GitHubApi(token, settings).createIssue(
            targetOwner = settings.owner,
            targetRepo = "Exvia",
            title = title,
            bodyText = body,
            labels = listOf(label),
        )
    }
}
