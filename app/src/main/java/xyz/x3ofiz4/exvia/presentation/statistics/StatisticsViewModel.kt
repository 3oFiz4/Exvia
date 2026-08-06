package xyz.x3ofiz4.exvia.presentation.statistics

import xyz.x3ofiz4.exvia.domain.model.table.TableData
import xyz.x3ofiz4.exvia.domain.service.Statistics

/** Presentation-facing façade for all statistical and finance calculations. */
class StatisticsViewModel {
    fun financeStats(data: TableData): Statistics.FinanceStats? = Statistics.financeStats(data)
    fun keyStats(values: List<String>): Statistics.KeyStats = Statistics.keyStats(values)
    fun parseNumber(value: String): Double? = Statistics.parseNumber(value)
    fun parseDate(value: String) = Statistics.parseDate(value)
}
