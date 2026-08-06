package xyz.x3ofiz4.exvia.app


import android.content.Context
import xyz.x3ofiz4.exvia.data.local.ExviaFileCache
import xyz.x3ofiz4.exvia.data.local.SelectedFileStore
import xyz.x3ofiz4.exvia.data.local.SettingsStore
import xyz.x3ofiz4.exvia.data.local.TokenStore
import xyz.x3ofiz4.exvia.data.repository.ConfigurationRepositoryImpl
import xyz.x3ofiz4.exvia.data.repository.GitHubExpenseRepository
import xyz.x3ofiz4.exvia.domain.repository.ConfigurationRepository
import xyz.x3ofiz4.exvia.domain.repository.ExpenseRepository

/** Application-level dependency container. The View layer never constructs data sources directly. */
class ExviaContainer(context: Context) {
    private val appContext = context.applicationContext

    val tokenStore = TokenStore(appContext)
    val settingsStore = SettingsStore(appContext)
    val fileCache = ExviaFileCache(appContext)
    val selectedFileStore = SelectedFileStore(appContext)

    val expenseRepository: ExpenseRepository = GitHubExpenseRepository(
        tokenStore = tokenStore,
        cache = fileCache,
        selectedFileStore = selectedFileStore,
    )

    val configurationRepository: ConfigurationRepository = ConfigurationRepositoryImpl(
        settingsStore = settingsStore,
        tokenStore = tokenStore,
        selectedFileStore = selectedFileStore,
    )
}
