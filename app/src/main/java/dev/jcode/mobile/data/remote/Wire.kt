package dev.jcode.mobile.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Client requests, mirroring `crates/jcode-protocol/src/wire.rs`
 * (`#[serde(tag = "type")]`, snake_case tags).
 *
 * Only the requests this client uses are modelled. The server ignores fields it
 * does not expect, so extra fields are harmless and missing optional fields are
 * omitted rather than sent as null.
 */
sealed interface Request {
    val id: Long
    fun toJson(): JsonObject
}

data class Subscribe(
    override val id: Long,
    val targetSessionId: String? = null,
    val clientInstanceId: String,
    val workingDir: String? = null,
    val clientHasLocalHistory: Boolean = false,
    val allowSessionTakeover: Boolean = true,
    val continueOnDisconnect: Boolean = true,
) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "subscribe")
        put("id", id)
        put("client_instance_id", clientInstanceId)
        put("client_has_local_history", clientHasLocalHistory)
        put("allow_session_takeover", allowSessionTakeover)
        put("continue_on_disconnect", continueOnDisconnect)
        if (targetSessionId != null) put("target_session_id", targetSessionId)
        if (!workingDir.isNullOrBlank()) put("working_dir", workingDir)
    }
}

data class GetHistory(override val id: Long) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "get_history")
        put("id", id)
    }
}

data class SendMessage(
    override val id: Long,
    val content: String,
    val images: List<Pair<String, String>> = emptyList(),
) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "message")
        put("id", id)
        put("content", content)
        if (images.isNotEmpty()) {
            putJsonArray("images") {
                images.forEach { (mime, data) ->
                    add(buildJsonObject {
                        put("mime", mime)
                        put("data", data)
                    })
                }
            }
        }
    }
}

data class Cancel(override val id: Long) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "cancel")
        put("id", id)
    }
}

data class SoftInterrupt(
    override val id: Long,
    val content: String,
    val urgent: Boolean = false,
    val clientId: String? = null,
) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "soft_interrupt")
        put("id", id)
        put("content", content)
        put("urgent", urgent)
        if (clientId != null) put("client_id", clientId)
    }
}

data class CancelSoftInterrupts(
    override val id: Long,
    val clientIds: List<String>? = null,
) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "cancel_soft_interrupts")
        put("id", id)
        if (!clientIds.isNullOrEmpty()) {
            putJsonArray("client_ids") { clientIds.forEach { add(it) } }
        }
    }
}

data class ResumeSession(
    override val id: Long,
    val sessionId: String,
    val clientInstanceId: String,
    val clientHasLocalHistory: Boolean = false,
    val allowSessionTakeover: Boolean = true,
) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "resume_session")
        put("id", id)
        put("session_id", sessionId)
        put("client_instance_id", clientInstanceId)
        put("client_has_local_history", clientHasLocalHistory)
        put("allow_session_takeover", allowSessionTakeover)
    }
}

data class SetModel(override val id: Long, val model: String) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "set_model")
        put("id", id)
        put("model", model)
    }
}

data class SetReasoningEffort(override val id: Long, val effort: String) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "set_reasoning_effort")
        put("id", id)
        put("effort", effort)
    }
}

data class RenameSession(override val id: Long, val title: String?) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "rename_session")
        put("id", id)
        if (title != null) put("title", title)
    }
}

data class ClearConversation(override val id: Long) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "clear")
        put("id", id)
    }
}

data class Ping(override val id: Long) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "ping")
        put("id", id)
    }
}

data class Compact(override val id: Long) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "compact")
        put("id", id)
    }
}

data class SetSessionSaved(
    override val id: Long,
    val saved: Boolean,
    val label: String? = null,
) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "set_session_saved")
        put("id", id)
        put("saved", saved)
        if (label != null) put("label", label)
    }
}

/**
 * Answers an SDK-style tool call that is waiting on the client, such as a
 * question or a permission prompt rendered by the app itself.
 */
data class SubmitToolResult(
    override val id: Long,
    val callId: String,
    val output: String,
    val error: String? = null,
) : Request {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "tool_result")
        put("id", id)
        put("call_id", callId)
        put("output", output)
        if (error != null) put("error", error)
    }
}

// ---------------------------------------------------------------------------
// Server events
// ---------------------------------------------------------------------------

data class ToolCallRecord(
    val id: String,
    val name: String,
    val input: String,
    val output: String?,
    val error: String?,
)

data class HistoryMessage(
    val role: String,
    val content: String,
    val toolCalls: List<String>,
    val toolData: ToolCallRecord?,
)

data class HistoryPayload(
    val id: Long,
    val sessionId: String,
    val messages: List<HistoryMessage>,
    val messagesBaseCount: Int?,
    val providerName: String?,
    val providerModel: String?,
    val availableModels: List<String>,
    val totalTokens: Pair<Long, Long>?,
    val allSessions: List<String>,
    val serverVersion: String?,
    val serverName: String?,
    val displayTitle: String?,
    val reasoningEffort: String?,
    val statusDetail: String?,
    val wasInterrupted: Boolean,
)

