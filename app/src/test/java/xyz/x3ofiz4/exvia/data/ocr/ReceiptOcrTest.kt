package xyz.x3ofiz4.exvia.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.x3ofiz4.exvia.data.local.SettingsStore
import xyz.x3ofiz4.exvia.domain.model.custom.OcrBoundingBox
import xyz.x3ofiz4.exvia.domain.model.custom.OcrTemplateDefinition

class ReceiptOcrTest {

    @Test
    fun ocrTemplate_serializationRoundtrip_preservesAllVariablesIncludingScriptAndRegex() {
        val box1 = OcrBoundingBox(
            name = "Receipt Total",
            posX = 0.65f,
            posY = 0.82f,
            width = 0.25f,
            height = 0.08f,
            mapTo = "price",
            script = ReceiptScriptEngine.DEFAULT_RECEIPT_JS_SCRIPT,
            regex = "\\d+(\\.\\d{2})?",
        )
        val box2 = OcrBoundingBox(
            name = "Date and Time",
            posX = 0.10f,
            posY = 0.15f,
            width = 0.40f,
            height = 0.05f,
            mapTo = "date",
            script = "return value.split('\\n')[0];",
            regex = "",
        )
        val box3 = OcrBoundingBox(
            name = "Vendor Store",
            posX = 0.10f,
            posY = 0.05f,
            width = 0.80f,
            height = 0.07f,
            mapTo = "description",
            script = "",
            regex = "s/Store #\\d+//g",
        )

        val originalTemplate = OcrTemplateDefinition(
            id = "template-1234",
            name = "Grocery Receipts",
            folderUri = "content://com.android.externalstorage.documents/tree/primary%3AReceipts",
            folderDisplayName = "Receipts",
            fileNamePattern = "^receipt_.*\\.(jpg|png)$",
            boundingBoxes = listOf(box1, box2, box3),
            enabled = true,
            lastScannedTimestamp = 1725900000000L,
            processedFiles = listOf("receipt_001.jpg", "receipt_002.png"),
        )

        val json = SettingsStore.ocrTemplatesToJson(listOf(originalTemplate))
        assertNotNull(json)
        assertTrue(json.contains("Grocery Receipts"))
        assertTrue(json.contains("Receipt Total"))
        assertTrue(json.contains("receipt_001.jpg"))

        val restoredList = SettingsStore.parseOcrTemplates(json)
        assertEquals(1, restoredList.size)

        val restored = restoredList[0]
        assertEquals("template-1234", restored.id)
        assertEquals("Grocery Receipts", restored.name)
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3AReceipts", restored.folderUri)
        assertEquals("Receipts", restored.folderDisplayName)
        assertEquals("^receipt_.*\\.(jpg|png)$", restored.fileNamePattern)
        assertTrue(restored.enabled)
        assertEquals(1725900000000L, restored.lastScannedTimestamp)
        assertEquals(listOf("receipt_001.jpg", "receipt_002.png"), restored.processedFiles)

        assertEquals(3, restored.boundingBoxes.size)

        val rBox1 = restored.boundingBoxes[0]
        assertEquals("Receipt Total", rBox1.name)
        assertEquals(0.65f, rBox1.posX, 0.001f)
        assertEquals(0.82f, rBox1.posY, 0.001f)
        assertEquals(0.25f, rBox1.width, 0.001f)
        assertEquals(0.08f, rBox1.height, 0.001f)
        assertEquals("price", rBox1.mapTo)
        assertEquals(ReceiptScriptEngine.DEFAULT_RECEIPT_JS_SCRIPT, rBox1.script)
        assertEquals("\\d+(\\.\\d{2})?", rBox1.regex)

        val rBox2 = restored.boundingBoxes[1]
        assertEquals("Date and Time", rBox2.name)
        assertEquals("return value.split('\\n')[0];", rBox2.script)
        assertEquals("", rBox2.regex)

        val rBox3 = restored.boundingBoxes[2]
        assertEquals("s/Store #\\d+//g", rBox3.regex)
    }

    @Test
    fun receiptScriptEngine_defaultScript_filtersPassesModifiesAndFinishes() {
        val script = ReceiptScriptEngine.DEFAULT_RECEIPT_JS_SCRIPT

        // 1. Numeric with currency symbol and comma
        val (res1, err1) = ReceiptScriptEngine.executeJs(script, "$ 1,250.50")
        assertEquals(null, err1)
        assertEquals("1.250.50", res1)

        // 2. Simple price with USD prefix
        val (res2, err2) = ReceiptScriptEngine.executeJs(script, "USD 89.99")
        assertEquals(null, err2)
        assertEquals("89.99", res2)

        // 3. Empty or whitespace string rejected (not pass)
        val (res3, err3) = ReceiptScriptEngine.executeJs(script, "   ")
        assertEquals(null, err3)
        assertEquals("", res3)
    }

