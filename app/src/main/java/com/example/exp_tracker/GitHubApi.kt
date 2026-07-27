package com.example.exp_tracker

import android.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
<<<<<<< HEAD
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Small GitHub REST client using only Android/Java standard APIs.
 *
 * GitHub's Contents API creates real commits directly on the configured branch.
 */
class GitHubApi(private val token: String) {

    fun listExpenseFiles(): List<RepoFile> {
        val response = try {
            request(
                method = "GET",
                url = contentsUrl(RepoConfig.EXPENSE_FOLDER),
            )
        } catch (e: GitHubHttpException) {
            // Git has no empty directories. If Financial/ does not exist yet (or
            // disappears after deleting its last file), treat it as an empty list
            // so the user can create the next JSON file from the app.
            if (e.statusCode == 404) return emptyList() else throw e
        }
        val array = JSONArray(response)
        val result = mutableListOf<RepoFile>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            if (item.optString("type") == "file" && item.optString("name").endsWith(".json", true)) {
                result += RepoFile(
                    name = item.getString("name"),
                    path = item.getString("path"),
                    sha = item.getString("sha"),
                )
            }
        }
        return result.sortedBy { it.name.lowercase() }
    }

    fun fetchExpenses(path: String): List<ExpenseRow> {
        val file = getFile(path)
        return parseRows(file.text).sortedWith(
            compareByDescending<ExpenseRow> { dateSortKey(it.date) }
                .thenByDescending { it.originalIndex },
        )
    }

    fun appendExpense(path: String, priceText: String, ticker: String, description: String, tags: String): String {
        val normalizedPrice = normalizePrice(priceText)
        val cleanTicker = ticker.trim()
        val cleanDescription = description.trim()
        val cleanTags = normalizeTagsForStorage(tags)
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d/M/yy @ HH:mm"))
        val existing = getFile(path)
        val root = parseEditableRoot(existing.text)
        val items = expenseArray(root)

        val expense = JSONObject().apply {
            put(RepoConfig.DATE_KEY, date)
            // String storage is intentional: it preserves an explicit leading '+'.
            put(RepoConfig.PRICE_KEY, normalizedPrice)
            if (cleanTicker.isNotBlank()) put(RepoConfig.TICKER_KEY, cleanTicker)
            if (cleanDescription.isNotBlank()) put(RepoConfig.DESCRIPTION_KEY, cleanDescription)
            if (cleanTags.isNotBlank()) put(RepoConfig.TAGS_KEY, cleanTags)
        }
        items.put(expense)

        putFile(
            path = path,
            newText = serialize(root),
            previousSha = existing.sha,
            message = "Expense at $date: $normalizedPrice, ($cleanTicker) $cleanDescription",
=======
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * GitHub Contents API client. Writes create commits directly on the configured branch.
 */
class GitHubApi(
    private val token: String,
    private val settings: RepoSettings,
) {
    private data class EditableDocument(val root: Any, val items: JSONArray)

    fun listExpenseFiles(): List<RepoFile> {
        val response = try {
            request("GET", contentsUrl(settings.folder))
        } catch (e: GitHubHttpException) {
            if (e.statusCode == 404) return emptyList()
            throw e
        }
        val array = JSONArray(response)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name")
            if (item.optString("type") != "file" || !name.endsWith(".json", ignoreCase = true)) return@mapNotNull null
            RepoFile(name, item.optString("path"), item.optString("sha"))
        }.sortedByDescending { it.name.lowercase() }
    }

    fun fetchTable(path: String): TableData = parseTable(getFile(path).text)

    fun appendRow(path: String, inputValues: Map<String, String>): String {
        val existing = getFile(path)
        val document = parseEditableDocument(existing.text)
        val existingKeys = collectKeys(document.items)
        val allKeys = (existingKeys + inputValues.keys).distinct()
        val dateKey = settings.detectDateKey(allKeys)
        val values = LinkedHashMap(inputValues)
        val now = currentDateTime()
        if (dateKey != null && values[dateKey].isNullOrBlank()) values[dateKey] = now

        val item = JSONObject()
        for (key in allKeys) {
            val value = values[key]?.trim().orEmpty()
            if (value.isBlank()) continue
            putTypedValue(item, document.items, key, value, settings.detectTagsKey(allKeys))
        }
        document.items.put(item)

        val tableShape = tableShape(allKeys)
        val date = tableShape.dateKey?.let { values[it]?.trim() }.orEmpty().ifBlank { now }
        putFile(
            path = path,
            newText = serialize(document.root),
            previousSha = existing.sha,
            message = buildExpenseCommitMessage(date, values, tableShape, path),
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
        )
        return date
    }

<<<<<<< HEAD
    fun updateExpense(
        path: String,
        originalRow: ExpenseRow,
        date: String,
        priceText: String,
        ticker: String,
        description: String,
        tags: String,
    ) {
        require(date.isNotBlank()) { "DATE is required." }
        val normalizedPrice = normalizePrice(priceText)
        val existing = getFile(path)
        val root = parseEditableRoot(existing.text)
        val items = expenseArray(root)
        val rowIndex = locateRowIndex(items, originalRow)
        val item = items.optJSONObject(rowIndex)
            ?: throw JSONException("Selected expense is not a JSON object.")
        item.put(RepoConfig.DATE_KEY, date.trim())
        item.put(RepoConfig.PRICE_KEY, normalizedPrice)
        setOptional(item, RepoConfig.TICKER_KEY, ticker.trim())
        setOptional(item, RepoConfig.DESCRIPTION_KEY, description.trim())
        setOptional(item, RepoConfig.TAGS_KEY, normalizeTagsForStorage(tags))

        putFile(
            path = path,
            newText = serialize(root),
            previousSha = existing.sha,
            message = "Update expense at ${date.trim()}: $normalizedPrice, (${ticker.trim()}) ${description.trim()}",
        )
    }

    fun deleteExpense(path: String, row: ExpenseRow) {
        val existing = getFile(path)
        val root = parseEditableRoot(existing.text)
        val items = expenseArray(root)
        val rowIndex = locateRowIndex(items, row)
        items.remove(rowIndex)

        putFile(
            path = path,
            newText = serialize(root),
            previousSha = existing.sha,
            message = "Remove expense at ${row.date}: ${row.price}, (${row.ticker}) ${row.description}",
        )
=======
    fun updateRow(path: String, originalRow: DynamicRow, inputValues: Map<String, String>) {
        val existing = getFile(path)
        val document = parseEditableDocument(existing.text)
        val index = locateRowIndex(document.items, originalRow)
        val existingKeys = collectKeys(document.items)
        val allKeys = (existingKeys + inputValues.keys).distinct()
        val tagsKey = settings.detectTagsKey(allKeys)
        val replacement = JSONObject()
        for (key in allKeys) {
            val value = inputValues[key]?.trim().orEmpty()
            if (value.isBlank()) continue
            putTypedValue(replacement, document.items, key, value, tagsKey)
        }
        document.items.put(index, replacement)

        val shape = tableShape(allKeys)
        val date = shape.dateKey?.let { inputValues[it] }.orEmpty().ifBlank { currentDateTime() }
        putFile(
            path,
            serialize(document.root),
            existing.sha,
            "Update expense at $date",
        )
    }

    fun deleteRow(path: String, row: DynamicRow) {
        val existing = getFile(path)
        val document = parseEditableDocument(existing.text)
        val index = locateRowIndex(document.items, row)
        document.items.remove(index)
        val dateKey = settings.detectDateKey(row.values.keys.toList())
        val label = dateKey?.let { row.values[it] }.orEmpty().ifBlank { "row ${index + 1}" }
        putFile(path, serialize(document.root), existing.sha, "Remove expense at $label")
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
    }

    fun createExpenseFile(fileNameInput: String): RepoFile {
        val fileName = normalizeFileName(fileNameInput)
<<<<<<< HEAD
        val path = RepoConfig.pathFor(fileName)
=======
        val path = settings.pathFor(fileName)
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
        try {
            getFile(path)
            throw IllegalArgumentException("$fileName already exists.")
        } catch (e: GitHubHttpException) {
            if (e.statusCode != 404) throw e
        }
<<<<<<< HEAD

        putFile(
            path = path,
            newText = "[]\n",
            previousSha = null,
            message = "Create expense file: $fileName",
        )
=======
        putFile(path, "[]\n", null, "Create expense file: $fileName")
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
        return listExpenseFiles().first { it.name.equals(fileName, ignoreCase = true) }
    }

    fun deleteExpenseFile(file: RepoFile) {
        val latest = getFile(file.path)
        val body = JSONObject().apply {
            put("message", "Remove expense file: ${file.name}")
            put("sha", latest.sha)
<<<<<<< HEAD
            put("branch", RepoConfig.BRANCH)
        }
        request(
            method = "DELETE",
            url = contentsUrl(file.path, includeRef = false),
            body = body.toString(),
        )
    }

    private fun getFile(path: String): GitHubFile {
        val response = request(
            method = "GET",
            url = contentsUrl(path),
        )
        val json = JSONObject(response)
        val encoded = json.getString("content").replace("\n", "")
        val decoded = Base64.decode(encoded, Base64.DEFAULT).toString(Charsets.UTF_8)
        return GitHubFile(
            path = json.getString("path"),
            sha = json.getString("sha"),
            text = decoded,
        )
    }

    private fun putFile(path: String, newText: String, previousSha: String?, message: String) {
        val body = JSONObject().apply {
            put("message", message)
            put("content", Base64.encodeToString(newText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            put("branch", RepoConfig.BRANCH)
            if (previousSha != null) put("sha", previousSha)
        }

        request(
            method = "PUT",
            url = contentsUrl(path, includeRef = false),
            body = body.toString(),
        )
    }

    private fun parseRows(text: String): List<ExpenseRow> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val root = JSONTokener(trimmed).nextValue()
        val items = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray(RepoConfig.ARRAY_KEY) ?: return emptyList()
            else -> return emptyList()
        }

        val rows = mutableListOf<ExpenseRow>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            rows += ExpenseRow(
                date = firstValue(item, RepoConfig.DATE_KEY, "DATE", "timestamp", "TIMESTAMP"),
                price = firstValue(item, RepoConfig.PRICE_KEY, "PRICE", "amount", "AMOUNT"),
                ticker = firstValue(item, RepoConfig.TICKER_KEY, "TICKER"),
                description = firstValue(item, RepoConfig.DESCRIPTION_KEY, "DESCRIPTION", "desc", "DESC"),
                tags = readTags(item),
                originalIndex = i,
            )
        }
        return rows
    }

    private fun parseEditableRoot(text: String): Any {
        if (text.isBlank()) return JSONArray()
        return when (val root = JSONTokener(text).nextValue()) {
            is JSONArray -> root
            is JSONObject -> root
            else -> throw JSONException("Expense JSON must be an array or object.")
        }
    }

    private fun expenseArray(root: Any): JSONArray = when (root) {
        is JSONArray -> root
        is JSONObject -> root.optJSONArray(RepoConfig.ARRAY_KEY)
            ?: throw JSONException("JSON object has no '${RepoConfig.ARRAY_KEY}' array.")
        else -> error("Unsupported JSON root")
    }

    private fun serialize(root: Any): String = when (root) {
        is JSONArray -> root.toString(2) + "\n"
        is JSONObject -> root.toString(2) + "\n"
        else -> error("Unsupported JSON root")
    }

    private fun setOptional(item: JSONObject, key: String, value: String) {
        if (value.isBlank()) item.remove(key) else item.put(key, value)
    }

    private fun locateRowIndex(items: JSONArray, row: ExpenseRow): Int {
        fun matches(index: Int): Boolean {
            val item = items.optJSONObject(index) ?: return false
            return firstValue(item, RepoConfig.DATE_KEY, "DATE", "timestamp", "TIMESTAMP") == row.date &&
                firstValue(item, RepoConfig.PRICE_KEY, "PRICE", "amount", "AMOUNT") == row.price &&
                firstValue(item, RepoConfig.TICKER_KEY, "TICKER") == row.ticker &&
                firstValue(item, RepoConfig.DESCRIPTION_KEY, "DESCRIPTION", "desc", "DESC") == row.description &&
                readTags(item) == row.tags
        }

        if (row.originalIndex in 0 until items.length() && matches(row.originalIndex)) {
            return row.originalIndex
        }

        val matches = (0 until items.length()).filter(::matches)
        require(matches.size == 1) {
            "The expense changed remotely or is ambiguous. Refresh before editing/deleting it."
        }
        return matches.single()
    }

    private fun normalizePrice(input: String): String {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "PRICE is required." }
        require(!trimmed.startsWith("-")) { "PRICE may be positive or begin with '+', but cannot be negative." }
        val hasPlus = trimmed.startsWith("+")
        val numeric = if (hasPlus) trimmed.drop(1) else trimmed
        val value = try {
            BigDecimal(numeric)
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("PRICE must be a number, for example 12.50 or +12.50.")
        }
        require(value.signum() >= 0) { "PRICE cannot be negative." }
        val normalized = value.stripTrailingZeros().toPlainString()
        return if (hasPlus) "+$normalized" else normalized
    }

    private fun normalizeFileName(input: String): String {
        var name = input.trim()
        require(name.isNotEmpty()) { "File name is required." }
        require('/' !in name && '\\' !in name) { "Enter a file name only, not a path." }
        if (!name.endsWith(".json", ignoreCase = true)) name += ".json"
        require(name.length <= 120) { "File name is too long." }
        return name
    }

    private fun firstValue(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            if (obj.has(key) && !obj.isNull(key)) return obj.opt(key).toString()
        }
        return ""
    }

    private fun dateSortKey(value: String): Long {
        if (value.isBlank()) return Long.MIN_VALUE
        val trimmed = value.trim()

        // Current app format: 3/7/26 @ 14:05. Existing rows such as 3/7/26 are
        // treated as midnight so they remain sortable alongside timestamped rows.
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
                // Try the next supported format.
            }
        }

        // Backward compatibility with early versions of this app that wrote ISO dates.
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
                        Long.MIN_VALUE
                    }
                }
            }
        }
    }

    private fun readTags(item: JSONObject): String {
        val keys = listOf(RepoConfig.TAGS_KEY, "TAGS", "tag", "TAG")
        for (key in keys) {
            if (!item.has(key) || item.isNull(key)) continue
            val value = item.opt(key)
            return when (value) {
                is JSONArray -> (0 until value.length())
                    .mapNotNull { index -> value.opt(index)?.toString()?.trim()?.takeIf { it.isNotBlank() } }
                    .joinToString(", ")
                else -> tagsStorageToDisplay(value.toString())
            }
        }
        return ""
=======
            put("branch", settings.branch)
        }
        request("DELETE", contentsUrl(file.path, includeRef = false), body.toString())
    }

    private fun parseTable(text: String): TableData {
        val document = parseEditableDocument(text)
        val keys = collectKeys(document.items)
        val shape = tableShape(keys)
        val rows = mutableListOf<DynamicRow>()
        for (i in 0 until document.items.length()) {
            val item = document.items.optJSONObject(i) ?: continue
            val values = linkedMapOf<String, String>()
            for (key in keys) {
                values[key] = displayValue(item.opt(key), key == shape.tagsKey)
            }
            rows += DynamicRow(LinkedHashMap(values), i, item.toString())
        }
        val sorted = if (shape.dateKey != null) {
            rows.sortedWith(compareByDescending<DynamicRow> {
                Statistics.parseDate(it.values[shape.dateKey].orEmpty()) ?: Long.MIN_VALUE
            }.thenByDescending { it.originalIndex })
        } else rows
        return shape.copy(rows = sorted)
    }

    private fun tableShape(keys: List<String>): TableData = TableData(
        keys = keys,
        rows = emptyList(),
        dateKey = settings.detectDateKey(keys),
        moneyKey = settings.detectMoneyKey(keys),
        tickerKey = settings.detectTickerKey(keys),
        tagsKey = settings.detectTagsKey(keys),
    )

    private fun parseEditableDocument(text: String): EditableDocument {
        if (text.isBlank()) {
            val array = JSONArray()
            return EditableDocument(array, array)
        }
        val root = JSONTokener(text.trim()).nextValue()
        return when (root) {
            is JSONArray -> EditableDocument(root, root)
            is JSONObject -> {
                val configured = if (settings.arrayKey.isNotBlank()) root.optJSONArray(settings.arrayKey) else null
                val array = configured ?: firstArray(root)
                    ?: throw JSONException("JSON object has no array to use as its table.")
                EditableDocument(root, array)
            }
            else -> throw JSONException("JSON root must be an array or an object containing an array.")
        }
    }

    private fun firstArray(root: JSONObject): JSONArray? {
        val iterator = root.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val value = root.opt(key)
            if (value is JSONArray) return value
        }
        return null
    }

    private fun collectKeys(items: JSONArray): List<String> {
        val keys = linkedSetOf<String>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val iterator = item.keys()
            while (iterator.hasNext()) keys += iterator.next()
        }
        return keys.toList()
    }

    private fun displayValue(value: Any?, isTags: Boolean): String {
        if (value == null || value === JSONObject.NULL) return ""
        if (isTags) {
            return when (value) {
                is JSONArray -> (0 until value.length()).mapNotNull { index ->
                    value.opt(index)?.toString()?.trim()?.takeIf { it.isNotBlank() }
                }.joinToString(", ")
                else -> tagsStorageToDisplay(value.toString())
            }
        }
        return when (value) {
            is JSONArray, is JSONObject -> value.toString()
            else -> value.toString()
        }
    }

    private fun putTypedValue(item: JSONObject, items: JSONArray, key: String, input: String, tagsKey: String?) {
        if (key == tagsKey) {
            item.put(key, normalizeTagsForStorage(input))
            return
        }
        when (inferColumnType(items, key)) {
            "number" -> {
                if (input.startsWith("+")) item.put(key, input)
                else item.put(key, input.toBigDecimalOrNull() ?: input)
            }
            "boolean" -> item.put(key, input.equals("true", ignoreCase = true))
            else -> item.put(key, input)
        }
    }

    private fun inferColumnType(items: JSONArray, key: String): String {
        var numbers = 0
        var booleans = 0
        var strings = 0
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            if (!obj.has(key) || obj.isNull(key)) continue
            when (obj.opt(key)) {
                is Number -> numbers++
                is Boolean -> booleans++
                else -> strings++
            }
        }
        return when {
            numbers > 0 && strings == 0 && booleans == 0 -> "number"
            booleans > 0 && numbers == 0 && strings == 0 -> "boolean"
            else -> "string"
        }
    }

    private fun locateRowIndex(items: JSONArray, row: DynamicRow): Int {
        fun matches(index: Int): Boolean = items.optJSONObject(index)?.toString() == row.originalJson
        if (row.originalIndex in 0 until items.length() && matches(row.originalIndex)) return row.originalIndex
        val matches = (0 until items.length()).filter(::matches)
        require(matches.size == 1) { "The row changed remotely or is ambiguous. Refresh before editing/deleting it." }
        return matches.single()
    }

    private fun buildExpenseCommitMessage(
        date: String,
        values: Map<String, String>,
        shape: TableData,
        path: String,
    ): String {
        val money = shape.moneyKey?.let { values[it]?.trim() }.orEmpty()
        val ticker = shape.tickerKey?.let { values[it]?.trim() }.orEmpty()
        val descriptionKey = values.keys.firstOrNull {
            it.equals("description", true) || it.equals("desc", true) || it.equals("name", true)
        }
        val description = descriptionKey?.let { values[it]?.trim() }.orEmpty()
        return if (shape.moneyKey != null || shape.tickerKey != null || description.isNotBlank()) {
            "Expense at $date: $money, ($ticker) $description"
        } else {
            "Amend ${path.substringAfterLast('/')} at $date"
        }
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
    }

    private fun tagsStorageToDisplay(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed.substring(1, trimmed.length - 1)
                .split(',')
                .map { it.trim().trim('\'', '"') }
                .filter { it.isNotBlank() }
                .joinToString(", ")
        }
        return trimmed
    }

    private fun normalizeTagsForStorage(input: String): String {
<<<<<<< HEAD
        val tags = input.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
=======
        val tags = input.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
        if (tags.isEmpty()) return ""
        val safe = tags.map { it.replace("'", "\\'") }
        return safe.joinToString(prefix = "['", separator = "', '", postfix = "']")
    }

