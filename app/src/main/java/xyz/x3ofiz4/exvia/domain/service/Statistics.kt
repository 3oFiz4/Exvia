package xyz.x3ofiz4.exvia.domain.service
import xyz.x3ofiz4.exvia.domain.model.table.TableData


import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

object Statistics {
    data class KeyStats(
        val mean: Double?,
        val median: Double?,
        val mode: String?,
        val sum: Double?,
        val stdv: Double?,
        val minimum: Double?,
        val maximum: Double?,
        val range: Double?,
        val q1: Double?,
        val q2: Double?,
        val q3: Double?,
        val iqr: Double?,
        val skew: Double?,
        val kurtosis: Double?,
        val n: Int,
        val nUnique: Int,
        val variance: Double?,
        val meanMedianGap: Double?,
        val numericN: Int,
    )

    data class CumulativeBoxPoint(
        val x: Long,
        val sourceValue: Double,
        val mean: Double,
        val median: Double,
        val q1: Double,
        val q3: Double,
        val stdv: Double,
        val lowerStd: Double,
        val upperStd: Double,
        val lowerOutlierFence: Double,
        val upperOutlierFence: Double,
        val outliers: List<Double>,
        val sourceIsOutlier: Boolean,
        /** Number of raw rows merged into this exact timestamp observation. */
        val sourceCount: Int,
        /** Number of timestamp-level observations in the cumulative distribution. */
        val n: Int,
    )

    data class FinanceStats(
        val moneyKey: String,
        val totalIncome: Double,
        val totalExpenses: Double,
        val netCashFlow: Double,
        val savingsRate: Double?,
        val expenseRatio: Double?,
        val emergencyFundMonths: Double?,
        val debtToIncomeRatio: Double?,
        val averageExpense: Double?,
        val medianExpense: Double?,
        val expenseStdv: Double?,
        val averageIncome: Double?,
        val medianIncome: Double?,
        val incomeStdv: Double?,
        val largestExpense: Double?,
        val largestIncome: Double?,
        val expenseFrequencyPerDay: Double?,
        val incomeStabilityScore: Double?,
        val incomeGrowthRate: Double?,
        val incomeDiversity: Int,
        val largestIncomeSource: Pair<String, Double>?,
        val recurringIncomeRatio: Double?,
        val bonusIncomeRatio: Double?,
        val averageTimeBetweenIncomeDays: Double?,
        val expenseGrowthRate: Double?,
        val expenseVolatility: Double?,
        val recurringExpenseRatio: Double?,
        val subscriptionBurden: Double?,
        val cashBurnRate: Double?,
        val cashReserveDays: Double?,
        val averageDailyBalance: Double?,
        val daysUntilCashRunsOut: Double?,
        val noSpendDayRatio: Double?,
        val transactionCount: Int,
        val firstDate: String?,
        val lastDate: String?,
        val periodDays: Int?,
        val categorySpending: List<Pair<String, Double>>,
    )

    fun keyStats(values: List<String>): KeyStats {
        val clean = values.map { it.trim() }.filter { it.isNotBlank() }
        val numeric = clean.mapNotNull(::parseNumber).sorted()
        val mean = numeric.takeIf { it.isNotEmpty() }?.average()
        val variance = if (numeric.isNotEmpty() && mean != null) {
            numeric.sumOf { (it - mean).pow(2) } / numeric.size
        } else null
        val stdv = variance?.let(::sqrt)
        val frequencies = clean.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
        val mode = frequencies.firstOrNull()?.takeIf { it.value > 1 || clean.size == 1 }?.key
        val q1 = quantile(numeric, 0.25)
        val q2 = quantile(numeric, 0.50)
        val q3 = quantile(numeric, 0.75)
        val skew = if (numeric.size >= 2 && mean != null && stdv != null && stdv > 0.0) {
            numeric.sumOf { ((it - mean) / stdv).pow(3) } / numeric.size
        } else null
        val kurtosis = if (numeric.size >= 2 && mean != null && stdv != null && stdv > 0.0) {
            numeric.sumOf { ((it - mean) / stdv).pow(4) } / numeric.size - 3.0
        } else null

        return KeyStats(
            mean = mean,
            median = q2,
            mode = mode,
            sum = numeric.takeIf { it.isNotEmpty() }?.sum(),
            stdv = stdv,
            minimum = numeric.firstOrNull(),
            maximum = numeric.lastOrNull(),
            range = if (numeric.isNotEmpty()) numeric.last() - numeric.first() else null,
            q1 = q1,
            q2 = q2,
            q3 = q3,
            iqr = if (q1 != null && q3 != null) q3 - q1 else null,
            skew = skew,
            kurtosis = kurtosis,
            n = clean.size,
            nUnique = clean.toSet().size,
            variance = variance,
            meanMedianGap = if (mean != null && q2 != null) mean - q2 else null,
            numericN = numeric.size,
        )
    }