sealed interface ServerEvent {
    data class Ack(val id: Long) : ServerEvent
    data class TextDelta(val text: String) : ServerEvent
    data class ReasoningDelta(val text: String) : ServerEvent
    data class ReasoningDone(val durationSecs: Double?) : ServerEvent
    data class TextReplace(val text: String) : ServerEvent
    data class ToolStart(val id: String, val name: String) : ServerEvent
    data class ToolInput(val delta: String) : ServerEvent
    data class ToolExec(val id: String, val name: String) : ServerEvent
    data class ToolDone(val id: String, val name: String, val output: String, val error: String?) : ServerEvent
    data class Tokens(val input: Long, val output: Long) : ServerEvent
    data class StatusDetail(val detail: String) : ServerEvent
    data class ConnectionPhase(val phase: String) : ServerEvent
    data class SoftInterruptInjected(
        val content: String,
        val displayRole: String?,
        val point: String,
        val toolsSkipped: Int?,
        val clientIds: List<String>,
    ) : ServerEvent
    data class SoftInterruptsCancelled(
        val id: Long,
        val cancelled: List<String>,
        val notQueued: List<String>,
    ) : ServerEvent
    data class RetryRollback(val attempt: Int, val max: Int) : ServerEvent
    data object MessageEnd : ServerEvent
    data object Interrupted : ServerEvent
    data class Done(val id: Long) : ServerEvent
    data class ErrorEvent(val id: Long, val message: String, val retryAfterSecs: Long?) : ServerEvent
    data class Pong(val id: Long) : ServerEvent
    data class State(val id: Long, val sessionId: String, val messageCount: Int, val isProcessing: Boolean) : ServerEvent
    data class SessionId(val sessionId: String) : ServerEvent
    data class SessionRenamed(val sessionId: String, val displayTitle: String) : ServerEvent
    data class History(val payload: HistoryPayload) : ServerEvent
    data class ModelChanged(val id: Long, val model: String, val error: String?) : ServerEvent
    data class ReasoningEffortChanged(val id: Long, val effort: String?, val error: String?) : ServerEvent
    data class CompactResult(val id: Long, val message: String, val success: Boolean) : ServerEvent
    data class AvailableModelsUpdated(val models: List<String>, val providerModel: String?) : ServerEvent
    data class Compaction(val trigger: String, val tokensSaved: Long?) : ServerEvent
    data class Notification(val fromName: String?, val message: String) : ServerEvent
    data class Reloading(val newSocket: String?) : ServerEvent
    data class SessionCloseRequested(val reason: String) : ServerEvent
    data class Unknown(val type: String) : ServerEvent
}

object Wire {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(request: Request): String = request.toJson().toString()

    /**
     * Decodes one newline-delimited JSON event. Unknown event types decode as
     * [ServerEvent.Unknown] so a newer server never breaks an older app.
     */
    fun decodeEvent(line: String): ServerEvent? {
        val element = runCatching { json.parseToJsonElement(line) }.getOrNull() ?: return null
        val obj = element as? JsonObject ?: return null
        val type = obj.optString("type") ?: return null
        return when (type) {
            "ack" -> ServerEvent.Ack(obj.long("id"))
            "text_delta" -> ServerEvent.TextDelta(obj.string("text"))
            "reasoning_delta" -> ServerEvent.ReasoningDelta(obj.string("text"))
            "reasoning_done" -> ServerEvent.ReasoningDone(obj.optDouble("duration_secs"))
            "text_replace" -> ServerEvent.TextReplace(obj.string("text"))
            "tool_start" -> ServerEvent.ToolStart(obj.string("id"), obj.string("name"))
            "tool_input" -> ServerEvent.ToolInput(obj.string("delta"))
            "tool_exec" -> ServerEvent.ToolExec(obj.string("id"), obj.string("name"))
            "tool_done" -> ServerEvent.ToolDone(
                id = obj.string("id"),
                name = obj.string("name"),
                output = obj.string("output"),
                error = obj.optString("error"),
            )
            "tokens" -> ServerEvent.Tokens(obj.long("input"), obj.long("output"))
            "status_detail" -> ServerEvent.StatusDetail(obj.string("detail"))
            "connection_phase" -> ServerEvent.ConnectionPhase(obj.string("phase"))
            "soft_interrupt_injected" -> ServerEvent.SoftInterruptInjected(
                content = obj.string("content"),
                displayRole = obj.optString("display_role"),
                point = obj.string("point"),
                toolsSkipped = obj.optInt("tools_skipped"),
                clientIds = obj.stringList("client_ids"),
            )
            "soft_interrupts_cancelled" -> ServerEvent.SoftInterruptsCancelled(
                id = obj.long("id"),
                cancelled = obj.stringList("cancelled"),
                notQueued = obj.stringList("not_queued"),
            )
            "retry_rollback" -> ServerEvent.RetryRollback(obj.int("attempt"), obj.int("max"))
            "message_end" -> ServerEvent.MessageEnd
            "interrupted" -> ServerEvent.Interrupted
            "done" -> ServerEvent.Done(obj.long("id"))
            "error" -> ServerEvent.ErrorEvent(
                id = obj.long("id"),
                message = obj.string("message"),
                retryAfterSecs = obj.optLong("retry_after_secs"),
            )
            "pong" -> ServerEvent.Pong(obj.long("id"))
            "state" -> ServerEvent.State(
                id = obj.long("id"),
                sessionId = obj.string("session_id"),
                messageCount = obj.int("message_count"),
                isProcessing = obj.bool("is_processing"),
            )
            "session" -> ServerEvent.SessionId(obj.string("session_id"))
            "session_renamed" -> ServerEvent.SessionRenamed(
                sessionId = obj.string("session_id"),
                displayTitle = obj.string("display_title"),
            )
            "history" -> ServerEvent.History(decodeHistory(obj))
            "model_changed" -> ServerEvent.ModelChanged(
                id = obj.long("id"),
                model = obj.string("model"),
                error = obj.optString("error"),
            )
            "reasoning_effort_changed" -> ServerEvent.ReasoningEffortChanged(
                id = obj.long("id"),
                effort = obj.optString("effort"),
                error = obj.optString("error"),
            )
            "compact_result" -> ServerEvent.CompactResult(
                id = obj.long("id"),
                message = obj.string("message"),
                success = obj.bool("success"),
            )
            "available_models_updated" -> ServerEvent.AvailableModelsUpdated(
                models = obj.stringList("available_models"),
                providerModel = obj.optString("provider_model"),
            )
            "compaction" -> ServerEvent.Compaction(
                trigger = obj.string("trigger"),
                tokensSaved = obj.optLong("tokens_saved"),
            )
            "notification" -> ServerEvent.Notification(
                fromName = obj.optString("from_name"),
                message = obj.string("message"),
            )
            "reloading" -> ServerEvent.Reloading(obj.optString("new_socket"))
            "session_close_requested" -> ServerEvent.SessionCloseRequested(obj.string("reason"))
            else -> ServerEvent.Unknown(type)
        }
    }

