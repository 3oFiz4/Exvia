package xyz.x3ofiz4.exvia.presentation.settings


import xyz.x3ofiz4.exvia.domain.model.custom.FilterSnippet
import xyz.x3ofiz4.exvia.domain.model.settings.RepoSettings

data class SettingsUiState(
    val settings: RepoSettings,
    val snippets: List<FilterSnippet>,
    val developerMode: Boolean,
    val hasToken: Boolean,
    val repoInitializationAsked: Boolean,
    val busy: Boolean = false,
    val status: String = "",
)

sealed interface SettingsEffect {
    data class Error(val prefix: String, val throwable: Throwable) : SettingsEffect
    data class Reload(val message: String) : SettingsEffect
    data class ReportCreated(val url: String) : SettingsEffect
    data class RepositoryCreated(val settings: RepoSettings) : SettingsEffect
    data class TableRulesSaved(val message: String) : SettingsEffect
}
