package dev.jcode.mobile.data.chat

import dev.jcode.mobile.data.remote.HistoryPayload
import dev.jcode.mobile.data.remote.ServerEvent

/**
 * Pure state machine turning wire events into transcript state.
 *
 * Ported from the proven iOS reducer (`ios/Sources/JCodeKit/SessionReducer.swift`).
 * Keeping this free of Android and Compose types is what makes every streaming
 * edge case unit-testable.
 */
object SessionReducer {

    sealed interface Intent {
        data class UserSentMessage(val text: String) : Intent
        data class UserQueuedInterrupt(val clientId: String, val text: String) : Intent
        data class CancelledQueuedInterrupt(val clientId: String) : Intent
        data object CancelledQueuedInterrupts : Intent
        data class AnswerTool(val callId: String) : Intent
        data class DismissPermission(val id: String) : Intent
        data object ClearPermissions : Intent
        data object DismissError : Intent
        data class DismissNotice(val id: String) : Intent
        data object ClearedConversation : Intent
        data object Reset : Intent
    }

    fun reduce(state: SessionState, event: ServerEvent): SessionState = when (event) {
        is ServerEvent.TextDelta -> withStreamingAssistant(state) { it.copy(text = it.text + event.text) }

        is ServerEvent.ReasoningDelta -> withStreamingAssistant(
            state.copy(isReasoning = true),
        ) { it.copy(reasoning = it.reasoning + event.text) }

        is ServerEvent.ReasoningDone -> state.copy(isReasoning = false)

        is ServerEvent.TextReplace -> withStreamingAssistant(state) { it.copy(text = event.text) }

        is ServerEvent.ToolStart -> withStreamingAssistant(state) { entry ->
            entry.copy(
                tools = entry.tools + ToolCall(id = event.id, name = event.name),
            )
        }

        is ServerEvent.ToolInput -> withStreamingAssistant(state) { entry ->
            if (entry.tools.isEmpty()) entry
            else entry.copy(
                tools = entry.tools.mapIndexed { index, tool ->
                    if (index == entry.tools.lastIndex) tool.copy(input = tool.input + event.delta) else tool
                },
            )
        }

        is ServerEvent.ToolExec -> withStreamingAssistant(state) { entry ->
            val existing = entry.tools.indexOfFirst { it.id == event.id }
            if (existing >= 0) {
                entry.copy(
                    tools = entry.tools.mapIndexed { index, tool ->
                        if (index == existing) tool.copy(status = ToolStatus.Running) else tool
                    },
                )
            } else {
                entry.copy(tools = entry.tools + ToolCall(id = event.id, name = event.name, status = ToolStatus.Running))
            }
        }

        is ServerEvent.ToolDone -> {
            val updated = withStreamingAssistant(state) { entry ->
                val existing = entry.tools.indexOfFirst { it.id == event.id }
                val completed = if (event.error != null) ToolStatus.Failed else ToolStatus.Succeeded
                if (existing >= 0) {
                    entry.copy(
                        tools = entry.tools.mapIndexed { index, tool ->
                            if (index == existing) {
                                tool.copy(output = event.output, status = completed, error = event.error)
                            } else {
                                tool
                            }
                        },
                    )
                } else {
                    entry.copy(
                        tools = entry.tools + ToolCall(
                            id = event.id,
                            name = event.name,
                            output = event.output,
                            status = completed,
                            error = event.error,
                        ),
                    )
                }
            }
            if (event.name == "todo") applyTodoFromTool(updated, event.id, event.output) else updated
        }

        ServerEvent.MessageEnd -> finishStreaming(state)

        is ServerEvent.ConnectionPhase -> state.copy(
            serverPhase = event.phase.takeIf { it.isNotEmpty() },
        )

        is ServerEvent.SoftInterruptInjected -> resolvePendingInterrupt(
            state,
            content = event.content,
            displayRole = event.displayRole,
            clientIds = event.clientIds,
        )

        is ServerEvent.SoftInterruptsCancelled -> removeQueuedInterrupts(state, event.cancelled)

        is ServerEvent.RetryRollback -> {
            val trimmed = if (state.transcript.lastOrNull()?.streaming == true) {
                state.transcript.dropLast(1)
            } else {
                state.transcript
            }
            state.copy(
                transcript = trimmed,
                isReasoning = false,
                statusDetail = "Retrying (${event.attempt}/${event.max})",
            )
        }

        is ServerEvent.Done -> drainPendingInterrupts(
            finishStreaming(state).copy(isProcessing = false, isReasoning = false, serverPhase = null),
        )

        ServerEvent.Interrupted -> drainPendingInterrupts(
            finishStreaming(state).copy(
                isProcessing = false,
                isReasoning = false,
                serverPhase = null,
                notices = state.notices + Notice(message = "Interrupted"),
            ),
        )

        is ServerEvent.ErrorEvent -> finishStreaming(state).copy(
            isProcessing = false,
            isReasoning = false,
            serverPhase = null,
            errorBanner = if (event.retryAfterSecs != null) {
                "${event.message} (retry in ${event.retryAfterSecs}s)"
            } else {
                event.message
            },
        )

        is ServerEvent.Tokens -> state.copy(tokenInput = event.input, tokenOutput = event.output)

        is ServerEvent.StatusDetail -> state.copy(statusDetail = event.detail)

        is ServerEvent.State -> state.withKnownSession(event.sessionId).copy(
            isProcessing = event.isProcessing,
        )

        is ServerEvent.SessionId -> state.withKnownSession(event.sessionId)

        is ServerEvent.SessionRenamed -> {
            val titles = state.sessionTitles + (event.sessionId to event.displayTitle)
            state.copy(
                sessionTitles = titles,
                sessionTitle = if (state.sessionId == null || state.sessionId == event.sessionId) {
                    event.displayTitle
                } else {
                    state.sessionTitle
                },
            )
        }

        is ServerEvent.History -> applyHistory(state, event.payload)

        is ServerEvent.ModelChanged -> if (event.error != null) {
            state.copy(errorBanner = event.error)
        } else {
            state.copy(modelName = event.model)
        }

        is ServerEvent.ReasoningEffortChanged -> if (event.error != null) {
            state.copy(errorBanner = event.error)
        } else {
            state.copy(reasoningEffort = event.effort)
        }

        is ServerEvent.CompactResult -> if (event.success) {
            state.copy(notices = state.notices + Notice(kind = Notice.Kind.Compaction, message = event.message))
        } else {
            state.copy(errorBanner = event.message)
        }

        is ServerEvent.AvailableModelsUpdated -> state.copy(
            availableModels = event.models,
            modelName = event.providerModel ?: state.modelName,
        )

        is ServerEvent.Compaction -> {
            val saved = event.tokensSaved
            if (saved != null && event.trigger != "background") {
                state.copy(
                    notices = state.notices + Notice(
                        kind = Notice.Kind.Compaction,
                        message = "Context compacted ($saved tokens saved)",
                    ),
                )
            } else {
                state
            }
        }

        is ServerEvent.Notification -> {
            val notices = state.notices + Notice(
                kind = Notice.Kind.Notification,
                message = (event.fromName?.let { "$it: " } ?: "") + event.message,
            )
            val permissions = if (looksLikePermission(event.message)) {
                val view = PermissionView(
                    id = permissionId(event.message),
                    message = event.message,
                )
                state.permissions.filterNot { it.message == view.message } + view
            } else {
                state.permissions
            }
            state.copy(notices = notices, permissions = permissions)
        }

        is ServerEvent.Reloading -> state.copy(
            notices = state.notices + Notice(message = "Server is updating, reconnecting shortly"),
        )

        is ServerEvent.SessionCloseRequested -> finishStreaming(state).copy(
            isProcessing = false,
            isReasoning = false,
            errorBanner = event.reason.ifEmpty { "Server closed this session" },
        )

        is ServerEvent.Ack, is ServerEvent.Pong, is ServerEvent.Unknown -> state
    }

