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

    private fun api(settings: RepoSettings): GitHubApi = GitHubApi(
        tokenStore.load() ?: throw IllegalStateException("GitHub PAT is required."),
        settings,
    )

    override fun synchronizeConfiguration(settings: RepoSettings, snippets: List<FilterSnippet>, developerMode: Boolean) {
        api(settings).upsertTextFile(
            GitHubApi.CONFIG_PATH,
            SettingsStore.settingsToConfigJson(settings, snippets, developerMode),
            "Update Exvia settings",
        )
    }

    override fun synchronizeFilterSnippets(settings: RepoSettings, snippets: List<FilterSnippet>) {
        api(settings).upsertTextFile(
            GitHubApi.FILTER_SNIPPETS_PATH,
            SettingsStore.filterSnippetsFileJson(snippets),
            "Update Exvia filtering snippets",
        )
    }

    override fun synchronizeTableRules(settings: RepoSettings) {
        val api = api(settings)
        api.upsertTextFile(
            GitHubApi.FLAGGING_SNIPPETS_PATH,
            SettingsStore.flaggingRulesFileJson(settings.flaggingRules),
            "Update Exvia flagging snippets",
        )
        api.upsertTextFile(
            GitHubApi.COLOR_MAPPINGS_PATH,
            SettingsStore.colorMappingsFileJson(settings.colorMappings),
            "Update Exvia color mappings",
        )
    }

    override fun synchronizeCustomMetrics(settings: RepoSettings) {
        api(settings).upsertTextFile(
            GitHubApi.CUSTOM_METRICS_PATH,
            SettingsStore.customMetricsFileJson(settings.customMetrics),
            "Update Exvia custom metrics",
        )
    }

    override fun synchronizeCustomPlots(settings: RepoSettings) {
        api(settings).upsertTextFile(
            GitHubApi.CUSTOM_PLOTS_PATH,
            SettingsStore.customPlotsFileJson(settings.customPlots),
            "Update Exvia custom plots",
        )
    }

    override fun synchronizeFileScripts(settings: RepoSettings) {
        api(settings).upsertTextFile(
            GitHubApi.FILE_SCRIPTS_PATH,
            SettingsStore.fileScriptsFileJson(settings.fileScripts),
            "Update Exvia file scripts",
        )
    }

    override fun synchronizeImaginaryFields(settings: RepoSettings) {
        api(settings).upsertTextFile(
            GitHubApi.IMAGINARY_FIELDS_PATH,
            SettingsStore.imaginaryFieldsFileJson(settings.imaginaryFields),
            "Update Exvia imaginary fields",
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
            append("Submitted from Exvia 1.13.5\n")
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