    @Test
    fun receiptScriptEngine_customScript_evaluatesCorrectly() {
        val script = """
            var lower = value.toLowerCase();
            if (lower.indexOf("subtotal") >= 0) return "";
            return lower.replace("store", "Shop");
        """.trimIndent()

        val (res1, _) = ReceiptScriptEngine.executeJs(script, "Target Store #102")
        assertEquals("target Shop #102", res1)

        val (res2, _) = ReceiptScriptEngine.executeJs(script, "SUBTOTAL $20")
        assertEquals("", res2)
    }

    @Test
    fun receiptScriptEngine_applyRegex_handlesExtractionAndSubstitution() {
        // Extraction
        val (extracted, _) = ReceiptScriptEngine.applyRegex("Total Amount: $45.90 USD", """\d+\.\d{2}""")
        assertEquals("45.90", extracted)

        // Capture groups
        val (captured, _) = ReceiptScriptEngine.applyRegex("Ref: INV-98210-A", """INV-(\d+)""")
        assertEquals("98210", captured)

        // Substitution
        val (substituted, _) = ReceiptScriptEngine.applyRegex("Total: 12,50", "s/,/./")
        assertEquals("Total: 12.50", substituted)

        // Non-matching filter returns empty
        val (noMatch, _) = ReceiptScriptEngine.applyRegex("No numbers here", """\d+""")
        assertEquals("", noMatch)
    }

    @Test
    fun receiptScriptEngine_fullPipeline_combinesScriptAndRegex() {
        val script = """
            function process(value) {
                // FILTERING -> PASS/NOT PASS -> MODIFY/SUBSTITUTE -> FINISH
                if (!value) return "";
                return value.toUpperCase().replace("DISCOUNT", "DISC");
            }
        """.trimIndent()
        val regex = """DISC:\s*\$([0-9.]+)"""

        val result = ReceiptScriptEngine.processValue(
            rawValue = "Discount: $15.00 applied",
            script = script,
            regex = regex,
        )

        assertEquals("Discount: $15.00 applied", result.rawValue)
        assertEquals("DISC: $15.00 APPLIED", result.afterScript)
        assertEquals("15.00", result.finalValue)
        assertEquals(null, result.scriptError)
        assertEquals(null, result.regexError)
    }

    @Test
    fun parseOcrTemplates_handlesMalformedJsonGracefully() {
        val empty = SettingsStore.parseOcrTemplates("invalid json text")
        assertEquals(0, empty.size)

        val emptyArray = SettingsStore.parseOcrTemplates("[]")
        assertEquals(0, emptyArray.size)
    }

    @Test
    fun cleanExtractedText_correctlyExtractsPrices() {
        assertEquals("45.20", ReceiptOcrEngine.cleanExtractedText("price", "$ 45.20"))
        assertEquals("12.99", ReceiptOcrEngine.cleanExtractedText("price", "Total: 12.99 USD"))
        assertEquals("15.50", ReceiptOcrEngine.cleanExtractedText("amount", "EUR 15,50"))
        assertEquals("100", ReceiptOcrEngine.cleanExtractedText("cost", "$100"))
    }

    @Test
    fun cleanExtractedText_handlesDescriptionsAndDates() {
        val multilineDesc = "Walmart Supercenter\nStore #4412\nGroceries"
        val cleanedDesc = ReceiptOcrEngine.cleanExtractedText("description", multilineDesc)
        assertEquals("Walmart Supercenter Store #4412 Groceries", cleanedDesc)

        val multilineDate = "10/09/2026\n14:35 PM"
        val cleanedDate = ReceiptOcrEngine.cleanExtractedText("date", multilineDate)
        assertEquals("10/09/2026", cleanedDate)
    }

    @Test
    fun fileNameRegex_matchesTargetReceiptImages() {
        val regex = Regex("^receipt_.*\\.(jpg|png|jpeg)$", RegexOption.IGNORE_CASE)
        assertTrue(regex.containsMatchIn("receipt_001.jpg"))
        assertTrue(regex.containsMatchIn("RECEIPT_SEPTEMBER.PNG"))
        assertTrue(regex.containsMatchIn("receipt_store_12.jpeg"))

        assertFalse(regex.containsMatchIn("random_photo.jpg"))
        assertFalse(regex.containsMatchIn("receipt_001.pdf"))
        assertFalse(regex.containsMatchIn("invoice.txt"))
    }

    @Test
    fun ocrPluginManager_statuses_areDefinedCorrectly() {
        val statuses = OcrPluginManager.PluginStatus.entries
        assertTrue(statuses.contains(OcrPluginManager.PluginStatus.NOT_INSTALLED))
        assertTrue(statuses.contains(OcrPluginManager.PluginStatus.INSTALLED))
        assertTrue(statuses.contains(OcrPluginManager.PluginStatus.DOWNLOADING))
        assertTrue(statuses.contains(OcrPluginManager.PluginStatus.GMS_UNAVAILABLE))
    }
}
