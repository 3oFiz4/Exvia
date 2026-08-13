package xyz.x3ofiz4.exvia.domain.repository

import xyz.x3ofiz4.exvia.domain.model.custom.FilterSnippet
import xyz.x3ofiz4.exvia.domain.model.settings.RepoSettings

interface ConfigurationRepository {
    fun loadSettings(): RepoSettings
    fun saveLocal(settings: RepoSettings, token: String? = null)
    fun saveTableRulesLocal(settings: RepoSettings)
    fun loadToken(): String?
    fun clearToken()
    fun loadFilterSnippets(): List<FilterSnippet>
    fun saveFilterSnippets(snippets: List<FilterSnippet>)
    fun developerModeEnabled(): Boolean
    fun setDeveloperModeEnabled(enabled: Boolean)
    fun repoInitializationAsked(): Boolean
    fun setRepoInitializationAsked(asked: Boolean)
    fun synchronizeConfiguration(settings: RepoSettings, snippets: List<FilterSnippet>, developerMode: Boolean)
    fun synchronizeWorkspace(settings: RepoSettings, snippets: List<FilterSnippet>, developerMode: Boolean)
    fun synchronizeFilterSnippets(settings: RepoSettings, snippets: List<FilterSnippet>)
    fun synchronizeTableRules(settings: RepoSettings)
    fun synchronizeCustomMetrics(settings: RepoSettings)
    fun synchronizeCustomPlots(settings: RepoSettings)
    fun synchronizeFileScripts(settings: RepoSettings)
    fun synchronizeImaginaryFields(settings: RepoSettings)
    fun synchronizeScriptGroups(settings: RepoSettings)
    fun synchronizeEnvironmentVariables(settings: RepoSettings)
    fun synchronizeNotificationRules(settings: RepoSettings)
    fun synchronizeSchemaRules(settings: RepoSettings)
    fun synchronizeMetricColorMappings(settings: RepoSettings)
    fun synchronizeCustomMetricInputs(settings: RepoSettings)
    fun createRepository(settings: RepoSettings, username: String, repo: String, branch: String, folder: String, defaultFile: String): RepoSettings
    fun submitReport(settings: RepoSettings, title: String, description: String, label: String, classification: String, developerMode: Boolean): String
}
