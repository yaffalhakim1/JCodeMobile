package dev.jcode.mobile

import dev.jcode.mobile.data.chat.ConnectionPhase
import dev.jcode.mobile.data.chat.Entry
import dev.jcode.mobile.data.chat.Role
import dev.jcode.mobile.data.chat.SessionReducer
import dev.jcode.mobile.data.chat.SessionState
import dev.jcode.mobile.data.chat.ToolStatus
import dev.jcode.mobile.data.remote.ServerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReducerTest {

    private fun SessionState.reduce(event: ServerEvent) = SessionReducer.reduce(this, event)

    @Test
    fun textDeltasAccumulateIntoOneStreamingEntry() {
        var state = SessionState()
        state = state.reduce(ServerEvent.TextDelta("Hel"))
        state = state.reduce(ServerEvent.TextDelta("lo"))
        assertEquals(1, state.transcript.size)
        assertEquals("Hello", state.transcript[0].text)
        assertTrue(state.transcript[0].streaming)
        assertEquals(Role.Assistant, state.transcript[0].role)
    }

    @Test
    fun toolLifecycleUpdatesSameCard() {
        var state = SessionState()
        state = state.reduce(ServerEvent.TextDelta("working"))
        state = state.reduce(ServerEvent.ToolStart("t1", "bash"))
        state = state.reduce(ServerEvent.ToolInput("{\"command\""))
        state = state.reduce(ServerEvent.ToolInput(":\"ls\"}"))
        state = state.reduce(ServerEvent.ToolExec("t1", "bash"))
        state = state.reduce(ServerEvent.ToolDone("t1", "bash", "a.rs\n", null))
        val tool = state.transcript.single().tools.single()
        assertEquals("{\"command\":\"ls\"}", tool.input)
        assertEquals("a.rs\n", tool.output)
        assertEquals(ToolStatus.Succeeded, tool.status)
    }

    @Test
    fun retryRollbackDropsPartialAttempt() {
        var state = SessionState()
        state = state.reduce(ServerEvent.TextDelta("partial"))
        state = state.reduce(ServerEvent.RetryRollback(2, 5))
        assertTrue(state.transcript.isEmpty())
        assertEquals("Retrying (2/5)", state.statusDetail)
    }

    @Test
    fun queuedInterruptResolvesOnInjection() {
        var state = SessionState()
        state = SessionReducer.reduce(
            state,
            SessionReducer.Intent.UserQueuedInterrupt("q-1", "stop and check"),
        )
        assertTrue(state.transcript.single().queued)
        assertEquals(1, state.pendingInterrupts.size)

        state = state.reduce(
            ServerEvent.SoftInterruptInjected("stop and check", "user", "B", 0, listOf("q-1")),
        )
        assertFalse(state.transcript.single().queued)
        assertTrue(state.pendingInterrupts.isEmpty())
    }

    @Test
    fun injectedInterruptFromAnotherClientAppears() {
        var state = SessionState()
        state = state.reduce(
            ServerEvent.SoftInterruptInjected("from desktop", "user", "A", null, emptyList()),
        )
        assertEquals("from desktop", state.transcript.single().text)
        assertEquals(Role.User, state.transcript.single().role)
    }

    @Test
    fun doneFinishesStreamingAndClearsProcessing() {
        var state = SessionState(isProcessing = true)
        state = state.reduce(ServerEvent.TextDelta("answer"))
        state = state.reduce(ServerEvent.Done(3))
        assertFalse(state.isProcessing)
        assertFalse(state.transcript.single().streaming)
    }

    @Test
    fun emptyStreamingStubIsDropped() {
        var state = SessionState(isProcessing = true)
        state = state.reduce(ServerEvent.ReasoningDelta("thinking"))
        state = state.reduce(ServerEvent.MessageEnd)
        state = state.reduce(ServerEvent.Done(1))
        assertEquals(1, state.transcript.size)
        assertEquals("thinking", state.transcript.single().reasoning)
    }

    @Test
    fun historyReplacesTranscriptWholesale() {
        var state = SessionState()
        state = state.reduce(ServerEvent.TextDelta("stale"))
        val history = ServerEvent.History(
            dev.jcode.mobile.data.remote.HistoryPayload(
                id = 1,
                sessionId = "session_owl_2",
                messages = listOf(
                    dev.jcode.mobile.data.remote.HistoryMessage("user", "hi", emptyList(), null),
                    dev.jcode.mobile.data.remote.HistoryMessage("assistant", "hello", emptyList(), null),
                ),
                messagesBaseCount = null,
                providerName = "anthropic",
                providerModel = "claude-x",
                availableModels = listOf("claude-x"),
                totalTokens = 10L to 20L,
                allSessions = listOf("session_owl_2"),
                serverVersion = "1.0",
                serverName = "blazing",
                displayTitle = "Owl",
                reasoningEffort = "low",
                statusDetail = null,
                wasInterrupted = false,
            ),
        )
        state = state.reduce(history)
        assertEquals(2, state.transcript.size)
        assertEquals("hello", state.transcript[1].text)
        assertEquals("session_owl_2", state.sessionId)
        assertEquals("Owl", state.sessionTitle)
        assertEquals(10L, state.tokenInput)
        assertEquals(20L, state.tokenOutput)
    }

    @Test
    fun errorStopsProcessingAndSurfacesBanner() {
        var state = SessionState(isProcessing = true)
        state = state.reduce(ServerEvent.TextDelta("x"))
        state = state.reduce(ServerEvent.ErrorEvent(9, "boom", null))
        assertFalse(state.isProcessing)
        assertEquals("boom", state.errorBanner)
        assertFalse(state.transcript.single().streaming)
    }

    @Test
    fun reconnectPhaseFinishesStreaming() {
        var state = SessionState(isProcessing = true)
        state = state.reduce(ServerEvent.TextDelta("x"))
        state = SessionReducer.withPhase(state, ConnectionPhase.Reconnecting)
        assertFalse(state.transcript.single().streaming)
        assertFalse(state.isProcessing)
    }

    @Test
    fun optimisticUserMessageThenDone() {
        var state = SessionState()
        state = SessionReducer.reduce(state, SessionReducer.Intent.UserSentMessage("do it"))
        assertTrue(state.isProcessing)
        assertEquals(Role.User, state.transcript.single().role)
        state = state.reduce(ServerEvent.Done(1))
        assertFalse(state.isProcessing)
        assertEquals(1, state.transcript.size)
    }

    @Test
    fun cancelledQueuedInterruptsRemoveOptimisticBubbles() {
        var state = SessionState()
        state = SessionReducer.reduce(state, SessionReducer.Intent.UserQueuedInterrupt("q-2", "a"))
        state = SessionReducer.reduce(state, SessionReducer.Intent.CancelledQueuedInterrupts)
        assertTrue(state.transcript.isEmpty())
        assertTrue(state.pendingInterrupts.isEmpty())
    }

    @Test
    fun rendersHistoryToolData() {
        val payload = dev.jcode.mobile.data.remote.HistoryPayload(
            id = 1,
            sessionId = "s",
            messages = listOf(
                dev.jcode.mobile.data.remote.HistoryMessage(
                    role = "assistant",
                    content = "",
                    toolCalls = emptyList(),
                    toolData = dev.jcode.mobile.data.remote.ToolCallRecord(
                        id = "t1",
                        name = "bash",
                        input = "ls",
                        output = "a.rs",
                        error = null,
                    ),
                ),
            ),
            messagesBaseCount = null,
            providerName = null,
            providerModel = null,
            availableModels = emptyList(),
            totalTokens = null,
            allSessions = emptyList(),
            serverVersion = null,
            serverName = null,
            displayTitle = null,
            reasoningEffort = null,
            statusDetail = null,
            wasInterrupted = false,
        )
        val state = SessionState().reduce(ServerEvent.History(payload))
        val entry: Entry = state.transcript.single()
        assertEquals("bash", entry.tools.single().name)
        assertEquals(ToolStatus.Succeeded, entry.tools.single().status)
    }

    @Test
    fun cancellingOneQueuedKeepsTheOthers() {
        var state = SessionState()
        state = SessionReducer.reduce(state, SessionReducer.Intent.UserQueuedInterrupt("q-1", "first"))
        state = SessionReducer.reduce(state, SessionReducer.Intent.UserQueuedInterrupt("q-2", "second"))
        assertEquals(2, state.pendingInterrupts.size)

        state = SessionReducer.reduce(state, SessionReducer.Intent.CancelledQueuedInterrupt("q-1"))
        assertEquals(listOf("second"), state.pendingInterrupts.map { it.text })
        assertEquals(listOf("second"), state.transcript.map { it.text })
    }

    @Test
    fun serverCancellationRemovesTheBubble() {
        var state = SessionState()
        state = SessionReducer.reduce(state, SessionReducer.Intent.UserQueuedInterrupt("q-1", "first"))
        state = state.reduce(
            ServerEvent.SoftInterruptsCancelled(9, cancelled = listOf("q-1"), notQueued = emptyList()),
        )
        assertTrue(state.pendingInterrupts.isEmpty())
        assertTrue(state.transcript.isEmpty())
    }
}
