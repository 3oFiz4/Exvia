package xyz.x3ofiz4.exvia.data.ocr

import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

/**
 * Executes user JavaScript processors and post-extraction regular expressions
 * to transform, filter, and validate raw OCR extracted values before mapping them
 * to Exvia expense fields.
 */
object ReceiptScriptEngine {

    const val DEFAULT_RECEIPT_JS_SCRIPT = """/**
 * Receipt Value Processor
 * Pipeline: FILTERING -> PASS/NOT PASS -> MODIFY/SUBSTITUTE -> FINISH
 * @param {string} value - Raw OCR extracted text
 * @returns {string} - Modified value to map into Exvia (or null/empty to reject)
 */
function process(value) {
    // 1. FILTERING: Initial cleanup & trimming
    var text = (value || "").trim();

    // 2. PASS/NOT PASS: Validation criteria
    if (text.length === 0) {
        return ""; // Not pass: reject empty or invalid text
    }

    // 3. MODIFY/SUBSTITUTE: Clean, replace, or format
    // Example: strip non-numeric/currency characters and normalize decimal comma
    var modified = text.replace(/[^0-9.,]/g, "")
                       .replace(/,/g, ".");

    // 4. FINISH: Return target value
    return modified;
}"""

    data class ScriptProcessResult(
        val rawValue: String,
        val afterScript: String,
        val finalValue: String,
        val scriptError: String? = null,
        val regexError: String? = null,
    )

    /**
     * Executes the pipeline:
     * 1. Raw OCR string
     * 2. JavaScript script execution (if script is non-blank)
     * 3. Regular expression transformation / extraction (if regex is non-blank)
     */
    fun processValue(rawValue: String, script: String, regex: String): ScriptProcessResult {
        val trimmedRaw = rawValue.trim()

        val (afterScript, scriptErr) = if (script.isNotBlank()) {
            executeJs(script, trimmedRaw)
        } else {
            Pair(trimmedRaw, null)
        }

        val (finalVal, regexErr) = if (regex.isNotBlank()) {
            applyRegex(afterScript, regex)
        } else {
            Pair(afterScript, null)
        }

        return ScriptProcessResult(
            rawValue = trimmedRaw,
            afterScript = afterScript,
            finalValue = finalVal,
            scriptError = scriptErr,
            regexError = regexErr,
        )
    }

    /**
     * Evaluates a JavaScript snippet against [rawValue] using Mozilla Rhino.
     * Returns Pair(resultString, errorOrNull).
     */
    fun executeJs(script: String, rawValue: String): Pair<String, String?> {
        if (script.isBlank()) return Pair(rawValue, null)

        val cx = Context.enter()
        return try {
            // Optimization level -1 is required on Android ART/Dalvik as bytecode generation is unavailable
            cx.optimizationLevel = -1
            val scope: Scriptable = cx.initSafeStandardObjects()
            scope.put("value", scope, rawValue)

            val codeToRun = when {
                script.contains("function process") -> {
                    "$script\nprocess(value);"
                }
                script.contains("return ") -> {
                    "(function(value) {\n$script\n})(value);"
                }
                else -> {
                    "(function(value) {\nreturn ($script);\n})(value);"
                }
            }

            val evalResult = cx.evaluateString(scope, codeToRun, "ReceiptScript", 1, null)
            val resultStr = when (evalResult) {
                null, is Undefined -> ""
                else -> Context.toString(evalResult).trim()
            }
            Pair(resultStr, null)
        } catch (e: Exception) {
            Pair(rawValue, e.message ?: "Script execution error")
        } finally {
            Context.exit()
        }
    }

    /**
     * Applies a regular expression to [value].
     * Supports:
     * - Substitution syntax: `s/pattern/replacement/flags` or `s/pattern/replacement/`
     * - Capture groups: returns the first capture group match
     * - Substring match: returns matching substring
     * - Non-matching: returns empty string (filtered out)
     */
    fun applyRegex(value: String, regexPattern: String): Pair<String, String?> {
        val trimmedPattern = regexPattern.trim()
        if (trimmedPattern.isBlank()) return Pair(value, null)

        return try {
            // Check for substitution syntax s/pattern/replacement/[flags]
            val subMatch = Regex("^s/(.*?)/(.*?)(?:/([a-zA-Z]*))?$").matchEntire(trimmedPattern)
            if (subMatch != null) {
                val search = subMatch.groupValues[1]
                val replace = subMatch.groupValues[2]
                val flags = subMatch.groupValues.getOrNull(3).orEmpty()

                val options = mutableSetOf<RegexOption>()
                if (flags.contains("i", ignoreCase = true)) options.add(RegexOption.IGNORE_CASE)
                if (flags.contains("m", ignoreCase = true)) options.add(RegexOption.MULTILINE)

                val reg = Regex(search, options)
                val result = reg.replace(value, replace)
                return Pair(result, null)
            }

            // Normal matching or extraction
            val reg = Regex(trimmedPattern)
            val match = reg.find(value)
            if (match != null) {
                // If regex defined capture groups, return the first group
                if (match.groupValues.size > 1) {
                    Pair(match.groupValues[1], null)
                } else {
                    Pair(match.value, null)
                }
            } else {
                // When a regex filter does not match, return empty string to reject/filter out
                Pair("", null)
            }
        } catch (e: Exception) {
            Pair(value, e.message ?: "Invalid regex pattern")
        }
    }
}
