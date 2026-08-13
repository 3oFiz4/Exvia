package xyz.x3ofiz4.exvia.presentation.main

import xyz.x3ofiz4.exvia.domain.model.custom.TableQueryMode
import xyz.x3ofiz4.exvia.domain.model.custom.TableStyleRule
import xyz.x3ofiz4.exvia.domain.model.repository.RepoFile
import xyz.x3ofiz4.exvia.domain.model.repository.CommitPage
import xyz.x3ofiz4.exvia.domain.model.table.TableData
import xyz.x3ofiz4.exvia.domain.service.TableStyleResult

data class MainUiState(
    val files: List<RepoFile> = emptyList(),
    val selectedPath: String? = null,
    val sourceData: TableData = TableData(emptyList(), emptyList(), null, null, null, null),
    val visibleData: TableData = sourceData,
    val queryMode: TableQueryMode = TableQueryMode.FILTERING,
    val filterEnabled: Boolean = false,
    val filterQuery: String = "",
    val filterError: String? = null,
    val flagEnabled: Boolean = false,
    val flagQuery: String = "",
    val activeFlagRule: TableStyleRule? = null,
    val tableStyles: TableStyleResult = TableStyleResult(),
    val imaginaryKeys: Set<String> = emptySet(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasStagedChanges: Boolean = false,
    val hasAnyStagedChanges: Boolean = false,
    val busy: Boolean = false,
    val status: String = "",
    val revision: Long = 0L,
)

sealed interface MainEffect {
    data class Error(val prefix: String, val throwable: Throwable) : MainEffect
    data class ToastMessage(val message: String) : MainEffect
    data class GitHistoryLoaded(val page: CommitPage) : MainEffect
    data class AutomationEvent(val name: String, val payload: Map<String, String> = emptyMap()) : MainEffect
}
