package xyz.x3ofiz4.exvia.presentation.main


import xyz.x3ofiz4.exvia.domain.model.repository.RepoFile
import xyz.x3ofiz4.exvia.domain.model.table.TableData

data class MainUiState(
    val files: List<RepoFile> = emptyList(),
    val selectedPath: String? = null,
    val sourceData: TableData = TableData(emptyList(), emptyList(), null, null, null, null),
    val visibleData: TableData = sourceData,
    val filterEnabled: Boolean = false,
    val filterQuery: String = "",
    val filterError: String? = null,
    val busy: Boolean = false,
    val status: String = "",
    val revision: Long = 0L,
)

sealed interface MainEffect {
    data class Error(val prefix: String, val throwable: Throwable) : MainEffect
    data class ToastMessage(val message: String) : MainEffect
}
