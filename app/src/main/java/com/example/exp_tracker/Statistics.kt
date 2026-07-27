package com.example.exp_tracker

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
        val averageExpense: Double?,
        val averageIncome: Double?,
        val largestExpense: Double?,
        val largestIncome: Double?,
        val transactionCount: Int,
        val firstDate: String?,
        val lastDate: String?,
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
        var income = 0.0
        var expense = 0.0
        val incomes = mutableListOf<Double>()
        val expenses = mutableListOf<Double>()
        val category = linkedMapOf<String, Double>()
        val dated = mutableListOf<Pair<Long, String>>()

        for (row in data.rows) {
            val raw = row.values[moneyKey]?.trim().orEmpty()
            val number = parseNumber(raw) ?: continue
            val isIncome = raw.startsWith("+")
            if (isIncome) {
                val amount = kotlin.math.abs(number)
                income += amount
                incomes += amount
            } else {
                val amount = kotlin.math.abs(number)
                expense += amount
                expenses += amount
                val categoryKey = data.tickerKey?.let { row.values[it]?.trim() }.orEmpty().ifBlank { "Uncategorized" }
                category[categoryKey] = (category[categoryKey] ?: 0.0) + amount
            }
            data.dateKey?.let { dateKey ->
                val text = row.values[dateKey]?.trim().orEmpty()
                parseDate(text)?.let { dated += it to text }
            }
        }

        val net = income - expense
        val sortedDates = dated.sortedBy { it.first }
        return FinanceStats(
            moneyKey = moneyKey,
            totalIncome = income,
            totalExpenses = expense,
            netCashFlow = net,
            savingsRate = if (income > 0.0) net / income * 100.0 else null,
            averageExpense = expenses.takeIf { it.isNotEmpty() }?.average(),
            averageIncome = incomes.takeIf { it.isNotEmpty() }?.average(),
            largestExpense = expenses.maxOrNull(),
            largestIncome = incomes.maxOrNull(),
            transactionCount = incomes.size + expenses.size,
            firstDate = sortedDates.firstOrNull()?.second,
            lastDate = sortedDates.lastOrNull()?.second,
            categorySpending = category.entries.sortedByDescending { it.value }.take(8).map { it.key to it.value },
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