    fun reduce(state: SessionState, intent: Intent): SessionState = when (intent) {
        is Intent.UserSentMessage -> state.copy(
            transcript = state.transcript + Entry(role = Role.User, text = intent.text),
            isProcessing = true,
            errorBanner = null,
        )

        is Intent.UserQueuedInterrupt -> state.copy(
            transcript = state.transcript + Entry(
                role = Role.User,
                text = intent.text,
                queued = true,
                queueId = intent.clientId,
            ),
            pendingInterrupts = state.pendingInterrupts + QueuedInterrupt(intent.clientId, intent.text),
        )

        is Intent.CancelledQueuedInterrupt -> removeQueuedInterrupts(state, listOf(intent.clientId))

        Intent.CancelledQueuedInterrupts -> state.copy(
            transcript = state.transcript.filterNot { it.queued },
            pendingInterrupts = emptyList(),
        )

        is Intent.AnswerTool -> state.copy(answeredToolCalls = state.answeredToolCalls + intent.callId)

        is Intent.DismissPermission -> state.copy(
            permissions = state.permissions.filterNot { it.id == intent.id },
        )

        Intent.ClearPermissions -> state.copy(permissions = emptyList())

        Intent.DismissError -> state.copy(errorBanner = null)

        is Intent.DismissNotice -> state.copy(notices = state.notices.filterNot { it.id == intent.id })

        Intent.ClearedConversation -> state.copy(
            transcript = emptyList(),
            isProcessing = false,
            isReasoning = false,
            errorBanner = null,
        )

        Intent.Reset -> SessionState()
    }

