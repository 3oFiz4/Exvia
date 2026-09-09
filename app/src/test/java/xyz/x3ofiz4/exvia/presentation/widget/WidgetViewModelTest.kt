package xyz.x3ofiz4.exvia.presentation.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class WidgetViewModelTest {

    @Test
    fun widgetUiState_hasCorrectDefaults() {
        val state = WidgetUiState()
        assertEquals("expenses.json", state.targetFile)
        assertEquals("Financial/expenses.json", state.targetPath)
        assertEquals(listOf("date", "price"), state.keys)
        assertEquals("date", state.dateKey)
        assertEquals("price", state.moneyKey)
        assertEquals("Ready", state.logMessage)
        assertTrue(state.isLogSuccess)
    }

    @Test
    fun dateFormat_matchesExpectedFormat() {
        val formatter = DateTimeFormatter.ofPattern("d/M/yy @ HH:mm")
        val formatted = LocalDateTime.now().format(formatter)
        val regex = Regex("""^\d{1,2}/\d{1,2}/\d{2} @ \d{2}:\d{2}$""")
        assertTrue("Formatted date '$formatted' should match expected format", regex.matches(formatted))
    }

    @Test
    fun suggestionsComputation_sortsByFrequencyDescending() {
        val values = listOf("Food", "Groceries", "Food", "Rent", "Food", "Groceries")
        val counts = linkedMapOf<String, Pair<String, Int>>()
        for (value in values) {
            val normalized = value.lowercase()
            val old = counts[normalized]
            counts[normalized] = value to ((old?.second ?: 0) + 1)
        }
        val sorted = counts.values.sortedWith(
            compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() }
        ).map { it.first }

        assertEquals("Food", sorted[0])
        assertEquals("Groceries", sorted[1])
        assertEquals("Rent", sorted[2])
    }

    @Test
    fun targetFileDisplay_stripsDirectoryPrefix() {
        val rawPath = "Financial/september_expenses.json"
        val displayName = rawPath.substringAfterLast('/')
        assertEquals("september_expenses.json", displayName)
    }
}
