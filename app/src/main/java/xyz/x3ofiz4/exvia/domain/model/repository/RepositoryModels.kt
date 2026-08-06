package xyz.x3ofiz4.exvia.domain.model.repository

data class GitHubFile(
    val path: String,
    val sha: String,
    val text: String,
)

data class RepoFile(
    val name: String,
    val path: String,
    val sha: String,
)

class GitHubHttpException(
    val statusCode: Int,
    message: String,
) : Exception(message)
