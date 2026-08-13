package xyz.x3ofiz4.exvia.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import xyz.x3ofiz4.exvia.domain.model.settings.RepoSettings
import java.io.File
import java.security.MessageDigest

/**
 * Persistent local staging area used when automatic amend is disabled.
 *
 * Staged table rows are deliberately stored outside the GitHub response cache. This keeps
 * the last remote snapshot and the local working copy separate, which makes Pull/Re-sync,
 * Commit/Push, app restarts, and undo/redo deterministic.
 */
class LocalStagingStore(context: Context) {
    data class StagedTable(
        val path: String,
        val baseSha: String,
        val rows: List<Map<String, String>>,
        val messages: List<String>,
        val updatedAt: Long,
    )

    private val root = File(context.filesDir, "exvia_git_stage").apply { mkdirs() }

    fun load(settings: RepoSettings, path: String): StagedTable? {
        val file = stageFile(settings, path)
        if (!file.isFile) return null
        return try {
            val obj = JSONObject(file.readText())
            if (obj.optString("path") != path) return null
            val rowsArray = obj.optJSONArray("rows") ?: JSONArray()
            val rows = (0 until rowsArray.length()).mapNotNull { index ->
                val row = rowsArray.optJSONObject(index) ?: return@mapNotNull null
                val values = linkedMapOf<String, String>()
                val keys = row.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (!row.isNull(key)) values[key] = row.opt(key)?.toString().orEmpty()
                }
                values
            }
            val messagesArray = obj.optJSONArray("messages") ?: JSONArray()
            val messages = (0 until messagesArray.length()).mapNotNull { index ->
                messagesArray.optString(index).takeIf { it.isNotBlank() }
            }
            StagedTable(
                path = path,
                baseSha = obj.optString("baseSha"),
                rows = rows,
                messages = messages,
                updatedAt = obj.optLong("updatedAt", 0L),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(settings: RepoSettings, staged: StagedTable) {
        val payload = JSONObject().apply {
            put("path", staged.path)
            put("baseSha", staged.baseSha)
            put("updatedAt", staged.updatedAt)
            put("messages", JSONArray().apply { staged.messages.forEach(::put) })
            put("rows", JSONArray().apply {
                staged.rows.forEach { values ->
                    put(JSONObject().apply { values.forEach { (key, value) -> put(key, value) } })
                }
            })
        }
        atomicWrite(stageFile(settings, staged.path), payload.toString())
    }

    fun has(settings: RepoSettings, path: String?): Boolean =
        path != null && stageFile(settings, path).isFile

    fun hasAny(settings: RepoSettings): Boolean =
        repoDir(settings).listFiles()?.any { it.isFile && it.name.endsWith(".json") } == true

    fun clear(settings: RepoSettings, path: String) {
        stageFile(settings, path).delete()
    }

    fun clearRepository(settings: RepoSettings) {
        repoDir(settings).deleteRecursively()
    }

    private fun repoDir(settings: RepoSettings): File = File(root, sha256(repositoryIdentity(settings))).apply { mkdirs() }

    private fun stageFile(settings: RepoSettings, path: String): File =
        File(repoDir(settings), "${sha256(path)}.json")

    private fun repositoryIdentity(settings: RepoSettings): String = listOf(
        settings.owner.trim().lowercase(),
        settings.repo.trim().lowercase(),
        settings.branch.trim(),
    ).joinToString("|")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun atomicWrite(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.writeText(text)
            temp.delete()
        }
    }
}