    /**
     * Builds one cumulative statistical snapshot per distinct timestamp.
     *
     * Raw rows that share the same parsed datetime are first merged into one observation
     * by summing their numeric values. The cumulative distribution is then built from
     * those timestamp totals, not from individual rows. This keeps the chart meaningful
     * when several expenses are recorded during the same minute.
     *
     * Missing timestamps are not synthesized: the returned x values retain their real
     * epoch positions, so sparse dates naturally appear as gaps on the time axis.
     * Whiskers are mean ± one population standard deviation.
     */
    fun cumulativeBoxSeries(points: List<Pair<Long, Double>>): List<CumulativeBoxPoint> {
        if (points.isEmpty()) return emptyList()

        val grouped = points.groupBy({ it.first }, { it.second })
        val buckets = grouped
            .map { (x, values) -> Triple(x, values.sum(), values.size) }
            .sortedBy { it.first }

        val ordered = mutableListOf<Double>()
        var sum = 0.0
        var sumSquares = 0.0

        return buckets.map { (x, value, sourceCount) ->
            val insertAt = ordered.binarySearch(value).let { if (it >= 0) it else -it - 1 }
            ordered.add(insertAt, value)
            sum += value
            sumSquares += value * value

            val n = ordered.size
            val mean = sum / n
            val variance = (sumSquares / n - mean * mean).coerceAtLeast(0.0)
            val stdv = sqrt(variance)
            val q1 = quantile(ordered, 0.25) ?: value
            val median = quantile(ordered, 0.50) ?: value
            val q3 = quantile(ordered, 0.75) ?: value
            val iqr = q3 - q1
            val lowerOutlierFence = q1 - 1.5 * iqr
            val upperOutlierFence = q3 + 1.5 * iqr
            val outliers = ordered.filter { it < lowerOutlierFence || it > upperOutlierFence }
            CumulativeBoxPoint(
                x = x,
                sourceValue = value,
                mean = mean,
                median = median,
                q1 = q1,
                q3 = q3,
                stdv = stdv,
                lowerStd = mean - stdv,
                upperStd = mean + stdv,
                lowerOutlierFence = lowerOutlierFence,
                upperOutlierFence = upperOutlierFence,
                outliers = outliers,
                sourceIsOutlier = value < lowerOutlierFence || value > upperOutlierFence,
                sourceCount = sourceCount,
                n = n,
            )
        }
    }


    /** Sum all numeric values that share the exact parsed datetime. */
    fun timestampTotals(points: List<Pair<Long, Double>>): List<Pair<Long, Double>> = points
        .groupBy({ it.first }, { it.second })
        .map { (x, values) -> x to values.sum() }
        .sortedBy { it.first }

    /** Running sum of timestamp-level totals. */
    fun cumulativeTotalSeries(points: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
        var total = 0.0
        return timestampTotals(points).map { (x, value) ->
            total += value
            x to total
        }
    }

    /** Fitted normal probability-density curve for numeric observations. */
    fun normalDistribution(values: List<Double>, sampleCount: Int = 121): List<Pair<Double, Double>> {
        if (values.isEmpty()) return emptyList()
        val mean = values.average()
        val variance = values.sumOf { (it - mean).pow(2) } / values.size
        val stdv = sqrt(variance)
        if (stdv <= 0.0 || !stdv.isFinite()) return listOf(mean to 1.0)
        val count = sampleCount.coerceAtLeast(21)
        val minX = mean - 4.0 * stdv
        val maxX = mean + 4.0 * stdv
        val step = (maxX - minX) / (count - 1)
        val scale = 1.0 / (stdv * sqrt(2.0 * PI))
        return (0 until count).map { i ->
            val x = minX + step * i
            val z = (x - mean) / stdv
            x to (scale * exp(-0.5 * z * z))
        }
    }