    fun withPhase(state: SessionState, phase: ConnectionPhase, reason: String? = null): SessionState {
        val base = state.copy(phase = phase, failureReason = reason)
        return when (phase) {
            ConnectionPhase.Connected -> base.copy(errorBanner = null)
            ConnectionPhase.Failed -> base.copy(
                errorBanner = reason,
                isProcessing = false,
                isReasoning = false,
            )
            ConnectionPhase.Disconnected, ConnectionPhase.Reconnecting -> finishStreaming(
                base.copy(isProcessing = false, isReasoning = false, serverPhase = null),
            )
            ConnectionPhase.Connecting -> base
        }
    }

    private fun applyHistory(state: SessionState, payload: HistoryPayload): SessionState {
        var latestTodos: List<TodoView>? = null
        val transcript = payload.messages.mapNotNull { message ->
            val role = when (message.role) {
                "user" -> Role.User
                "assistant" -> Role.Assistant
                "system" -> Role.System
                else -> return@mapNotNull null
            }
            val tools = buildList {
                val data = message.toolData
                if (data != null) {
                    if (data.name == "todo") {
                        TodoParser.parse(data.input, data.output.orEmpty())?.let { latestTodos = it }
                    }
                    add(
                        ToolCall(
                            id = data.id,
                            name = data.name,
                            input = data.input,
                            output = data.output.orEmpty(),
                            status = when {
                                data.error != null -> ToolStatus.Failed
                                data.output != null -> ToolStatus.Succeeded
                                else -> ToolStatus.Running
                            },
                            error = data.error,
                        ),
                    )
                } else {
                    message.toolCalls.forEach { name ->
                        add(ToolCall(id = name, name = name, status = ToolStatus.Succeeded))
                    }
                }
            }
            if (message.content.isEmpty() && tools.isEmpty()) return@mapNotNull null
            Entry(role = role, text = message.content, tools = tools)
        }

        val titles = if (payload.displayTitle != null) {
            state.sessionTitles + (payload.sessionId to payload.displayTitle)
        } else {
            state.sessionTitles
        }

        return state.copy(
            transcript = transcript,
            sessionId = payload.sessionId,
            providerName = payload.providerName ?: state.providerName,
            modelName = payload.providerModel ?: state.modelName,
            availableModels = payload.availableModels.ifEmpty { state.availableModels },
            allSessions = withSession(
                payload.allSessions.ifEmpty { state.allSessions },
                payload.sessionId,
            ),
            serverVersion = payload.serverVersion ?: state.serverVersion,
            serverName = payload.serverName ?: state.serverName,
            sessionTitle = payload.displayTitle ?: state.sessionTitle,
            sessionTitles = titles,
            reasoningEffort = payload.reasoningEffort ?: state.reasoningEffort,
            tokenInput = payload.totalTokens?.first ?: state.tokenInput,
            tokenOutput = payload.totalTokens?.second ?: state.tokenOutput,
            statusDetail = payload.statusDetail ?: state.statusDetail,
            pendingInterrupts = emptyList(),
            todos = latestTodos ?: state.todos,
        )
    }

