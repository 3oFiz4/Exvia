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

    override fun synchronizeWorkspace(settings: RepoSettings, snippets: List<FilterSnippet>, developerMode: Boolean) {
        val files = linkedMapOf(
            GitHubApi.CONFIG_PATH to SettingsStore.settingsToConfigJson(settings, snippets, developerMode),
            GitHubApi.FILTER_SNIPPETS_PATH to SettingsStore.filterSnippetsFileJson(snippets),
            GitHubApi.FLAGGING_SNIPPETS_PATH to SettingsStore.flaggingRulesFileJson(settings.flaggingRules),
            GitHubApi.COLOR_MAPPINGS_PATH to SettingsStore.colorMappingsFileJson(settings.colorMappings),
            GitHubApi.CUSTOM_METRICS_PATH to SettingsStore.customMetricsFileJson(settings.customMetrics),
            GitHubApi.CUSTOM_PLOTS_PATH to SettingsStore.customPlotsFileJson(settings.customPlots),
            GitHubApi.FILE_SCRIPTS_PATH to SettingsStore.fileScriptsFileJson(settings.fileScripts),
            GitHubApi.IMAGINARY_FIELDS_PATH to SettingsStore.imaginaryFieldsFileJson(settings.imaginaryFields),
            GitHubApi.SCRIPT_GROUPS_PATH to SettingsStore.scriptGroupsFileJson(settings.scriptGroups),
            GitHubApi.ENVIRONMENT_VARIABLES_PATH to SettingsStore.environmentVariablesFileJson(settings.environmentVariables),
            GitHubApi.NOTIFICATION_RULES_PATH to SettingsStore.notificationRulesFileJson(settings.notificationRules),
            GitHubApi.SCHEMA_RULES_PATH to SettingsStore.schemaRulesFileJson(settings.schemaRules),
            GitHubApi.METRIC_COLOR_MAPPINGS_PATH to SettingsStore.metricColorMappingsFileJson(settings.metricColorMappings),
            GitHubApi.CUSTOM_METRIC_INPUTS_PATH to SettingsStore.customMetricInputsFileJson(settings.customMetricInputs),
        )
        api(settings).upsertTextFilesAtomic(files, "Update Exvia workspace settings")
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

    override fun synchronizeScriptGroups(settings: RepoSettings) {
        api(settings).upsertTextFile(GitHubApi.SCRIPT_GROUPS_PATH, SettingsStore.scriptGroupsFileJson(settings.scriptGroups), "Update Exvia script groups")
    }

    override fun synchronizeEnvironmentVariables(settings: RepoSettings) {
        api(settings).upsertTextFile(GitHubApi.ENVIRONMENT_VARIABLES_PATH, SettingsStore.environmentVariablesFileJson(settings.environmentVariables), "Update Exvia environment variables")
    }

    override fun synchronizeNotificationRules(settings: RepoSettings) {
        api(settings).upsertTextFile(GitHubApi.NOTIFICATION_RULES_PATH, SettingsStore.notificationRulesFileJson(settings.notificationRules), "Update Exvia notification rules")
    }

    override fun synchronizeSchemaRules(settings: RepoSettings) {
        api(settings).upsertTextFile(GitHubApi.SCHEMA_RULES_PATH, SettingsStore.schemaRulesFileJson(settings.schemaRules), "Update Exvia schema rules")
    }

    override fun synchronizeMetricColorMappings(settings: RepoSettings) {
        api(settings).upsertTextFile(GitHubApi.METRIC_COLOR_MAPPINGS_PATH, SettingsStore.metricColorMappingsFileJson(settings.metricColorMappings), "Update Exvia metric color mappings")
    }

    override fun synchronizeCustomMetricInputs(settings: RepoSettings) {
        api(settings).upsertTextFile(GitHubApi.CUSTOM_METRIC_INPUTS_PATH, SettingsStore.customMetricInputsFileJson(settings.customMetricInputs), "Update Exvia custom metric inputs")
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
            append("Submitted from Exvia 1.13.6\n")
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