    fun financeStats(data: TableData): FinanceStats? {
        val moneyKey = data.moneyKey ?: return null
        data class Tx(val amount: Double, val income: Boolean, val epoch: Long?, val source: String, val text: String)
        val txs = mutableListOf<Tx>()
        val categorySpending = linkedMapOf<String, Double>()
        val incomeSources = linkedMapOf<String, Double>()

        data.rows.forEach { row ->
            val raw = row.values[moneyKey]?.trim().orEmpty()
            val parsed = parseNumber(raw) ?: return@forEach
            val income = raw.startsWith("+")
            val amount = kotlin.math.abs(parsed)
            val epoch = data.dateKey?.let { parseDate(row.values[it].orEmpty()) }
            val source = data.tickerKey?.let { row.values[it]?.trim() }.orEmpty().ifBlank { "Uncategorized" }
            val allText = row.values.values.joinToString(" ").lowercase()
            txs += Tx(amount, income, epoch, source, allText)
            if (income) incomeSources[source] = (incomeSources[source] ?: 0.0) + amount
            else categorySpending[source] = (categorySpending[source] ?: 0.0) + amount
        }
        if (txs.isEmpty()) return null

        val incomes = txs.filter { it.income }
        val expenses = txs.filterNot { it.income }
        val incomeValues = incomes.map { it.amount }
        val expenseValues = expenses.map { it.amount }
        val totalIncome = incomeValues.sum()
        val totalExpenses = expenseValues.sum()
        val net = totalIncome - totalExpenses
        val dated = txs.mapNotNull { tx -> tx.epoch?.let { it to tx } }.sortedBy { it.first }
        val firstEpoch = dated.firstOrNull()?.first
        val lastEpoch = dated.lastOrNull()?.first
        val dayMs = 86_400_000.0
        val periodDays = if (firstEpoch != null && lastEpoch != null) (((lastEpoch - firstEpoch) / dayMs).toInt() + 1).coerceAtLeast(1) else null
        val avgDailyExpense = periodDays?.takeIf { it > 0 }?.let { totalExpenses / it }
        val avgDailyIncome = periodDays?.takeIf { it > 0 }?.let { totalIncome / it }

        fun mean(values: List<Double>) = values.takeIf { it.isNotEmpty() }?.average()
        fun median(values: List<Double>) = quantile(values.sorted(), 0.5)
        fun std(values: List<Double>): Double? {
            if (values.isEmpty()) return null
            val m = values.average()
            return sqrt(values.sumOf { (it - m).pow(2) } / values.size)
        }
        fun growth(items: List<Tx>): Double? {
            val d = items.filter { it.epoch != null }.sortedBy { it.epoch }
            val first = d.firstOrNull()?.amount ?: return null
            val last = d.lastOrNull()?.amount ?: return null
            if (first == 0.0) return null
            return (last - first) / kotlin.math.abs(first) * 100.0
        }
        fun ratioAmount(items: List<Tx>, predicate: (Tx) -> Boolean): Double? {
            val total = items.sumOf { it.amount }
            if (total <= 0.0) return null
            return items.filter(predicate).sumOf { it.amount } / total * 100.0
        }
        val recurringWords = listOf("recurring", "subscription", "salary", "payroll", "rent", "mortgage", "utility", "monthly")
        val bonusWords = listOf("bonus", "windfall", "gift", "reward")
        val debtWords = listOf("debt", "loan", "credit", "mortgage")
        val subscriptionWords = listOf("subscription", "subscribe", "netflix", "spotify", "membership")
        fun containsAny(tx: Tx, words: List<String>) = words.any { it in tx.text }

        val incomeMean = mean(incomeValues)
        val incomeStd = std(incomeValues)
        val stability = if (incomeMean != null && incomeMean > 0.0 && incomeStd != null) {
            (100.0 / (1.0 + incomeStd / incomeMean)).coerceIn(0.0, 100.0)
        } else null

        val incomeDates = incomes.mapNotNull { it.epoch }.sorted()
        val avgIncomeGap = if (incomeDates.size >= 2) incomeDates.zipWithNext().map { (a, b) -> (b - a) / dayMs }.average() else null

        val debtPayments = expenses.filter { containsAny(it, debtWords) }.sumOf { it.amount }
        val subscription = expenses.filter { containsAny(it, subscriptionWords) }.sumOf { it.amount }
        val reserve = kotlin.math.max(net, 0.0)
        val reserveDays = avgDailyExpense?.takeIf { it > 0.0 }?.let { reserve / it }
        val emergencyMonths = reserveDays?.let { it / 30.4375 }
        val burn = if (avgDailyExpense != null && avgDailyIncome != null) avgDailyExpense - avgDailyIncome else avgDailyExpense

        // Reconstruct a zero-start daily closing balance from observed transactions.
        val dailySigned = linkedMapOf<Long, Double>()
        dated.forEach { (epoch, tx) ->
            val day = epoch / 86_400_000L
            dailySigned[day] = (dailySigned[day] ?: 0.0) + if (tx.income) tx.amount else -tx.amount
        }
        var balance = 0.0
        val balances = mutableListOf<Double>()
        if (dailySigned.isNotEmpty()) {
            val firstDay = dailySigned.keys.minOrNull()!!
            val lastDay = dailySigned.keys.maxOrNull()!!
            for (day in firstDay..lastDay) {
                balance += dailySigned[day] ?: 0.0
                balances += balance
            }
        }
        val noSpendRatio = if (periodDays != null && periodDays > 0 && dated.isNotEmpty()) {
            val spendDays = expenses.mapNotNull { it.epoch }.map { it / 86_400_000L }.toSet().size
            (periodDays - spendDays).coerceAtLeast(0).toDouble() / periodDays * 100.0
        } else null
        val daysUntilOut = if (balance > 0.0 && burn != null && burn > 0.0) balance / burn else null
        val sortedDates = dated
        val firstText = sortedDates.firstOrNull()?.second?.epoch?.let { epoch ->
            data.dateKey?.let { key -> data.rows.firstOrNull { parseDate(it.values[key].orEmpty()) == epoch }?.values?.get(key) }
        }
        val lastText = sortedDates.lastOrNull()?.second?.epoch?.let { epoch ->
            data.dateKey?.let { key -> data.rows.lastOrNull { parseDate(it.values[key].orEmpty()) == epoch }?.values?.get(key) }
        }

        return FinanceStats(
            moneyKey = moneyKey,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            netCashFlow = net,
            savingsRate = if (totalIncome > 0.0) net / totalIncome * 100.0 else null,
            expenseRatio = if (totalIncome > 0.0) totalExpenses / totalIncome * 100.0 else null,
            emergencyFundMonths = emergencyMonths,
            debtToIncomeRatio = if (totalIncome > 0.0) debtPayments / totalIncome * 100.0 else null,
            averageExpense = mean(expenseValues),
            medianExpense = median(expenseValues),
            expenseStdv = std(expenseValues),
            averageIncome = incomeMean,
            medianIncome = median(incomeValues),
            incomeStdv = incomeStd,
            largestExpense = expenseValues.maxOrNull(),
            largestIncome = incomeValues.maxOrNull(),
            expenseFrequencyPerDay = periodDays?.takeIf { it > 0 }?.let { expenses.size.toDouble() / it },
            incomeStabilityScore = stability,
            incomeGrowthRate = growth(incomes),
            incomeDiversity = incomeSources.size,
            largestIncomeSource = incomeSources.maxByOrNull { it.value }?.let { it.key to it.value },
            recurringIncomeRatio = ratioAmount(incomes) { containsAny(it, recurringWords) },
            bonusIncomeRatio = ratioAmount(incomes) { containsAny(it, bonusWords) },
            averageTimeBetweenIncomeDays = avgIncomeGap,
            expenseGrowthRate = growth(expenses),
            expenseVolatility = std(expenseValues),
            recurringExpenseRatio = ratioAmount(expenses) { containsAny(it, recurringWords) },
            subscriptionBurden = if (totalIncome > 0.0) subscription / totalIncome * 100.0 else null,
            cashBurnRate = burn,
            cashReserveDays = reserveDays,
            averageDailyBalance = balances.takeIf { it.isNotEmpty() }?.average(),
            daysUntilCashRunsOut = daysUntilOut,
            noSpendDayRatio = noSpendRatio,
            transactionCount = txs.size,
            firstDate = firstText,
            lastDate = lastText,
            periodDays = periodDays,
            categorySpending = categorySpending.entries.sortedByDescending { it.value }.take(10).map { it.key to it.value },
        )
    }

