package com.example.exp_tracker

data class ExpenseRow(
    val price: String,
    val ticker: String,
    val description: String,
    val tags: String,
    val date: String,
    val originalIndex: Int,
)

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
