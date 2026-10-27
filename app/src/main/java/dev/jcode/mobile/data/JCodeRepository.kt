package dev.jcode.mobile.data

import android.content.Context
import dev.jcode.mobile.core.security.TokenCipher
import dev.jcode.mobile.data.chat.ConnectionPhase
import dev.jcode.mobile.data.chat.SessionReducer
import dev.jcode.mobile.data.chat.SessionState
import dev.jcode.mobile.data.remote.Cancel
import dev.jcode.mobile.data.remote.CancelSoftInterrupts
import dev.jcode.mobile.data.remote.ClearConversation
import dev.jcode.mobile.data.remote.Compact
import dev.jcode.mobile.data.remote.ConnectionOutput
import dev.jcode.mobile.data.remote.GatewayApi
import dev.jcode.mobile.data.remote.GatewayConnection
import dev.jcode.mobile.data.remote.GatewayException
import dev.jcode.mobile.data.remote.GatewayInput
import dev.jcode.mobile.data.remote.GatewayTarget
import dev.jcode.mobile.data.remote.GetHistory
import dev.jcode.mobile.data.remote.ProjectInfo
import dev.jcode.mobile.data.remote.ProjectsApi
import dev.jcode.mobile.data.remote.RenameSession
import dev.jcode.mobile.data.remote.ResumeSession
import dev.jcode.mobile.data.remote.SendMessage
import dev.jcode.mobile.data.remote.SetModel
import dev.jcode.mobile.data.remote.SetReasoningEffort
import dev.jcode.mobile.data.remote.SoftInterrupt
import dev.jcode.mobile.data.remote.SubmitToolResult
import dev.jcode.mobile.data.settings.AppSettings
import dev.jcode.mobile.data.settings.ServerProfile
import dev.jcode.mobile.data.settings.SettingsStore
import dev.jcode.mobile.data.settings.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Owns the single live connection and the session state derived from it.
 * Screens observe [state] and call the intent methods.
 */