    /** Updates the pinned plan from a completed `todo` tool call. */
    private fun applyTodoFromTool(
        state: SessionState,
        toolId: String,
        output: String,
    ): SessionState {
        val input = state.transcript
            .asReversed()
            .asSequence()
            .flatMap { it.tools.asReversed().asSequence() }
            .firstOrNull { it.id == toolId }
            ?.input
            .orEmpty()
        val todos = TodoParser.parse(input, output) ?: return state
        return state.copy(todos = todos)
    }

    private fun looksLikePermission(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("permission") || lower.contains("approval required") ||
            lower.contains("needs approval")
    }

    private fun permissionId(message: String): String {
        val match = Regex("[0-9a-fA-F]{8,64}").find(message)
        return match?.value ?: message.hashCode().toString()
    }

    private fun resolvePendingInterrupt(
        state: SessionState,
        content: String,
        displayRole: String?,
        clientIds: List<String>,
    ): SessionState {
        val ids = clientIds.toSet()
        val pending = state.pendingInterrupts.filterNot { queued ->
            queued.clientId in ids || queued.text == content
        }
        val queuedIndex = state.transcript.indexOfFirst { it.queueId in ids || it.text == content }
            .takeIf { it >= 0 && state.transcript[it].queued }
            ?: state.transcript.indexOfFirst { it.queued }
        val transcript = if (queuedIndex >= 0) {
            state.transcript.mapIndexed { index, entry ->
                if (index == queuedIndex) entry.copy(queued = false, queueId = null) else entry
            }
        } else {
            val role = if (displayRole == "system") Role.System else Role.User
            state.transcript + Entry(role = role, text = content)
        }
        return state.copy(transcript = transcript, pendingInterrupts = pending)
    }

    /** Server-confirmed removal: the ids are no longer waiting. */
    private fun removeQueuedInterrupts(state: SessionState, ids: List<String>): SessionState {
        if (ids.isEmpty()) return state
        val removed = ids.toSet()
        return state.copy(
            pendingInterrupts = state.pendingInterrupts.filterNot { it.clientId in removed },
            transcript = state.transcript.filterNot { it.queued && it.queueId in removed },
        )
    }

    private fun drainPendingInterrupts(state: SessionState): SessionState = state.copy(
        pendingInterrupts = emptyList(),
        transcript = state.transcript.map { if (it.queued) it.copy(queued = false) else it },
    )

    /** A session the server just named belongs in the sidebar immediately. */
    private fun SessionState.withKnownSession(sessionId: String): SessionState = copy(
        sessionId = sessionId,
        allSessions = withSession(allSessions, sessionId),
    )

    private fun withSession(sessions: List<String>, sessionId: String): List<String> =
        if (sessionId.isBlank() || sessionId in sessions) sessions else sessions + sessionId

    private inline fun withStreamingAssistant(
        state: SessionState,
        mutate: (Entry) -> Entry,
    ): SessionState {
        val last = state.transcript.lastOrNull()
        return if (last != null && last.role == Role.Assistant && last.streaming) {
            state.copy(
                transcript = state.transcript.dropLast(1) + mutate(last),
            )
        } else {
            state.copy(
                transcript = state.transcript + mutate(
                    Entry(role = Role.Assistant, streaming = true),
                ),
            )
        }
    }

    private fun finishStreaming(state: SessionState): SessionState {
        val last = state.transcript.lastOrNull() ?: return state
        if (!last.streaming) return state
        val transcript = if (last.text.isEmpty() && last.tools.isEmpty() && last.reasoning.isEmpty()) {
            state.transcript.dropLast(1)
        } else {
            state.transcript.dropLast(1) + last.copy(streaming = false)
        }
        return state.copy(transcript = transcript)
    }
}
