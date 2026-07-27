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
        )
        return date
    }

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
    }

    fun createExpenseFile(fileNameInput: String): RepoFile {
        val fileName = normalizeFileName(fileNameInput)
        val path = RepoConfig.pathFor(fileName)
        try {
            getFile(path)
            throw IllegalArgumentException("$fileName already exists.")
        } catch (e: GitHubHttpException) {
            if (e.statusCode != 404) throw e
        }

        putFile(
            path = path,
            newText = "[]\n",
            previousSha = null,
            message = "Create expense file: $fileName",
        )
        return listExpenseFiles().first { it.name.equals(fileName, ignoreCase = true) }
    }

    fun deleteExpenseFile(file: RepoFile) {
        val latest = getFile(file.path)
        val body = JSONObject().apply {
            put("message", "Remove expense file: ${file.name}")
            put("sha", latest.sha)
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
        val tags = input.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (tags.isEmpty()) return ""
        val safe = tags.map { it.replace("'", "\\'") }
        return safe.joinToString(prefix = "['", separator = "', '", postfix = "']")
    }

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
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