class JCodeRepository(context: Context) {
    private val settingsStore = SettingsStore(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private val _activeProfile = MutableStateFlow<ServerProfile?>(null)
    val activeProfile: StateFlow<ServerProfile?> = _activeProfile.asStateFlow()

    private var connection: GatewayConnection? = null
    private var outputJob: Job? = null
    private var deviceId: String = ""
    private var deviceName: String = "Android device"

    init {
        scope.launch {
            val identity = settingsStore.ensureDeviceIdentity()
            deviceId = identity.deviceId
            deviceName = identity.deviceName
        }
        scope.launch {
            state.map { it.sessionId to it.sessionTitle }
                .distinctUntilChanged()
                .collect { (sessionId, title) ->
                    val profile = _activeProfile.value ?: return@collect
                    if (sessionId != null) {
                        settingsStore.updateSession(profile.id, sessionId, title)
                    }
                }
        }
    }

    suspend fun bootstrap() {
        val identity = settingsStore.ensureDeviceIdentity()
        deviceId = identity.deviceId
        deviceName = identity.deviceName
        identity.selected?.let { connect(it, it.lastSessionId) }
    }

    suspend fun pair(hostInput: String, portInput: String, code: String): Result<ServerProfile> = runCatching {
        val target = GatewayInput.parseEndpoint(hostInput, portInput)
            ?: throw GatewayException("Enter the host or tailnet name of this machine")
        val identity = settingsStore.ensureDeviceIdentity()
        deviceId = identity.deviceId
        deviceName = identity.deviceName
        val result = GatewayApi.pair(target, code.trim(), identity.deviceId, identity.deviceName)
        val profile = ServerProfile(
            id = UUID.randomUUID().toString(),
            name = result.serverName,
            host = target.host,
            port = target.port,
            tokenSealed = TokenCipher.seal(result.token),
            version = result.serverVersion,
        )
        settingsStore.upsertServer(profile)
        connect(profile, resumeSessionId = null)
        profile
    }

    /**
     * Pairing must not run in a screen's composition scope. Saving the server
     * removes the connect screen, which cancels that scope and used to abort
     * the first connection before it started.
     */
    fun pairAsync(
        hostInput: String,
        portInput: String,
        code: String,
        onResult: (Result<ServerProfile>) -> Unit,
    ) {
        scope.launch {
            val result = pair(hostInput, portInput, code)
            withContext(Dispatchers.Main.immediate) { onResult(result) }
        }
    }

    suspend fun connect(
        profile: ServerProfile,
        resumeSessionId: String? = profile.lastSessionId,
        workingDir: String? = null,
    ) {
        val identity = settingsStore.ensureDeviceIdentity()
        deviceId = identity.deviceId
        deviceName = identity.deviceName
        val token = profile.token()
        if (token == null) {
            _activeProfile.value = profile
            _state.value = SessionState(
                phase = ConnectionPhase.Failed,
                failureReason = "Stored token cannot be decrypted. Pair this device again.",
                errorBanner = "Stored token cannot be decrypted. Pair this device again.",
            )
            return
        }

        val previous = _state.value
        val sameServer = _activeProfile.value?.id == profile.id
        disconnectInternal()
        _activeProfile.value = profile
        _state.value = SessionState(
            phase = ConnectionPhase.Connecting,
            workingDir = workingDir,
            // Keep the sidebar populated while the socket reconnects instead of
            // flashing an empty list until history arrives.
            allSessions = if (sameServer) previous.allSessions else emptyList(),
            sessionTitles = if (sameServer) previous.sessionTitles else emptyMap(),
            serverName = if (sameServer) previous.serverName else null,
            serverVersion = if (sameServer) previous.serverVersion else null,
            availableModels = if (sameServer) previous.availableModels else emptyList(),
            providerName = if (sameServer) previous.providerName else null,
            modelName = if (sameServer) previous.modelName else null,
        )

        val conn = GatewayConnection(
            target = GatewayTarget(profile.host, profile.port),
            token = token,
            clientInstanceId = deviceId,
            scope = scope,
        )
        connection = conn
        outputJob = scope.launch {
            conn.events.collect { output ->
                when (output) {
                    is ConnectionOutput.Phase -> _state.update {
                        SessionReducer.withPhase(it, output.phase, output.reason)
                    }
                    is ConnectionOutput.Event -> _state.update {
                        SessionReducer.reduce(it, output.event)
                    }
                }
            }
        }
        conn.start(resumeSessionId, workingDir)
        settingsStore.selectServer(profile.id)
        if (!workingDir.isNullOrBlank()) settingsStore.rememberProject(profile.id, workingDir)
    }

    suspend fun selectServer(id: String) {
        val profile = settings.value.servers.firstOrNull { it.id == id } ?: return
        settingsStore.selectServer(id)
        connect(profile, profile.lastSessionId)
    }

    suspend fun setThemeMode(choice: ThemeChoice) {
        settingsStore.setThemeMode(choice)
    }

    suspend fun setThemeFamily(family: String) {
        settingsStore.setThemeFamily(family)
    }

    suspend fun removeServer(id: String) {
        val wasActive = _activeProfile.value?.id == id
        settingsStore.removeServer(id)
        if (wasActive) {
            disconnectInternal()
            _activeProfile.value = null
            _state.value = SessionState()
        }
    }

    fun disconnect() {
        disconnectInternal()
        _state.value = SessionState(phase = ConnectionPhase.Disconnected)
    }

    /**
     * The banner's Retry used to send `get_history` on a dead socket, which did
     * nothing. Reconnect when the transport is down; refresh only when live.
     */
    fun retry() {
        val profile = _activeProfile.value
        if (profile == null) {
            refreshHistory()
            return
        }
        when (_state.value.phase) {
            ConnectionPhase.Connected, ConnectionPhase.Connecting, ConnectionPhase.Reconnecting ->
                refreshHistory()

            ConnectionPhase.Disconnected, ConnectionPhase.Failed ->
                scope.launch { connect(profile, profile.lastSessionId) }
        }
    }

    fun submit(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return if (_state.value.isProcessing) queueInterrupt(trimmed) else sendMessage(trimmed)
    }

    fun sendMessage(text: String): Boolean {
        val conn = connection ?: return false
        if (_state.value.phase != ConnectionPhase.Connected) return false
        _state.update { SessionReducer.reduce(it, SessionReducer.Intent.UserSentMessage(text)) }
        val sent = conn.send { SendMessage(it, text) }
        if (!sent) {
            _state.update { it.copy(isProcessing = false, errorBanner = "Not connected") }
        }
        return sent
    }

    fun queueInterrupt(text: String): Boolean {
        val conn = connection ?: return false
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val clientId = UUID.randomUUID().toString()
        val sent = conn.send { SoftInterrupt(it, trimmed, clientId = clientId) }
        if (sent) {
            _state.update {
                SessionReducer.reduce(it, SessionReducer.Intent.UserQueuedInterrupt(clientId, trimmed))
            }
        } else {
            _state.update { it.copy(errorBanner = "Not connected") }
        }
        return sent
    }

    fun cancelQueuedInterrupt(clientId: String): Boolean {
        val conn = connection ?: return false
        val sent = conn.send { CancelSoftInterrupts(it, clientIds = listOf(clientId)) }
        if (sent) {
            _state.update {
                SessionReducer.reduce(it, SessionReducer.Intent.CancelledQueuedInterrupt(clientId))
            }
        }
        return sent
    }

    fun cancelSoftInterrupts(): Boolean {
        val conn = connection ?: return false
        val sent = conn.send { CancelSoftInterrupts(it) }
        if (sent) {
            _state.update { SessionReducer.reduce(it, SessionReducer.Intent.CancelledQueuedInterrupts) }
        }
        return sent
    }

    fun cancel() {
        connection?.send { Cancel(it) }
    }

    fun resumeSession(sessionId: String) {
        val conn = connection ?: return
        _state.update {
            it.copy(
                sessionId = sessionId,
                workingDir = null,
                transcript = emptyList(),
                isProcessing = false,
                isReasoning = false,
                errorBanner = null,
                pendingInterrupts = emptyList(),
                todos = emptyList(),
                permissions = emptyList(),
                answeredToolCalls = emptySet(),
            )
        }
        conn.send { ResumeSession(it, sessionId, deviceId) }
        conn.send { GetHistory(it) }
    }

    suspend fun newSession(workingDir: String? = null) {
        val profile = _activeProfile.value ?: return
        connect(profile, resumeSessionId = null, workingDir = workingDir)
    }

    /** Reads the host's project list from the optional bridge. */
    suspend fun listProjects(): Result<List<ProjectInfo>> = runCatching {
        val profile = _activeProfile.value ?: error("No active server")
        ProjectsApi.list(GatewayTarget(profile.host, profile.port)).projects
    }

    fun rename(title: String) {
        connection?.send { RenameSession(it, title.ifBlank { null }) }
    }

    fun clearConversation() {
        _state.update { SessionReducer.reduce(it, SessionReducer.Intent.ClearedConversation) }
        connection?.send { ClearConversation(it) }
    }

    fun compact() {
        connection?.send { Compact(it) }
    }

    /** Answers a client-rendered tool call such as a question or confirmation. */
    fun submitToolResult(callId: String, output: String): Boolean {
        val conn = connection ?: return false
        val sent = conn.send { SubmitToolResult(it, callId, output) }
        if (sent) {
            _state.update { SessionReducer.reduce(it, SessionReducer.Intent.AnswerTool(callId)) }
        }
        return sent
    }

    fun denyTool(callId: String, reason: String = "Denied by the user on mobile"): Boolean {
        val conn = connection ?: return false
        val sent = conn.send { SubmitToolResult(it, callId, "", reason) }
        if (sent) {
            _state.update { SessionReducer.reduce(it, SessionReducer.Intent.AnswerTool(callId)) }
        }
        return sent
    }

    fun dismissPermission(id: String) {
        _state.update { SessionReducer.reduce(it, SessionReducer.Intent.DismissPermission(id)) }
    }

    fun clearPermissions() {
        _state.update { SessionReducer.reduce(it, SessionReducer.Intent.ClearPermissions) }
    }

    fun setModel(model: String) {
        connection?.send { SetModel(it, model) }
    }

    fun setReasoningEffort(effort: String) {
        connection?.send { SetReasoningEffort(it, effort) }
    }

    fun refreshHistory() {
        connection?.send { GetHistory(it) }
    }

    fun dismissError() {
        _state.update { SessionReducer.reduce(it, SessionReducer.Intent.DismissError) }
    }

    fun dismissNotice(id: String) {
        _state.update { SessionReducer.reduce(it, SessionReducer.Intent.DismissNotice(id)) }
    }

    fun shutdown() {
        disconnectInternal()
        scope.cancel()
    }

    private fun disconnectInternal() {
        outputJob?.cancel()
        outputJob = null
        connection?.stop()
        connection = null
    }
}
