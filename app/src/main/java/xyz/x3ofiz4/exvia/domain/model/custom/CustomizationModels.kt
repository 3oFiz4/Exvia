package xyz.x3ofiz4.exvia.domain.model.custom

import xyz.x3ofiz4.exvia.domain.model.theme.PlotTheme
import xyz.x3ofiz4.exvia.domain.model.theme.ThemePalette

data class CustomMetricDefinition(
    val id: String,
    val name: String,
    val script: String,
    val enabled: Boolean = true,
)

data class FilterSnippet(
    val id: String,
    val name: String,
    val query: String,
)

data class CustomPlotDefinition(
    val id: String,
    val name: String,
    val script: String,
    val engine: String = "auto",
    val enabled: Boolean = true,
)

data class NamedUiTheme(
    val id: String,
    val name: String,
    val palette: ThemePalette,
)

data class NamedPlotTheme(
    val id: String,
    val name: String,
    val theme: PlotTheme,
)
