package xyz.x3ofiz4.exvia.data.local
import xyz.x3ofiz4.exvia.domain.model.repository.GitHubFile
import xyz.x3ofiz4.exvia.domain.model.repository.RepoFile
import xyz.x3ofiz4.exvia.domain.model.settings.RepoSettings


import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Persistent repository/file cache used by Exvia.
 *
 * Only files that have actually been opened are cached locally. The repository file
 * list is cached separately, so a later application launch can render the Files tab
 * and reopen any previously selected file without contacting GitHub. A manual Re-sync
 * refreshes the list and selected file from GitHub. Successful writes replace the
 * affected cache entry immediately.
 */
class ExviaFileCache(context: Context) {
    private val root = File(context.filesDir, "exvia_repo_cache").apply { mkdirs() }

    fun loadFiles(settings: RepoSettings): List<RepoFile>? {
        val file = File(repoDir(settings), "index.json")
        if (!file.isFile) return null
        return try {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("files") ?: return emptyList()
            (0 until arr.length()).mapNotNull { index ->
                val obj = arr.optJSONObject(index) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                val path = obj.optString("path").trim()
                if (name.isBlank() || path.isBlank()) return@mapNotNull null
                RepoFile(name, path, obj.optString("sha"))
            }
        } catch (_: Exception) {
            null
        }
    }

    fun saveFiles(settings: RepoSettings, files: List<RepoFile>) {
        val directory = repoDir(settings)
        val payload = JSONObject().apply {
            put("repository", repositoryIdentity(settings))
            put("files", JSONArray().apply {
                files.forEach { file ->
                    put(JSONObject().apply {
                        put("name", file.name)
                        put("path", file.path)
                        put("sha", file.sha)
                    })
                }
            })
        }
        atomicWrite(File(directory, "index.json"), payload.toString(2))

        val validPaths = files.map { it.path }.toSet()
        File(directory, "files").listFiles()?.forEach { cached ->
            val metadata = try { JSONObject(cached.readText()) } catch (_: Exception) { null }
            val path = metadata?.optString("path").orEmpty()
            if (path.isNotBlank() && path !in validPaths) cached.delete()
        }
    }

    fun loadFile(settings: RepoSettings, path: String): GitHubFile? {
        val file = cacheFile(settings, path)
        if (!file.isFile) return null
        return try {
            val obj = JSONObject(file.readText())
            if (obj.optString("path") != path) return null
            GitHubFile(path, obj.optString("sha"), obj.optString("text"))
        } catch (_: Exception) {
            null
        }
    }

    fun saveFile(settings: RepoSettings, file: GitHubFile) {
        val payload = JSONObject().apply {
            put("path", file.path)
            put("sha", file.sha)
            put("text", file.text)
            put("repository", repositoryIdentity(settings))
            put("cached_at", System.currentTimeMillis())
        }
        atomicWrite(cacheFile(settings, file.path), payload.toString())
    }

    fun removeFile(settings: RepoSettings, path: String) {
        cacheFile(settings, path).delete()
    }

    fun clearRepository(settings: RepoSettings) {
        repoDir(settings).deleteRecursively()
    }

    private fun repoDir(settings: RepoSettings): File = File(root, sha256(repositoryIdentity(settings))).apply {
        mkdirs()
        File(this, "files").mkdirs()
    }

    private fun cacheFile(settings: RepoSettings, path: String): File =
        File(File(repoDir(settings), "files"), "${sha256(path)}.json")

    private fun repositoryIdentity(settings: RepoSettings): String = listOf(
        settings.owner.trim().lowercase(),
        settings.repo.trim().lowercase(),
        settings.branch.trim(),
        settings.folder.trim('/'),
    ).joinToString("|")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun atomicWrite(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(target)) {
            target.writeText(text)
            temporary.delete()
        }
    }
}
