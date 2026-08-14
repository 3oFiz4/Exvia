package xyz.x3ofiz4.exvia.data.remote

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ContentPart
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.chat.Tool
import com.aallam.openai.api.chat.ToolCall
import com.aallam.openai.api.chat.ToolChoice
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.api.response.ResponseInput
import com.aallam.openai.api.response.ResponseRequest
import com.aallam.openai.api.response.ResponseTool
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import xyz.x3ofiz4.exvia.domain.service.AssistantApiProtocol
import xyz.x3ofiz4.exvia.domain.service.AssistantEndpoint
import xyz.x3ofiz4.exvia.domain.service.AssistantEndpointResolver
import java.util.Base64

data class AssistantMessage(val role: String, val text: String)

data class AssistantAttachment(
    val name: String,
    val mimeType: String,
    val dataUrl: String,
)

data class AssistantReply(
    val text: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val reasoningSummary: String = "",
    val reasoningTokens: Int = 0,
)

fun interface AssistantToolExecutor {
    fun execute(name: String, argumentsJson: String): String
}

/** openai-kotlin adapter for Responses and Chat Completions, with a bounded application-tool loop. */
class AssistantApi {
    fun complete(
        baseUrl: String,
        apiKey: String,
        model: String,
        history: List<AssistantMessage>,
        message: String,
        attachments: List<AssistantAttachment>,
        toolExecutor: AssistantToolExecutor,
    ): AssistantReply = runBlocking {
        val endpoint = AssistantEndpointResolver.resolve(baseUrl)
        val client = createClient(endpoint, apiKey)
        try {
            when (endpoint.protocol) {
                AssistantApiProtocol.RESPONSES -> completeResponses(
                    client, model, history, message, attachments, toolExecutor,
                )
                AssistantApiProtocol.CHAT_COMPLETIONS -> completeChat(
                    client, model, history, message, attachments, toolExecutor,
                )
            }
        } catch (error: Throwable) {
            throw IllegalStateException(friendlyFailure(endpoint, error), error)
        } finally {
            client.close()
        }
    }

    /** Generate one JSON configuration artifact without exposing application tools. */
    fun generateArtifact(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
    ): AssistantReply = runBlocking {
        val endpoint = AssistantEndpointResolver.resolve(baseUrl)
        val client = createClient(endpoint, apiKey)
        try {
            when (endpoint.protocol) {
                AssistantApiProtocol.RESPONSES -> {
                    val response = client.response(
                        ResponseRequest(
                            model = ModelId(model),
                            input = ResponseInput(prompt),
                            instructions = GENERATION_INSTRUCTIONS,
                            store = false,
                        ),
                    )
                    AssistantReply(
                        text = response.outputText
                            ?: response.output.flatMap { it.content.orEmpty() }.mapNotNull { it.text }.joinToString("\n"),
                        inputTokens = response.usage?.inputTokens ?: 0,
                        outputTokens = response.usage?.outputTokens ?: 0,
                        totalTokens = response.usage?.totalTokens ?: 0,
                        reasoningSummary = responseReasoningSummary(response.output.mapNotNull { it.summary }),
                        reasoningTokens = response.usage?.outputTokensDetails?.reasoningTokens ?: 0,
                    )
                }
                AssistantApiProtocol.CHAT_COMPLETIONS -> {
                    val completion = client.chatCompletion(
                        ChatCompletionRequest(
                            model = ModelId(model),
                            messages = listOf(
                                ChatMessage.System(GENERATION_INSTRUCTIONS),
                                ChatMessage.User(prompt),
                            ),
                        ),
                    )
                    AssistantReply(
                        text = completion.choices.firstOrNull()?.message?.content.orEmpty(),
                        inputTokens = completion.usage?.promptTokens ?: 0,
                        outputTokens = completion.usage?.completionTokens ?: 0,
                        totalTokens = completion.usage?.totalTokens ?: 0,
                    )
                }
            }
        } catch (error: Throwable) {
            throw IllegalStateException(friendlyFailure(endpoint, error), error)
        } finally {
            client.close()
        }
    }

    private fun createClient(endpoint: AssistantEndpoint, apiKey: String): OpenAI = OpenAI(
        OpenAIConfig(
            token = apiKey,
            host = OpenAIHost(baseUrl = endpoint.baseUrl),
            // Avoid Ktor's JVM service-loader lookup on Android. Without an explicit
            // engine some devices fail at HttpClientJvmKt before a request is sent.
            engine = OkHttp.create(),
        ),
    )

