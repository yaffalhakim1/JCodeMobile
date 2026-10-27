package dev.jcode.mobile.data.chat

import java.util.UUID

enum class ConnectionPhase {
    Disconnected,
    Connecting,
    Connected,
    Reconnecting,
    Failed,
}

enum class Role {
    User,
    Assistant,
    System,
}

enum class ToolStatus {
    StreamingInput,
    Running,
    Succeeded,
    Failed,
}

data class ToolCall(
    val id: String,
    val name: String,
    val input: String = "",
    val output: String = "",
    val status: ToolStatus = ToolStatus.StreamingInput,
    val error: String? = null,
)

data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String = "",
    val reasoning: String = "",
    val reasoningDurationSecs: Double? = null,
    val tools: List<ToolCall> = emptyList(),
    val streaming: Boolean = false,
    val queued: Boolean = false,
    val queueId: String? = null,
)

/** A prompt waiting for the next safe point of the running turn. */
data class QueuedInterrupt(
    val clientId: String,
    val text: String,
)

data class Notice(
    val id: String = UUID.randomUUID().toString(),
    val kind: Kind = Kind.Info,
    val message: String,
) {
    enum class Kind { Info, Notification, Compaction }
}

data class TodoView(
    val id: String,
    val content: String,
    val status: String,
    val priority: String,
    val group: String?,
) {
    val isDone: Boolean get() = status == "completed"
    val isActive: Boolean get() = status == "in_progress"
    val isCancelled: Boolean get() = status == "cancelled"
}

data class PermissionView(
    val id: String,
    val message: String,
    val receivedAt: Long = System.currentTimeMillis(),
)

data class SessionState(
    val phase: ConnectionPhase = ConnectionPhase.Disconnected,
    val failureReason: String? = null,
    val transcript: List<Entry> = emptyList(),
    val sessionId: String? = null,
    val sessionTitle: String? = null,
    val workingDir: String? = null,
    val allSessions: List<String> = emptyList(),
    val sessionTitles: Map<String, String> = emptyMap(),
    val isProcessing: Boolean = false,
    val isReasoning: Boolean = false,
    val modelName: String? = null,
    val providerName: String? = null,
    val availableModels: List<String> = emptyList(),
    val reasoningEffort: String? = null,
    val serverVersion: String? = null,
    val serverName: String? = null,
    val tokenInput: Long = 0,
    val tokenOutput: Long = 0,
    val statusDetail: String? = null,
    val serverPhase: String? = null,
    val errorBanner: String? = null,
    val notices: List<Notice> = emptyList(),
    val pendingInterrupts: List<QueuedInterrupt> = emptyList(),
    val todos: List<TodoView> = emptyList(),
    val permissions: List<PermissionView> = emptyList(),
    val answeredToolCalls: Set<String> = emptySet(),
) {
    val hasPendingInterrupts: Boolean get() = pendingInterrupts.isNotEmpty()

    val todoDone: Int get() = todos.count { it.isDone }
    val todoTotal: Int get() = todos.size

    fun titleFor(sessionId: String): String? = sessionTitles[sessionId]
}
