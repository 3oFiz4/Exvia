package xyz.x3ofiz4.exvia.data.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.x3ofiz4.exvia.domain.model.custom.OcrBoundingBox

/**
 * High-performance Optical Character Recognition engine powered by Google ML Kit.
 * Extracts text from specified bounding boxes, runs customizable JavaScript and RegExp
 * transformation pipelines, and maps results to Exvia expense fields.
 */
object ReceiptOcrEngine {
    val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    data class BoundingBoxResult(
        val name: String,
        val mapTo: String,
        val rawText: String,
        val afterScriptText: String,
        val cleanedText: String,
        val cropRect: Rect,
        val scriptError: String? = null,
        val regexError: String? = null,
    )

    suspend fun processBoundingBoxes(
        bitmap: Bitmap,
        boxes: List<OcrBoundingBox>,
    ): List<BoundingBoxResult> = withContext(Dispatchers.Default) {
        val results = mutableListOf<BoundingBoxResult>()
        val imgW = bitmap.width
        val imgH = bitmap.height
        if (imgW <= 0 || imgH <= 0 || boxes.isEmpty()) return@withContext results

        for (box in boxes) {
            val left = (box.posX * imgW).toInt().coerceIn(0, imgW - 1)
            val top = (box.posY * imgH).toInt().coerceIn(0, imgH - 1)
            val right = ((box.posX + box.width) * imgW).toInt().coerceIn(left + 1, imgW)
            val bottom = ((box.posY + box.height) * imgH).toInt().coerceIn(top + 1, imgH)
            val width = (right - left).coerceAtLeast(1)
            val height = (bottom - top).coerceAtLeast(1)

            val cropRect = Rect(left, top, right, bottom)
            val cropped = try {
                Bitmap.createBitmap(bitmap, left, top, width, height)
            } catch (_: Exception) {
                null
            }

            val extractedText = if (cropped != null) {
                try {
                    val inputImage = InputImage.fromBitmap(cropped, 0)
                    val task = recognizer.process(inputImage)
                    val visionText = Tasks.await(task)
                    visionText.text.trim()
                } catch (_: Exception) {
                    ""
                }
            } else ""

            val (afterScript, cleaned, scriptErr, regexErr) = if (box.script.isNotBlank() || box.regex.isNotBlank()) {
                val proc = ReceiptScriptEngine.processValue(extractedText, box.script, box.regex)
                val finalCleaned = if (proc.finalValue.isNotBlank()) proc.finalValue else cleanExtractedText(box.mapTo, proc.finalValue)
                Tuple4(proc.afterScript, finalCleaned, proc.scriptError, proc.regexError)
            } else {
                val cleaned = cleanExtractedText(box.mapTo, extractedText)
                Tuple4(extractedText, cleaned, null, null)
            }

            results.add(
                BoundingBoxResult(
                    name = box.name,
                    mapTo = box.mapTo,
                    rawText = extractedText,
                    afterScriptText = afterScript,
                    cleanedText = cleaned,
                    cropRect = cropRect,
                    scriptError = scriptErr,
                    regexError = regexErr,
                )
            )
        }
        results
    }

    private data class Tuple4(
        val afterScript: String,
        val cleaned: String,
        val scriptError: String?,
        val regexError: String?,
    )

    /**
     * Extracts values mapped to target json fields as a map suitable for Table rows.
     */
    suspend fun extractExpenseFields(
        bitmap: Bitmap,
        boxes: List<OcrBoundingBox>,
    ): Map<String, String> = withContext(Dispatchers.Default) {
        val boxResults = processBoundingBoxes(bitmap, boxes)
        val map = linkedMapOf<String, String>()
        for (res in boxResults) {
            if (res.mapTo.isNotBlank() && res.cleanedText.isNotBlank()) {
                map[res.mapTo] = res.cleanedText
            }
        }
        map
    }

    suspend fun recognizeFullImage(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = Tasks.await(recognizer.process(inputImage))
            visionText.text.trim()
        } catch (_: Exception) {
            ""
        }
    }

    fun cleanExtractedText(mapTo: String, text: String): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return ""
        val lowerKey = mapTo.lowercase()
        return when {
            lowerKey.contains("price") || lowerKey.contains("amount") || lowerKey.contains("cost") || lowerKey.contains("total") -> {
                val regex = Regex("""[-+]?\$?\s*([0-9]+[.,][0-9]{2}|[0-9]+)""")
                val match = regex.find(trimmed)
                if (match != null) {
                    match.groupValues[1].replace(",", ".")
                } else {
                    trimmed.replace("$", "").replace("€", "").replace("£", "").trim()
                }
            }
            lowerKey.contains("date") -> {
                trimmed.lines().firstOrNull { it.isNotBlank() } ?: trimmed
            }
            else -> {
                trimmed.lines().filter { it.isNotBlank() }.joinToString(" ")
            }
        }
    }
}