    private fun decodeHistory(obj: JsonObject): HistoryPayload {
        val messages = (obj["messages"] as? JsonArray).orEmpty().mapNotNull { element ->
            val message = element as? JsonObject ?: return@mapNotNull null
            val toolData = (message["tool_data"] as? JsonObject)?.let { data ->
                ToolCallRecord(
                    id = data.string("id"),
                    name = data.string("name"),
                    input = data.string("input"),
                    output = data.optString("output"),
                    error = data.optString("error"),
                )
            }
            HistoryMessage(
                role = message.string("role"),
                content = message.string("content"),
                toolCalls = message.stringList("tool_calls"),
                toolData = toolData,
            )
        }
        val tokens = (obj["total_tokens"] as? JsonArray)?.takeIf { it.size >= 2 }?.let { pair ->
            val input = pair[0].asLongOrNull()
            val output = pair[1].asLongOrNull()
            if (input != null && output != null) input to output else null
        }
        return HistoryPayload(
            id = obj.long("id"),
            sessionId = obj.string("session_id"),
            messages = messages,
            messagesBaseCount = obj.optInt("messages_base_count"),
            providerName = obj.optString("provider_name"),
            providerModel = obj.optString("provider_model"),
            availableModels = obj.stringList("available_models"),
            totalTokens = tokens,
            allSessions = obj.stringList("all_sessions"),
            serverVersion = obj.optString("server_version"),
            serverName = obj.optString("server_name"),
            displayTitle = obj.optString("display_title"),
            reasoningEffort = obj.optString("reasoning_effort"),
            statusDetail = obj.optString("status_detail"),
            wasInterrupted = obj.bool("was_interrupted"),
        )
    }
}

// Lenient accessors: absent or null fields must never crash the client.
private fun JsonObject.primitive(key: String): JsonPrimitive? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

private fun JsonObject.string(key: String): String = primitive(key)?.contentOrNull ?: ""

private fun JsonObject.optString(key: String): String? =
    primitive(key)?.contentOrNull?.takeIf { it.isNotEmpty() }

private fun JsonObject.long(key: String): Long = primitive(key)?.contentOrNull?.toLongOrNull() ?: 0L

private fun JsonObject.optLong(key: String): Long? = primitive(key)?.contentOrNull?.toLongOrNull()

private fun JsonObject.int(key: String): Int = primitive(key)?.contentOrNull?.toIntOrNull() ?: 0

private fun JsonObject.optInt(key: String): Int? = primitive(key)?.contentOrNull?.toIntOrNull()

private fun JsonObject.bool(key: String): Boolean =
    primitive(key)?.contentOrNull?.toBooleanStrictOrNull() ?: false

private fun JsonObject.optDouble(key: String): Double? =
    primitive(key)?.contentOrNull?.toDoubleOrNull()

private fun JsonObject.stringList(key: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { element ->
        (element as? JsonPrimitive)?.contentOrNull
    }

private fun JsonElement.asLongOrNull(): Long? =
    (this as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