    private suspend fun completeResponses(
        client: OpenAI,
        model: String,
        history: List<AssistantMessage>,
        message: String,
        attachments: List<AssistantAttachment>,
        toolExecutor: AssistantToolExecutor,
    ): AssistantReply {
        val transcript = buildResponsesTranscript(history, message, attachments).toMutableList()
        var inputTokens = 0
        var outputTokens = 0
        var totalTokens = 0
        var reasoningTokens = 0
        val reasoningSummaries = mutableListOf<String>()
        repeat(MAX_TOOL_ROUNDS) {
            val response = client.response(
                ResponseRequest(
                    model = ModelId(model),
                    input = ResponseInput(JsonArray(transcript)),
                    instructions = SYSTEM_INSTRUCTIONS,
                    tools = RESPONSE_TOOLS,
                    parallelToolCalls = false,
                    store = false,
                ),
            )
            inputTokens += response.usage?.inputTokens ?: 0
            outputTokens += response.usage?.outputTokens ?: 0
            totalTokens += response.usage?.totalTokens ?: 0
            reasoningTokens += response.usage?.outputTokensDetails?.reasoningTokens ?: 0
            responseReasoningSummary(response.output.mapNotNull { it.summary })
                .takeIf { it.isNotBlank() }
                ?.let(reasoningSummaries::add)

            val calls = response.output.filter { it.type == "function_call" && it.callId != null && it.name != null }
            if (calls.isEmpty()) {
                val output = response.outputText
                    ?: response.output.flatMap { it.content.orEmpty() }.mapNotNull { it.text }.joinToString("\n")
                return AssistantReply(
                    text = output.ifBlank { "The assistant returned no text." },
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    totalTokens = totalTokens,
                    reasoningSummary = reasoningSummaries.distinct().joinToString("\n\n"),
                    reasoningTokens = reasoningTokens,
                )
            }

            calls.forEach { call ->
                transcript += buildJsonObject {
                    put("type", "function_call")
                    put("name", call.name!!)
                    put("arguments", call.arguments ?: "{}")
                    put("call_id", call.callId!!)
                }
                val result = executeTool(toolExecutor, call.name!!, call.arguments ?: "{}")
                transcript += buildJsonObject {
                    put("type", "function_call_output")
                    put("call_id", call.callId!!)
                    put("output", result)
                }
            }
        }
        return stoppedReply(inputTokens, outputTokens, totalTokens, reasoningSummaries.joinToString("\n\n"), reasoningTokens)
    }

    private suspend fun completeChat(
        client: OpenAI,
        model: String,
        history: List<AssistantMessage>,
        message: String,
        attachments: List<AssistantAttachment>,
        toolExecutor: AssistantToolExecutor,
    ): AssistantReply {
        val transcript = buildChatTranscript(history, message, attachments).toMutableList()
        var inputTokens = 0
        var outputTokens = 0
        var totalTokens = 0
        repeat(MAX_TOOL_ROUNDS) {
            val completion = client.chatCompletion(
                ChatCompletionRequest(
                    model = ModelId(model),
                    messages = transcript,
                    tools = CHAT_TOOLS,
                    toolChoice = ToolChoice.Auto,
                ),
            )
            inputTokens += completion.usage?.promptTokens ?: 0
            outputTokens += completion.usage?.completionTokens ?: 0
            totalTokens += completion.usage?.totalTokens ?: 0
            val assistantMessage = completion.choices.firstOrNull()?.message
                ?: return AssistantReply("The assistant returned no choices.", inputTokens, outputTokens, totalTokens)
            transcript += assistantMessage
            val calls = assistantMessage.toolCalls.orEmpty().filterIsInstance<ToolCall.Function>()
            if (calls.isEmpty()) {
                return AssistantReply(
                    text = assistantMessage.content.orEmpty().ifBlank { "The assistant returned no text." },
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    totalTokens = totalTokens,
                )
            }
            calls.forEach { call ->
                val name = call.function.nameOrNull ?: ""
                val arguments = call.function.argumentsOrNull ?: "{}"
                transcript += ChatMessage.Tool(
                    content = executeTool(toolExecutor, name, arguments),
                    toolCallId = call.id,
                )
            }
        }
        return stoppedReply(inputTokens, outputTokens, totalTokens)
    }

    private fun executeTool(executor: AssistantToolExecutor, name: String, arguments: String): String =
        runCatching { executor.execute(name, arguments) }
            .getOrElse { error -> "Tool error: ${error.message ?: error.javaClass.simpleName}" }

    private fun stoppedReply(
        input: Int,
        output: Int,
        total: Int,
        reasoningSummary: String = "",
        reasoningTokens: Int = 0,
    ) = AssistantReply(
        text = "I stopped after $MAX_TOOL_ROUNDS action rounds. Please narrow the request.",
        inputTokens = input,
        outputTokens = output,
        totalTokens = total,
        reasoningSummary = reasoningSummary,
        reasoningTokens = reasoningTokens,
    )

