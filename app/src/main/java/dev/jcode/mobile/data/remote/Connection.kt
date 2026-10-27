package dev.jcode.mobile.data.remote

import android.util.Log
import dev.jcode.mobile.data.chat.ConnectionPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.Request as HttpRequest
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicLong

sealed interface ConnectionOutput {
    data class Phase(val phase: ConnectionPhase, val reason: String? = null) : ConnectionOutput
    data class Event(val event: ServerEvent) : ConnectionOutput
}

private data class CloseInfo(
    val reason: String? = null,
    val unauthorized: Boolean = false,
)

/**
 * One WebSocket connection to a jcode gateway.
 *
 * Responsibilities: auth, subscribe, NDJSON decode, reconnect with capped
 * backoff, and request-id assignment. It knows nothing about UI state.
 */
class GatewayConnection(
    private val target: GatewayTarget,
    private val token: String,
    private val clientInstanceId: String,
    private val scope: CoroutineScope,
) {
    private val outputs = MutableSharedFlow<ConnectionOutput>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ConnectionOutput> = outputs.asSharedFlow()

    private val requestCounter = AtomicLong(1)

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var stopped = false

    private var targetSessionId: String? = null
    private var targetWorkingDir: String? = null
    private var expectReload = false
    private var closeReason: String? = null
    private var loopJob: Job? = null

    fun start(resumeSessionId: String?, workingDir: String? = null) {
        targetSessionId = resumeSessionId
        targetWorkingDir = workingDir
        stopped = false
        expectReload = false
        closeReason = null
        loopJob = scope.launch { loop() }
    }

    fun stop() {
        stopped = true
        socket?.cancel()
        socket = null
        loopJob?.cancel()
        loopJob = null
        outputs.tryEmit(ConnectionOutput.Phase(ConnectionPhase.Disconnected))
    }

    /** Returns false when there is no live socket. */
    fun send(build: (Long) -> Request): Boolean {
        val ws = socket ?: return false
        val request = build(requestCounter.getAndIncrement())
        return ws.send(Wire.encode(request))
    }

    private fun request(): HttpRequest = HttpRequest.Builder()
        .url(target.wsUrl)
        .header("Authorization", "Bearer $token")
        .build()

    private suspend fun loop() {
        var attempt = 0
        while (!stopped) {
            Log.d(TAG, "connecting ${target.wsUrl} attempt=$attempt")
            outputs.emit(
                ConnectionOutput.Phase(
                    if (attempt == 0) ConnectionPhase.Connecting else ConnectionPhase.Reconnecting,
                ),
            )
            val latch = CompletableDeferred<CloseInfo>()
            val ws = HttpClients.webSocket.newWebSocket(request(), listener(latch))
            socket = ws
            val close = latch.await()
            socket = null
            if (stopped) break

            if (close.unauthorized) {
                outputs.emit(
                    ConnectionOutput.Phase(
                        ConnectionPhase.Failed,
                        "This device is no longer paired with the server. Pair again.",
                    ),
                )
                return
            }
            closeReason?.let { reason ->
                outputs.emit(ConnectionOutput.Phase(ConnectionPhase.Failed, reason))
                return
            }
            if (expectReload) {
                expectReload = false
                attempt = 0
                delay(500)
                continue
            }

            attempt += 1
            val backoff = minOf(30_000L, 1_000L shl minOf(attempt - 1, 5))
            delay(backoff)
        }
    }

    private fun listener(latch: CompletableDeferred<CloseInfo>): WebSocketListener =
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "onOpen ${target.wsUrl}")
                outputs.tryEmit(ConnectionOutput.Phase(ConnectionPhase.Connected))
                val subscribeId = requestCounter.getAndIncrement()
                val subscribe = Subscribe(
                    id = subscribeId,
                    targetSessionId = targetSessionId,
                    clientInstanceId = clientInstanceId,
                    workingDir = targetWorkingDir,
                )
                val historyId = requestCounter.getAndIncrement()
                val sent = webSocket.send(Wire.encode(subscribe)) &&
                    webSocket.send(Wire.encode(GetHistory(historyId)))
                if (!sent) webSocket.cancel()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                for (line in text.lineSequence()) {
                    if (line.isBlank()) continue
                    val event = Wire.decodeEvent(line) ?: continue
                    when (event) {
                        is ServerEvent.SessionId -> targetSessionId = event.sessionId
                        is ServerEvent.Reloading -> expectReload = true
                        is ServerEvent.SessionCloseRequested -> {
                            closeReason = event.reason.ifBlank { "Server closed this session" }
                        }
                        else -> Unit
                    }
                    outputs.tryEmit(ConnectionOutput.Event(event))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "onFailure ${target.wsUrl} code=${response?.code}", t)
                if (!latch.isCompleted) {
                    latch.complete(
                        CloseInfo(
                            reason = t.message,
                            unauthorized = response?.code == 401,
                        ),
                    )
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!latch.isCompleted) latch.complete(CloseInfo())
            }
        }

    private companion object {
        const val TAG = "JCodeConn"
    }
}