<<<<<<< HEAD
    private fun contentsUrl(path: String, includeRef: Boolean = true): String {
        val base = "https://api.github.com/repos/${encodeSegment(RepoConfig.OWNER)}/${encodeSegment(RepoConfig.REPO)}/contents"
        val withPath = if (path.isBlank()) base else "$base/${encodePath(path)}"
        return if (includeRef) "$withPath?ref=${encodeSegment(RepoConfig.BRANCH)}" else withPath
    }

    private fun encodePath(path: String): String = path
        .split('/')
        .filter { it.isNotBlank() }
        .joinToString("/") { encodeSegment(it) }

    private fun encodeSegment(value: String): String = URLEncoder.encode(
        value,
        StandardCharsets.UTF_8.name(),
    ).replace("+", "%20")
=======
    private fun normalizeFileName(input: String): String {
        var name = input.trim()
        require(name.isNotEmpty()) { "File name is required." }
        require('/' !in name && '\\' !in name) { "Enter a file name only, not a path." }
        if (!name.endsWith(".json", ignoreCase = true)) name += ".json"
        require(name.length <= 120) { "File name is too long." }
        return name
    }

    private fun currentDateTime(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d/M/yy @ HH:mm"))

    private fun getFile(path: String): GitHubFile {
        val response = request("GET", contentsUrl(path))
        val json = JSONObject(response)
        val encoded = json.getString("content").replace("\n", "")
        val decoded = Base64.decode(encoded, Base64.DEFAULT).toString(Charsets.UTF_8)
        return GitHubFile(json.getString("path"), json.getString("sha"), decoded)
    }

    private fun putFile(path: String, newText: String, previousSha: String?, message: String) {
        val body = JSONObject().apply {
            put("message", message)
            put("content", Base64.encodeToString(newText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            put("branch", settings.branch)
            if (previousSha != null) put("sha", previousSha)
        }
        request("PUT", contentsUrl(path, includeRef = false), body.toString())
    }

    private fun serialize(root: Any): String = when (root) {
        is JSONArray -> root.toString(2) + "\n"
        is JSONObject -> root.toString(2) + "\n"
        else -> error("Unsupported JSON root")
    }

    private fun contentsUrl(path: String, includeRef: Boolean = true): String {
        val base = "https://api.github.com/repos/${encodeSegment(settings.owner)}/${encodeSegment(settings.repo)}/contents"
        val withPath = if (path.isBlank()) base else "$base/${encodePath(path)}"
        return if (includeRef) "$withPath?ref=${encodeSegment(settings.branch)}" else withPath
    }

    private fun encodePath(path: String): String = path.split('/').filter { it.isNotBlank() }.joinToString("/") { encodeSegment(it) }

    private fun encodeSegment(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)

    private fun request(method: String, url: String, body: String? = null): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "exp_tracker-android")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
<<<<<<< HEAD

        try {
            if (body != null) {
                connection.outputStream.use { out ->
                    out.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                val apiMessage = try {
                    JSONObject(response).optString("message")
                } catch (_: Exception) {
                    ""
                }
                val detail = if (apiMessage.isNotBlank()) apiMessage else "GitHub returned HTTP $status"
                throw GitHubHttpException(status, detail)
=======
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val apiMessage = try { JSONObject(response).optString("message") } catch (_: Exception) { "" }
                throw GitHubHttpException(status, apiMessage.ifBlank { "GitHub returned HTTP $status" })
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
