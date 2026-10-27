package dev.jcode.mobile

import dev.jcode.mobile.data.remote.HistoryPayload
import dev.jcode.mobile.data.remote.ServerEvent
import dev.jcode.mobile.data.remote.SoftInterrupt
import dev.jcode.mobile.data.remote.CancelSoftInterrupts
import dev.jcode.mobile.data.remote.Subscribe
import dev.jcode.mobile.data.remote.Wire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireTest {

    @Test
    fun subscribeOmitsNullTargetAndOptsIntoTakeover() {
        val line = Wire.encode(
            Subscribe(id = 7, targetSessionId = null, clientInstanceId = "device-1"),
        )
        assertTrue(line.contains("\"type\":\"subscribe\""))
        assertTrue(line.contains("\"id\":7"))
        assertTrue(line.contains("\"client_instance_id\":\"device-1\""))
        assertTrue(line.contains("\"continue_on_disconnect\":true"))
        assertTrue(line.contains("\"allow_session_takeover\":true"))
        assertFalse(line.contains("target_session_id"))
    }

    @Test
    fun subscribeIncludesTargetWhenResuming() {
        val line = Wire.encode(
            Subscribe(id = 8, targetSessionId = "session_fox_1", clientInstanceId = "d"),
        )
        assertTrue(line.contains("\"target_session_id\":\"session_fox_1\""))
    }

    @Test
    fun subscribeCarriesWorkingDir() {
        val line = Wire.encode(
            Subscribe(
                id = 9,
                targetSessionId = null,
                clientInstanceId = "d",
                workingDir = "/home/me/project",
            ),
        )
        assertTrue(line.contains("\"working_dir\":\"/home/me/project\""))
    }

    @Test
    fun steeringCarriesClientId() {
        assertTrue(
            Wire.encode(SoftInterrupt(3, "let me check", clientId = "q-1"))
                .contains("\"client_id\":\"q-1\""),
        )
        assertTrue(
            Wire.encode(CancelSoftInterrupts(4, listOf("q-1"))).contains("\"client_ids\":[\"q-1\"]"),
        )
    }

    @Test
    fun decodesSteeringIds() {
        val injected = Wire.decodeEvent(
            """{"type":"soft_interrupt_injected","content":"go","point":"B","client_ids":["q-1","q-2"]}""",
        ) as ServerEvent.SoftInterruptInjected
        assertEquals(listOf("q-1", "q-2"), injected.clientIds)

        val cancelled = Wire.decodeEvent(
            """{"type":"soft_interrupts_cancelled","id":5,"cancelled":["q-1"],"not_queued":[]}""",
        ) as ServerEvent.SoftInterruptsCancelled
        assertEquals(listOf("q-1"), cancelled.cancelled)
    }

    @Test
    fun decodesTextAndToolEvents() {
        assertEquals(
            ServerEvent.TextDelta("hello\nworld"),
            Wire.decodeEvent("""{"type":"text_delta","text":"hello\nworld"}"""),
        )
        assertEquals(
            ServerEvent.ToolDone("t1", "bash", "ok\n", null),
            Wire.decodeEvent("""{"type":"tool_done","id":"t1","name":"bash","output":"ok\n"}"""),
        )
        assertEquals(
            ServerEvent.ToolDone("t2", "read", "", "denied"),
            Wire.decodeEvent("""{"type":"tool_done","id":"t2","name":"read","output":"","error":"denied"}"""),
        )
    }

    @Test
    fun decodesErrorWithRetry() {
        assertEquals(
            ServerEvent.ErrorEvent(4, "rate limited", 12),
            Wire.decodeEvent("""{"type":"error","id":4,"message":"rate limited","retry_after_secs":12}"""),
        )
    }

    @Test
    fun unknownEventTypeIsNotFatal() {
        assertEquals(
            ServerEvent.Unknown("brand_new_thing"),
            Wire.decodeEvent("""{"type":"brand_new_thing","x":1}"""),
        )
    }

    @Test
    fun malformedJsonIsIgnored() {
        assertNull(Wire.decodeEvent("not json"))
        assertNull(Wire.decodeEvent("""{"no_type":true}"""))
    }

    @Test
    fun decodesHistoryPayload() {
        val event = Wire.decodeEvent(
            """
            {"type":"history","id":2,"session_id":"session_fox_1",
             "messages":[
               {"role":"user","content":"hi","tool_calls":[]},
               {"role":"assistant","content":"done","tool_calls":[],
                "tool_data":{"id":"t1","name":"bash","input":"ls","output":"a.rs","error":null}}
             ],
             "provider_name":"anthropic","provider_model":"claude-x",
             "available_models":["claude-x","gpt-y"],
             "total_tokens":[100,50],
             "all_sessions":["session_fox_1","session_owl_2"],
             "server_version":"0.99.1","server_name":"blazing",
             "display_title":"Fox work","reasoning_effort":"high","was_interrupted":false}
            """.trimIndent(),
        ) as ServerEvent.History
        val payload: HistoryPayload = event.payload
        assertEquals("session_fox_1", payload.sessionId)
        assertEquals(2, payload.messages.size)
        assertEquals("bash", payload.messages[1].toolData?.name)
        assertEquals(100L to 50L, payload.totalTokens)
        assertEquals(listOf("session_fox_1", "session_owl_2"), payload.allSessions)
        assertEquals("Fox work", payload.displayTitle)
        assertEquals("high", payload.reasoningEffort)
    }

    @Test
    fun historyToleratesMissingOptionalFields() {
        val event = Wire.decodeEvent(
            """{"type":"history","id":1,"session_id":"s","messages":[]}""",
        ) as ServerEvent.History
        assertEquals(emptyList<String>(), event.payload.allSessions)
        assertNull(event.payload.displayTitle)
        assertNull(event.payload.totalTokens)
    }
}
