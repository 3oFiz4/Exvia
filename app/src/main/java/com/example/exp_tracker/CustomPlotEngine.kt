package com.example.exp_tracker

/** Resolves configurable column/statistic/finance sources into numeric plot series. */
object CustomPlotEngine {
    data class ResolvedSeries(
        val points: List<Pair<Long, Double>>,
        val xLabels: Map<Long, String>,
        val timeAxis: Boolean,
        val xDescription: String,
        val yDescription: String,
    )

    sealed class Result {
        data class Success(val series: ResolvedSeries) : Result()
        data class Error(val message: String) : Result()
    }

    private sealed class Source {
        data class Column(val key: String) : Source()
        data object RowIndex : Source()
        data class Stat(val key: String, val metric: String) : Source()
        data class Finance(val key: String, val metric: String) : Source()
    }

    fun build(data: TableData, definition: CustomPlotDefinition): Result {
        if (data.rows.isEmpty()) return Result.Error("No rows are available in the current dataset.")
        val xSource = parseSource(definition.xSource, data.keys)
            ?: return Result.Error("Invalid x-axis source '${definition.xSource}'.")
        val ySource = parseSource(definition.ySource, data.keys)
            ?: return Result.Error("Invalid y-axis source '${definition.ySource}'.")

        val orderedRows = data.rows.sortedWith(compareBy<DynamicRow> {
            data.dateKey?.let { key -> Statistics.parseDate(it.values[key].orEmpty()) } ?: Long.MAX_VALUE
        }.thenBy { it.originalIndex })

        val xColumn = (xSource as? Source.Column)?.key
        val xIsDate = xColumn != null && orderedRows.any { Statistics.parseDate(it.values[xColumn].orEmpty()) != null }
        val steps: List<List<DynamicRow>> = if (xIsDate) {
            orderedRows.mapNotNull { row ->
                val epoch = Statistics.parseDate(row.values[xColumn].orEmpty()) ?: return@mapNotNull null
                epoch to row
            }.groupBy({ it.first }, { it.second }).toSortedMap().values.toList()
        } else {
            orderedRows.map { listOf(it) }
        }

        val prefix = mutableListOf<DynamicRow>()
        val points = mutableListOf<Pair<Long, Double>>()
        val labels = linkedMapOf<Long, String>()

        steps.forEachIndexed { index, stepRows ->
            prefix += stepRows
            val xCoordinate: Long
            val xLabel: String
            if (xIsDate && xColumn != null) {
                xCoordinate = Statistics.parseDate(stepRows.first().values[xColumn].orEmpty()) ?: return@forEachIndexed
                xLabel = stepRows.first().values[xColumn].orEmpty()
            } else {
                xCoordinate = index.toLong()
                xLabel = resolveSourceValue(xSource, stepRows.last(), prefix, data)?.let(::compact)
                    ?: when (xSource) {
                        is Source.Column -> stepRows.last().values[xSource.key].orEmpty().ifBlank { (index + 1).toString() }
                        Source.RowIndex -> (index + 1).toString()
                        else -> (index + 1).toString()
                    }
            }

            val y = when (ySource) {
                is Source.Column -> stepRows.mapNotNull { Statistics.parseNumber(it.values[ySource.key].orEmpty()) }.takeIf { it.isNotEmpty() }?.sum()
                else -> resolveSourceValue(ySource, stepRows.last(), prefix, data)
            } ?: return@forEachIndexed

            points += xCoordinate to y
            labels[xCoordinate] = xLabel
        }

        if (points.isEmpty()) return Result.Error("The selected y-axis source did not produce numeric values.")
        return Result.Success(
            ResolvedSeries(
                points = points,
                xLabels = labels,
                timeAxis = xIsDate,
                xDescription = describe(xSource),
                yDescription = describe(ySource),
            ),
        )
    }

    private fun parseSource(text: String, keys: List<String>): Source? {
        val clean = text.trim()
        if (clean.equals("row:index", true) || clean.equals("index", true)) return Source.RowIndex
        val parts = clean.split(':').map { it.trim() }
        if (parts.size == 1) {
            val key = resolveKey(parts[0], keys) ?: return null
            return Source.Column(key)
        }
        return when (parts[0].lowercase()) {
            "column", "col" -> resolveKey(parts.drop(1).joinToString(":"), keys)?.let(Source::Column)
            "stat", "statistics" -> if (parts.size >= 3) resolveKey(parts[1], keys)?.let { Source.Stat(it, normalize(parts.drop(2).joinToString(":"))) } else null
            "finance", "financial" -> if (parts.size >= 3) resolveKey(parts[1], keys)?.let { Source.Finance(it, normalize(parts.drop(2).joinToString(":"))) } else null
            "row" -> if (parts.getOrNull(1).equals("index", true)) Source.RowIndex else null
            else -> null
        }
    }