    fun parseNumber(value: String): Double? {
        val clean = value.trim().replace(",", "")
        if (!clean.matches(Regex("^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)$"))) return null
        return clean.toDoubleOrNull()
    }

    fun parseDate(value: String): Long? {
        if (value.isBlank()) return null
        val trimmed = value.trim()
        val localPatterns = listOf("d/M/yy @ HH:mm", "d/M/yy")
        for (pattern in localPatterns) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern)
                val local = if (pattern.contains('@')) {
                    LocalDateTime.parse(trimmed, formatter)
                } else {
                    LocalDate.parse(trimmed, formatter).atStartOfDay()
                }
                return local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) {
            }
        }
        return try {
            OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                Instant.parse(trimmed).toEpochMilli()
            } catch (_: Exception) {
                try {
                    LocalDateTime.parse(trimmed).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    try {
                        LocalDate.parse(trimmed).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
    }

    private fun quantile(sorted: List<Double>, p: Double): Double? {
        if (sorted.isEmpty()) return null
        if (sorted.size == 1) return sorted[0]
        val position = p * (sorted.size - 1)
        val lower = position.toInt()
        val upper = kotlin.math.ceil(position).toInt()
        if (lower == upper) return sorted[lower]
        val weight = position - lower
        return sorted[lower] * (1.0 - weight) + sorted[upper] * weight
    }
}