    private fun responseReasoningSummary(items: List<JsonElement>): String = items
        .flatMap(::summaryText)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("\n")

    private fun summaryText(element: JsonElement): List<String> = when (element) {
        is JsonArray -> element.flatMap(::summaryText)
        is JsonObject -> element["text"]?.let(::summaryText)
            ?: element.values.flatMap(::summaryText)
        is JsonPrimitive -> if (element.isString) listOf(element.content) else emptyList()
        else -> emptyList()
    }

    private fun buildResponsesTranscript(
        history: List<AssistantMessage>,
        message: String,
        attachments: List<AssistantAttachment>,
    ): List<JsonObject> = buildList {
        history.takeLast(MAX_HISTORY_MESSAGES).forEach { entry ->
            add(buildJsonObject {
                put("role", if (entry.role == "assistant") "assistant" else "user")
                putJsonArray("content") {
                    add(buildJsonObject {
                        put("type", if (entry.role == "assistant") "output_text" else "input_text")
                        put("text", entry.text)
                    })
                }
            })
        }
        add(buildJsonObject {
            put("role", "user")
            putJsonArray("content") {
                add(buildJsonObject { put("type", "input_text"); put("text", message) })
                attachments.forEach { attachment ->
                    add(buildJsonObject {
                        if (attachment.mimeType.startsWith("image/")) {
                            put("type", "input_image")
                            put("image_url", attachment.dataUrl)
                        } else {
                            put("type", "input_file")
                            put("filename", attachment.name)
                            put("file_data", attachment.dataUrl)
                        }
                    })
                }
            }
        })
    }

    private fun buildChatTranscript(
        history: List<AssistantMessage>,
        message: String,
        attachments: List<AssistantAttachment>,
    ): List<ChatMessage> = buildList {
        add(ChatMessage.System(SYSTEM_INSTRUCTIONS))
        history.takeLast(MAX_HISTORY_MESSAGES).forEach { entry ->
            add(
                if (entry.role == "assistant") ChatMessage.Assistant(entry.text)
                else ChatMessage.User(entry.text),
            )
        }
        val parts = buildList<ContentPart> {
            add(TextPart(message))
            attachments.forEach { attachment ->
                if (attachment.mimeType.startsWith("image/")) {
                    add(ImagePart(attachment.dataUrl))
                } else {
                    add(TextPart(chatAttachmentText(attachment)))
                }
            }
        }
        add(ChatMessage.User(parts))
    }

    private fun chatAttachmentText(attachment: AssistantAttachment): String {
        val textLike = attachment.mimeType.startsWith("text/") || attachment.mimeType in TEXT_MIME_TYPES
        if (!textLike) {
            return "[Attached file: ${attachment.name} (${attachment.mimeType}). This Chat Completions provider cannot receive arbitrary binary files through its OpenAI-compatible endpoint.]"
        }
        val encoded = attachment.dataUrl.substringAfter(";base64,", "")
        val decoded = runCatching { String(Base64.getDecoder().decode(encoded), Charsets.UTF_8) }.getOrNull()
            ?: return "[Attached text file ${attachment.name} could not be decoded.]"
        return "[Attached text file: ${attachment.name}]\n${decoded.take(MAX_CHAT_ATTACHMENT_CHARS)}"
    }