    private fun resolveSourceValue(
        source: Source,
        currentRow: DynamicRow,
        prefixRows: List<DynamicRow>,
        data: TableData,
    ): Double? = when (source) {
        is Source.Column -> Statistics.parseNumber(currentRow.values[source.key].orEmpty())
        Source.RowIndex -> prefixRows.size.toDouble()
        is Source.Stat -> statisticValue(Statistics.keyStats(prefixRows.map { it.values[source.key].orEmpty() }), source.metric)
        is Source.Finance -> Statistics.financeStats(data.copy(rows = prefixRows.toList(), moneyKey = source.key))?.let { financeValue(it, source.metric) }
    }

    private fun statisticValue(stats: Statistics.KeyStats, metric: String): Double? = when (metric) {
        "mean", "average" -> stats.mean
        "median", "q2" -> stats.median
        "meanmediangap", "meanminusmedian" -> stats.meanMedianGap
        "sum", "total" -> stats.sum
        "stdv", "std", "standarddeviation" -> stats.stdv
        "minimum", "min" -> stats.minimum
        "maximum", "max" -> stats.maximum
        "range" -> stats.range
        "q1" -> stats.q1
        "q3" -> stats.q3
        "iqr" -> stats.iqr
        "skew", "skewness" -> stats.skew
        "kurtosis" -> stats.kurtosis
        "variance" -> stats.variance
        "n", "count" -> stats.n.toDouble()
        "nunique", "uniquecount" -> stats.nUnique.toDouble()
        "numericn", "numericcount" -> stats.numericN.toDouble()
        else -> null
    }

    private fun financeValue(finance: Statistics.FinanceStats, metric: String): Double? = when (metric) {
        "totalincome", "income" -> finance.totalIncome
        "totalexpenses", "expenses" -> finance.totalExpenses
        "netcashflow", "net" -> finance.netCashFlow
        "savingsrate" -> finance.savingsRate
        "expenseratio" -> finance.expenseRatio
        "emergencyfund", "emergencyfundmonths" -> finance.emergencyFundMonths
        "debttoincomeratio", "dti" -> finance.debtToIncomeRatio
        "averageexpense", "meanexpense" -> finance.averageExpense
        "medianexpense" -> finance.medianExpense
        "expensestdv", "expensevolatility" -> finance.expenseStdv
        "averageincome", "meanincome" -> finance.averageIncome
        "medianincome" -> finance.medianIncome
        "incomestdv" -> finance.incomeStdv
        "largestexpense" -> finance.largestExpense
        "largestincome" -> finance.largestIncome
        "expensefrequencyperday" -> finance.expenseFrequencyPerDay
        "incomestabilityscore" -> finance.incomeStabilityScore
        "incomegrowthrate" -> finance.incomeGrowthRate
        "incomediversity" -> finance.incomeDiversity.toDouble()
        "recurringincomeratio" -> finance.recurringIncomeRatio
        "bonusincomeratio" -> finance.bonusIncomeRatio
        "averagetimebetweenincome", "averagetimebetweenincomedays" -> finance.averageTimeBetweenIncomeDays
        "expensegrowthrate" -> finance.expenseGrowthRate
        "recurringexpenseratio" -> finance.recurringExpenseRatio
        "subscriptionburden" -> finance.subscriptionBurden
        "cashburnrate" -> finance.cashBurnRate
        "cashreservedays" -> finance.cashReserveDays
        "averagedailybalance" -> finance.averageDailyBalance
        "daysuntilcashrunsout" -> finance.daysUntilCashRunsOut
        "nospenddayratio" -> finance.noSpendDayRatio
        "transactioncount" -> finance.transactionCount.toDouble()
        "perioddays" -> finance.periodDays?.toDouble()
        else -> null
    }

    private fun resolveKey(wanted: String, keys: List<String>): String? = keys.firstOrNull { it.equals(wanted.trim(), true) }
    private fun normalize(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }
    private fun describe(source: Source): String = when (source) {
        is Source.Column -> "column:${source.key}"
        Source.RowIndex -> "row:index"
        is Source.Stat -> "stat:${source.key}:${source.metric}"
        is Source.Finance -> "finance:${source.key}:${source.metric}"
    }

    private fun compact(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else "%.4f".format(value).trimEnd('0').trimEnd('.')
}
