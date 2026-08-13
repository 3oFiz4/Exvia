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


data class RepoCommit(
    val sha: String,
    val shortSha: String,
    val message: String,
    val author: String,
    val date: String,
    val htmlUrl: String,
)

data class CommitPage(
    val page: Int,
    val perPage: Int,
    val commits: List<RepoCommit>,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)
