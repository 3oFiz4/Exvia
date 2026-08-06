package xyz.x3ofiz4.exvia.domain.repository


import xyz.x3ofiz4.exvia.domain.model.custom.FilterSnippet
import xyz.x3ofiz4.exvia.domain.model.settings.RepoSettings

interface ConfigurationRepository {
    fun loadSettings(): RepoSettings
    fun saveLocal(settings: RepoSettings, token: String? = null)
    fun loadToken(): String?
    fun clearToken()
    fun loadFilterSnippets(): List<FilterSnippet>
    fun saveFilterSnippets(snippets: List<FilterSnippet>)
    fun developerModeEnabled(): Boolean
    fun setDeveloperModeEnabled(enabled: Boolean)
    fun repoInitializationAsked(): Boolean
    fun setRepoInitializationAsked(asked: Boolean)
    fun synchronizeConfiguration(settings: RepoSettings, snippets: List<FilterSnippet>, developerMode: Boolean)
    fun createRepository(settings: RepoSettings, username: String, repo: String, branch: String, folder: String, defaultFile: String): RepoSettings
    fun submitReport(settings: RepoSettings, title: String, description: String, label: String, classification: String, developerMode: Boolean): String
}
