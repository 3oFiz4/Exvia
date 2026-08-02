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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * GitHub Contents API client. Writes create commits directly on the configured branch.
 */
class GitHubApi(
    private val token: String,
    private val settings: RepoSettings,
) {
    companion object {
        const val CONFIG_FILE_NAME = ".exvia-config.json"
        const val CONFIG_PATH = "Financial/.exvia-config.json"
    }
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
            if (name.equals(CONFIG_FILE_NAME, ignoreCase = true) || name.startsWith(".")) return@mapNotNull null
            RepoFile(name, item.optString("path"), item.optString("sha"))
        }.sortedByDescending { it.name.lowercase() }
    }


    /** Creates or replaces a UTF-8 text file through the GitHub Contents API. */
    fun upsertTextFile(path: String, text: String, message: String) {
        val sha = try {
            getFile(path).sha
        } catch (e: GitHubHttpException) {
            if (e.statusCode == 404) null else throw e
        }
        putFile(path, text, sha, message)
    }

    /** Creates an issue in the requested repository using the current PAT. */
    fun createIssue(targetOwner: String, targetRepo: String, title: String, bodyText: String): String {
        require(targetOwner.isNotBlank()) { "Issue owner is required." }
        require(targetRepo.isNotBlank()) { "Issue repository is required." }
        require(title.isNotBlank()) { "Issue title is required." }
        val url = "https://api.github.com/repos/${encodeSegment(targetOwner)}/${encodeSegment(targetRepo)}/issues"
        val response = JSONObject(request("POST", url, JSONObject().apply {
            put("title", title)
            put("body", bodyText)
        }.toString()))
        return response.optString("html_url")
    }

    fun fetchTable(path: String): TableData = parseTable(getFile(path).text)

    fun fetchTableFile(path: String): Pair<GitHubFile, TableData> {
        val file = getFile(path)
        return file to parseTable(file.text)
    }

    fun parseCachedTable(text: String): TableData = parseTable(text)

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
        )
        return date
    }

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
    }

    fun createExpenseFile(fileNameInput: String): RepoFile {
        val fileName = normalizeFileName(fileNameInput)
        val path = settings.pathFor(fileName)
        try {
            getFile(path)
            throw IllegalArgumentException("$fileName already exists.")
        } catch (e: GitHubHttpException) {
            if (e.statusCode != 404) throw e
        }
        putFile(path, "[]\n", null, "Create expense file: $fileName")
        return listExpenseFiles().first { it.name.equals(fileName, ignoreCase = true) }
    }

    fun deleteExpenseFile(file: RepoFile) {
        val latest = getFile(file.path)
        val body = JSONObject().apply {
            put("message", "Remove expense file: ${file.name}")
            put("sha", latest.sha)
            put("branch", settings.branch)
        }
        request("DELETE", contentsUrl(file.path, includeRef = false), body.toString())
    }

    /**
     * Creates a private repository for the authenticated GitHub user, initializes the
     * requested branch, then creates the configured JSON file with an empty array.
     * The supplied username is verified against GET /user to avoid creating the repo
     * under an unexpected account.
     */
    fun createAndInitializeRepository(
        username: String,
        repo: String,
        branch: String,
        folder: String,
        defaultFile: String,
    ) {
        val login = JSONObject(request("GET", "https://api.github.com/user")).optString("login")
        require(login.equals(username.trim(), ignoreCase = true)) {
            "PAT belongs to '$login', not '${username.trim()}'."
        }
        val repoName = repo.trim()
        require(repoName.isNotBlank()) { "Repository name is required." }
        val desiredBranch = branch.trim().ifBlank { "main" }
        val created = JSONObject(request(
            "POST",
            "https://api.github.com/user/repos",
            JSONObject().apply {
                put("name", repoName)
                put("private", true)
                put("auto_init", true)
                put("description", "Expense data for Exvia")
            }.toString(),
        ))
        val defaultBranch = created.optString("default_branch").ifBlank { "main" }

        if (!desiredBranch.equals(defaultBranch, ignoreCase = false)) {
            var sha: String? = null
            var lastError: Exception? = null
            for (attempt in 0 until 6) {
                try {
                    val ref = JSONObject(request(
                        "GET",
                        "https://api.github.com/repos/${encodeSegment(login)}/${encodeSegment(repoName)}/git/ref/heads/${encodeSegment(defaultBranch)}",
                    ))
                    sha = ref.getJSONObject("object").getString("sha")
                    break
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < 5) Thread.sleep(450L * (attempt + 1))
                }
            }
            val baseSha = sha ?: throw (lastError ?: IllegalStateException("Could not initialize default branch."))
            request(
                "POST",
                "https://api.github.com/repos/${encodeSegment(login)}/${encodeSegment(repoName)}/git/refs",
                JSONObject().apply {
                    put("ref", "refs/heads/$desiredBranch")
                    put("sha", baseSha)
                }.toString(),
            )
            request(
                "PATCH",
                "https://api.github.com/repos/${encodeSegment(login)}/${encodeSegment(repoName)}",
                JSONObject().apply { put("default_branch", desiredBranch) }.toString(),
            )
        }

        var fileName = defaultFile.trim().ifBlank { "expenses.json" }
        if (!fileName.endsWith(".json", ignoreCase = true)) fileName += ".json"
        val path = listOf(folder.trim().trim('/'), fileName).filter { it.isNotBlank() }.joinToString("/")
        val body = JSONObject().apply {
            put("message", "Initialize Exvia data")
            put("content", Base64.encodeToString("[]\n".toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            put("branch", desiredBranch)
        }
        request(
            "PUT",
            "https://api.github.com/repos/${encodeSegment(login)}/${encodeSegment(repoName)}/contents/${encodePath(path)}",
            body.toString(),
        )
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
        val tags = input.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (tags.isEmpty()) return ""
        val safe = tags.map { it.replace("'", "\\'") }
        return safe.joinToString(prefix = "['", separator = "', '", postfix = "']")
    }

    private fun normalizeFileName(input: String): String {
        var name = input.trim()
        require(name.isNotEmpty()) { "File name is required." }
        require('/' !in name && '\\' !in name) { "Enter a file name only, not a path." }
        if (!name.endsWith(".json", ignoreCase = true)) name += ".json"
        require(!name.equals(CONFIG_FILE_NAME, ignoreCase = true) && !name.startsWith(".")) {
            "Hidden Exvia configuration file names are reserved."
        }
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

    private fun request(method: String, url: String, body: String? = null): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            setRequestProperty("User-Agent", "Exvia-Android")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val apiMessage = try { JSONObject(response).optString("message") } catch (_: Exception) { "" }
                throw GitHubHttpException(status, apiMessage.ifBlank { "GitHub returned HTTP $status" })
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
