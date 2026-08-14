package xyz.x3ofiz4.exvia.domain.service

import java.net.URI

enum class AssistantApiProtocol {
    RESPONSES,
    CHAT_COMPLETIONS,
}

data class AssistantEndpoint(
    val provider: String,
    val baseUrl: String,
    val requestUrl: String,
    val protocol: AssistantApiProtocol,
)

/**
 * Converts provider pages, API roots, and complete endpoint URLs into the base URL
 * format expected by openai-kotlin. Known providers are pinned to the API shape they
 * officially support; unknown OpenAI-compatible providers use Chat Completions.
 */
object AssistantEndpointResolver {
    fun resolve(value: String): AssistantEndpoint {
        val entered = value.trim()
        require(entered.isNotBlank()) { "BASE_URL is required." }
        val uri = runCatching { URI(entered) }
            .getOrElse { throw IllegalArgumentException("BASE_URL is not a valid URL.") }
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "BASE_URL must be a valid HTTPS URL."
        }

        val host = uri.host.lowercase()
        return when (host) {
            "api.openai.com" -> knownEndpoint(
                uri = uri,
                provider = "OpenAI",
                path = "/v1/",
                protocol = AssistantApiProtocol.RESPONSES,
            )
            "openrouter.ai" -> knownEndpoint(
                uri = uri,
                provider = "OpenRouter",
                path = "/api/v1/",
                protocol = AssistantApiProtocol.RESPONSES,
            )
            "generativelanguage.googleapis.com" -> knownEndpoint(
                uri = uri,
                provider = "Gemini",
                path = "/v1beta/openai/",
                protocol = AssistantApiProtocol.CHAT_COMPLETIONS,
            )
            else -> genericEndpoint(uri)
        }
    }

    fun normalize(value: String): String = resolve(value).baseUrl

    private fun knownEndpoint(
        uri: URI,
        provider: String,
        path: String,
        protocol: AssistantApiProtocol,
    ): AssistantEndpoint {
        val base = rebuild(uri, path)
        return AssistantEndpoint(
            provider = provider,
            baseUrl = base,
            requestUrl = base + protocol.path,
            protocol = protocol,
        )
    }

    private fun genericEndpoint(uri: URI): AssistantEndpoint {
        var path = uri.path.orEmpty().ifBlank { "/" }.trimEnd('/')
        val protocol = when {
            path.endsWith("/responses", ignoreCase = true) -> {
                path = path.dropLast("/responses".length)
                AssistantApiProtocol.RESPONSES
            }
            path.endsWith("/chat/completions", ignoreCase = true) -> {
                path = path.dropLast("/chat/completions".length)
                AssistantApiProtocol.CHAT_COMPLETIONS
            }
            path.endsWith("/chat", ignoreCase = true) -> {
                path = path.dropLast("/chat".length)
                AssistantApiProtocol.CHAT_COMPLETIONS
            }
            else -> AssistantApiProtocol.CHAT_COMPLETIONS
        }
        val base = rebuild(uri, "${path.ifBlank { "" }}/")
        return AssistantEndpoint(
            provider = uri.host,
            baseUrl = base,
            requestUrl = base + protocol.path,
            protocol = protocol,
        )
    }

    private fun rebuild(uri: URI, path: String): String = URI(
        uri.scheme,
        uri.userInfo,
        uri.host,
        uri.port,
        if (path.startsWith('/')) path else "/$path",
        null,
        null,
    ).toASCIIString()

    private val AssistantApiProtocol.path: String
        get() = when (this) {
            AssistantApiProtocol.RESPONSES -> "responses"
            AssistantApiProtocol.CHAT_COMPLETIONS -> "chat/completions"
        }
}