    private fun friendlyFailure(endpoint: AssistantEndpoint, error: Throwable): String {
        val details = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" · ")
        val lower = details.lowercase()
        val protocol = when (endpoint.protocol) {
            AssistantApiProtocol.RESPONSES -> "Responses"
            AssistantApiProtocol.CHAT_COMPLETIONS -> "Chat Completions"
        }
        return when {
            "httpclientjvmkt" in lower ->
                "Android HTTP transport could not start. Install this updated build, which explicitly uses OkHttp."
            "404" in lower || "not found" in lower ->
                "${endpoint.provider} returned HTTP 404 for ${endpoint.requestUrl}. Check BASE_URL and MODEL; Exvia selected the $protocol API automatically."
            "unexpected json token" in lower || "notransformationfoundexception" in lower || "notransformation" in lower ->
                "${endpoint.provider} returned a response that is not compatible with the $protocol API at ${endpoint.requestUrl}. Check that BASE_URL is the provider's OpenAI-compatible API root, not a website or model-list URL."
            details.isNotBlank() -> "$protocol request to ${endpoint.requestUrl} failed: ${details.take(MAX_ERROR_CHARS)}"
            else -> "$protocol request to ${endpoint.requestUrl} failed (${error.javaClass.simpleName})."
        }
    }

    companion object {
        private const val MAX_HISTORY_MESSAGES = 24
        private const val MAX_TOOL_ROUNDS = 6
        private const val MAX_CHAT_ATTACHMENT_CHARS = 60_000
        private const val MAX_ERROR_CHARS = 700

        private val TEXT_MIME_TYPES = setOf(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-yaml",
            "application/yaml",
            "application/sql",
        )

        private val SYSTEM_INSTRUCTIONS = """
            You are the Exvia in-app assistant. Be concise, accurate, and ask a focused question when required information is missing.
            Use inspect_app before assuming table contents, statistics, settings, or scripts. Slash-command context is supplied by the app.
            You may navigate and set temporary filters directly. For persistent changes, use upsert_app_resource or delete_app_resource;
            Exvia will show the user an approval dialog before applying them. Never claim an action succeeded unless its tool output confirms it.
            Do not request or reveal API keys, GitHub tokens, encrypted values, or other secrets. Do not invent a generic shell/code-execution ability.
        """.trimIndent()

        private val GENERATION_INSTRUCTIONS = """
            You create one Exvia configuration artifact from the supplied documentation, examples, output schema, and user request.
            Return exactly one valid JSON object and nothing else: no Markdown fence, explanation, comment, or trailing text.
            Preserve Exvia's documented modules and syntax. Do not invent unavailable modules, APIs, fields, or secrets.
            Prefer a complete, runnable artifact with a concise descriptive name.
        """.trimIndent()

        private fun schema(json: String) = Parameters.fromJsonString(json)

        private data class ToolDefinition(
            val name: String,
            val description: String,
            val strict: Boolean,
            val parameters: Parameters,
        )

        private val TOOL_DEFINITIONS = listOf(
            ToolDefinition(
                "inspect_app",
                "Read current Exvia table data, statistics, scripts, or safe settings. Use name to narrow an accordion or resource.",
                true,
                schema("""{"type":"object","properties":{"scope":{"type":"string","enum":["table","statistics","scripts","settings","all"]},"name":{"type":["string","null"]}},"required":["scope","name"],"additionalProperties":false}"""),
            ),
            ToolDefinition(
                "navigate_app",
                "Navigate to a visible Exvia section.",
                true,
                schema("""{"type":"object","properties":{"section":{"type":"string","enum":["table","stat","files","assistant"]}},"required":["section"],"additionalProperties":false}"""),
            ),
            ToolDefinition(
                "set_table_query",
                "Set or clear the current temporary Filtering or Flagging query. This is reversible and does not persist a snippet.",
                true,
                schema("""{"type":"object","properties":{"mode":{"type":"string","enum":["filtering","flagging"]},"query":{"type":"string"},"enabled":{"type":"boolean"}},"required":["mode","query","enabled"],"additionalProperties":false}"""),
            ),
            ToolDefinition(
                "upsert_app_resource",
                "Create or update one named Exvia configuration resource. Persistent changes require user approval in the app.",
                false,
                schema("""{"type":"object","properties":{"resource":{"type":"string","enum":["filter_snippet","flagging_rule","color_mapping","script_group","custom_metric","custom_plot","file_script","imaginary_field","environment_variable","notification_rule","schema_rule","metric_color_mapping"]},"id":{"type":"string"},"name":{"type":"string"},"query":{"type":"string"},"script":{"type":"string"},"foreground_script":{"type":"string"},"background_script":{"type":"string"},"content_script":{"type":"string"},"engine":{"type":"string"},"group_id":{"type":"string"},"expression":{"type":"string"},"event_name":{"type":"string"},"metric_name":{"type":"string"},"enabled":{"type":"boolean"}},"required":["resource","name"],"additionalProperties":false}"""),
            ),
            ToolDefinition(
                "delete_app_resource",
                "Delete one Exvia configuration resource by id or exact name. Persistent changes require user approval in the app.",
                true,
                schema("""{"type":"object","properties":{"resource":{"type":"string","enum":["filter_snippet","flagging_rule","color_mapping","script_group","custom_metric","custom_plot","file_script","imaginary_field","environment_variable","notification_rule","schema_rule","metric_color_mapping"]},"id_or_name":{"type":"string"}},"required":["resource","id_or_name"],"additionalProperties":false}"""),
            ),
        )

        private val RESPONSE_TOOLS = TOOL_DEFINITIONS.map { definition ->
            ResponseTool(
                type = "function",
                name = definition.name,
                description = definition.description,
                strict = definition.strict,
                parameters = definition.parameters,
            )
        }

        private val CHAT_TOOLS = TOOL_DEFINITIONS.map { definition ->
            Tool.function(
                name = definition.name,
                description = definition.description,
                parameters = definition.parameters,
            )
        }
    }
}
