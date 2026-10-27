package dev.jcode.mobile.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jcode.mobile.data.JCodeRepository
import dev.jcode.mobile.data.chat.ConnectionPhase
import dev.jcode.mobile.data.chat.Entry
import dev.jcode.mobile.data.chat.Role
import dev.jcode.mobile.data.chat.SessionState
import dev.jcode.mobile.data.chat.ToolCall
import dev.jcode.mobile.data.chat.ToolStatus
import dev.jcode.mobile.data.settings.ServerProfile
import dev.jcode.mobile.ui.components.ConnectionBanner
import dev.jcode.mobile.ui.components.ErrorBanner
import dev.jcode.mobile.ui.components.Glyphs
import dev.jcode.mobile.ui.components.NoticeRow
import dev.jcode.mobile.ui.components.StatusDot
import dev.jcode.mobile.ui.markdown.MarkdownText
import dev.jcode.mobile.ui.theme.CodeStyle
import dev.jcode.mobile.ui.theme.extendedColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repository: JCodeRepository,
    state: SessionState,
    profile: ServerProfile?,
    onBack: () -> Unit,
    onOpenConnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showPermissions by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    // WhatsApp-style: newest at the bottom, list inverted so item 0 is the tail.
    val newestFirst = remember(state.transcript) { state.transcript.asReversed() }
    val atBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex <= 0 && listState.firstVisibleItemScrollOffset < 24
        }
    }

    fun jumpToLatest() {
        scope.launch { listState.animateScrollToItem(0) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ChatTopBar(
                state = state,
                profile = profile,
                onBack = onBack,
                onModels = { showModels = true },
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it },
                onRename = { showRename = true },
                onRefresh = { repository.refreshHistory() },
                onCompact = { repository.compact() },
                onClear = { repository.clearConversation() },
                onPermissions = { showPermissions = true },
                onAddMachine = onOpenConnect,
                onSettings = onOpenSettings,
                onDisconnect = { repository.disconnect() },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                if (state.todos.isNotEmpty()) {
                    TodoPanel(
                        todos = state.todos,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                ConnectionBanner(
                    state = state,
                    onRetry = { repository.refreshHistory() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                state.errorBanner?.let { message ->
                    ErrorBanner(
                        message = message,
                        onDismiss = { repository.dismissError() },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                state.notices.lastOrNull()?.let { notice ->
                    NoticeRow(
                        notice = notice,
                        onDismiss = { repository.dismissNotice(notice.id) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                Composer(
                    state = state,
                    onOpenModels = { showModels = true },
                    onSubmit = { text ->
                        if (repository.submit(text)) jumpToLatest()
                    },
                    onStop = { repository.cancel() },
                    onCancelQueued = { clientId -> repository.cancelQueuedInterrupt(clientId) },
                    onCancelAllQueued = { repository.cancelSoftInterrupts() },
                )
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.transcript.isEmpty()) {
                EmptyChat(state)
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.isProcessing) {
                        item(key = "streaming-footer") {
                            StreamingFooter(state)
                        }
                    }
                    items(newestFirst, key = { it.id }) { entry ->
                        EntryRow(
                            entry = entry,
                            answeredTools = state.answeredToolCalls,
                            onSubmitTool = { callId, answer -> repository.submitToolResult(callId, answer) },
                            onDenyTool = { callId -> repository.denyTool(callId) },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !atBottom,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = ::jumpToLatest,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Icon(Glyphs.ArrowDown, contentDescription = "Jump to latest")
                    }
                }
            }
        }
    }

    if (showModels) {
        ModalBottomSheet(
            onDismissRequest = { showModels = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ModelsSheet(
                state = state,
                onModel = { model -> repository.setModel(model); showModels = false },
                onEffort = { effort -> repository.setReasoningEffort(effort); showModels = false },
            )
        }
    }

    if (showPermissions) {
        ModalBottomSheet(
            onDismissRequest = { showPermissions = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            PermissionSheet(
                permissions = state.permissions,
                onDismiss = { repository.dismissPermission(it) },
                onClear = { repository.clearPermissions() },
            )
        }
    }

    if (showRename) {
        RenameDialog(
            current = state.sessionTitle.orEmpty(),
            onDismiss = { showRename = false },
            onConfirm = { title -> repository.rename(title); showRename = false },
        )
    }

    LaunchedEffect(state.sessionId) {
        // A different session starts at its newest message.
        listState.scrollToItem(0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    state: SessionState,
    profile: ServerProfile?,
    onBack: () -> Unit,
    onModels: () -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onRename: () -> Unit,
    onRefresh: () -> Unit,
    onCompact: () -> Unit,
    onClear: () -> Unit,
    onPermissions: () -> Unit,
    onAddMachine: () -> Unit,
    onSettings: () -> Unit,
    onDisconnect: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Glyphs.Back, contentDescription = "Back to sessions")
            }
        },
        title = {
            Column {
                Text(
                    text = state.sessionTitle ?: prettySessionName(state.sessionId),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    StatusDot(state.phase, size = 6)
                    Text(
                        text = buildString {
                            state.workingDir?.let { append("${projectLabel(it)} · ") }
                            append(profile?.name ?: state.serverName ?: "jcode")
                            if (state.workingDir == null) {
                                state.serverVersion?.let { append(" · $it") }
                            }
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        actions = {
            if (state.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp).size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.extendedColors.brand,
                )
            }
            Surface(
                onClick = onModels,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 2.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Glyphs.Tune, contentDescription = null, modifier = Modifier.size(13.dp))
                    Text(
                        text = shortModel(state.modelName),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 96.dp),
                    )
                }
            }
            Box {
                IconButton(onClick = { onMenuOpenChange(true) }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                    DropdownMenuItem(
                        text = { Text("Rename session") },
                        onClick = { onMenuOpenChange(false); onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Refresh history") },
                        onClick = { onMenuOpenChange(false); onRefresh() },
                    )
                    DropdownMenuItem(
                        text = { Text("Compact context") },
                        onClick = { onMenuOpenChange(false); onCompact() },
                    )
                    DropdownMenuItem(
                        text = { Text("Permissions" + if (state.permissions.isEmpty()) "" else " (${state.permissions.size})") },
                        onClick = { onMenuOpenChange(false); onPermissions() },
                    )
                    DropdownMenuItem(
                        text = { Text("Clear conversation") },
                        onClick = { onMenuOpenChange(false); onClear() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Add machine") },
                        onClick = { onMenuOpenChange(false); onAddMachine() },
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = { onMenuOpenChange(false); onSettings() },
                    )
                    DropdownMenuItem(
                        text = { Text("Disconnect") },
                        onClick = { onMenuOpenChange(false); onDisconnect() },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun EntryRow(
    entry: Entry,
    answeredTools: Set<String>,
    onSubmitTool: (String, String) -> Unit,
    onDenyTool: (String) -> Unit,
) {
    when (entry.role) {
        Role.User -> UserMessage(entry)
        Role.Assistant -> AssistantRun(entry, answeredTools, onSubmitTool, onDenyTool)
        Role.System -> SystemNote(entry.text)
    }
}

/** A user turn: right-aligned and tonally filled. */
@Composable
private fun UserMessage(entry: Entry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (entry.queued) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.extendedColors.userBubble,
                    MaterialTheme.shapes.large,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entry.queued) {
                    Text(
                        text = "QUEUED · NEXT SAFE POINT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                MarkdownText(
                    text = entry.text,
                    baseStyle = MaterialTheme.typography.bodyLarge,
                    color = if (entry.queued) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.extendedColors.userBubbleText
                    },
                )
            }
        }
    }
}

/**
 * An assistant turn: one card for the activity, prose around it.
 *
 * Reasoning and tool calls fold into a single hairline-separated card, so a run
 * of ten tools reads as an activity log instead of ten panels.
 */
@Composable
private fun AssistantRun(
    entry: Entry,
    answeredTools: Set<String>,
    onSubmitTool: (String, String) -> Unit,
    onDenyTool: (String) -> Unit,
) {
    val activity = remember(entry.tools) {
        entry.tools.filterNot {
            it.name == "todo" || isInlineResponseTool(it.name) || isQuestionTool(it.name)
        }
    }
    val responses = remember(entry.tools) { entry.tools.filter { isInlineResponseTool(it.name) } }
    val questions = remember(entry.tools) { entry.tools.filter { isQuestionTool(it.name) } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (entry.reasoning.isNotBlank() || activity.isNotEmpty()) {
            AgentActivityCard {
                var needsDivider = false
                if (entry.reasoning.isNotBlank()) {
                    ReasoningRow(entry)
                    needsDivider = true
                }
                activity.forEach { tool ->
                    if (needsDivider) {
                        HorizontalDivider(
                            color = MaterialTheme.extendedColors.cardBorder.copy(alpha = 0.45f),
                        )
                    }
                    ToolRow(tool)
                    needsDivider = true
                }
            }
        }

        if (entry.streaming && entry.text.isBlank() && entry.reasoning.isBlank() && activity.isEmpty()) {
            ThinkingRow()
        }

        responses.forEach { InlineResponseCard(it) }

        questions.forEach { tool ->
            QuestionCard(
                tool = tool,
                answered = tool.id in answeredTools,
                onSubmit = { answer -> onSubmitTool(tool.id, answer) },
                onDeny = { onDenyTool(tool.id) },
            )
        }

        if (entry.text.isNotBlank()) {
            MarkdownText(
                text = entry.text + if (entry.streaming) " ▍" else "",
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun AgentActivityCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.extendedColors.cardSurface, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        content()
    }
}

@Composable
private fun DisclosureRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "disclosure",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke()
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
            Icon(
                imageVector = Glyphs.ChevronDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(16.dp).rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Box(Modifier.padding(top = 4.dp, bottom = 8.dp)) { content() }
        }
    }
}

@Composable
private fun ReasoningRow(entry: Entry) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    DisclosureRow(
        title = if (entry.streaming) "Thinking…" else "Thought process",
        expanded = expanded,
        onToggle = { expanded = !expanded },
        leading = {
            Icon(
                Glyphs.Sparkle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        },
    ) {
        MarkdownText(
            text = entry.reasoning,
            baseStyle = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThinkingRow() {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val dot = MaterialTheme.colorScheme.primary
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (index == 0) dot else dot.copy(alpha = 0.35f),
                        CircleShape,
                    ),
            )
        }
        Text(
            text = "Working",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolRow(tool: ToolCall) {
    var expanded by rememberSaveable(tool.id) { mutableStateOf(tool.status == ToolStatus.Failed) }
    val tint = when (tool.status) {
        ToolStatus.StreamingInput, ToolStatus.Running -> MaterialTheme.colorScheme.primary
        ToolStatus.Succeeded -> MaterialTheme.extendedColors.success
        ToolStatus.Failed -> MaterialTheme.colorScheme.error
    }
    DisclosureRow(
        title = toolSummary(tool),
        expanded = expanded,
        onToggle = { expanded = !expanded },
        leading = {
            when (tool.status) {
                ToolStatus.StreamingInput, ToolStatus.Running -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = tint,
                )
                ToolStatus.Succeeded -> Icon(
                    Glyphs.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = tint,
                )
                ToolStatus.Failed -> Icon(
                    Glyphs.Alert,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = tint,
                )
            }
        },
        trailing = {
            Text(
                text = when (tool.status) {
                    ToolStatus.StreamingInput -> "preparing"
                    ToolStatus.Running -> "running"
                    ToolStatus.Succeeded -> ""
                    ToolStatus.Failed -> "failed"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                color = tint,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (tool.input.isNotBlank()) {
                CodeLabel("INPUT")
                CodeSurface(tool.input, MaterialTheme.colorScheme.onSurface)
            }
            if (tool.output.isNotBlank()) {
                CodeLabel("OUTPUT")
                CodeSurface(tool.output.take(12000), MaterialTheme.colorScheme.onSurface)
            }
            tool.error?.let {
                CodeLabel("ERROR")
                CodeSurface(it, MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CodeLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CodeSurface(text: String, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.extendedColors.codeBackground,
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.extendedColors.codeBorder.copy(alpha = 0.5f),
        ),
    ) {
        Text(
            text = text,
            style = CodeStyle,
            color = color,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
        )
    }
}

@Composable
private fun SystemNote(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "SYSTEM",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun StreamingFooter(state: SessionState) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThinkingRow()
        Text(
            text = state.statusDetail
                ?: state.serverPhase?.let(::friendlyPhase)
                ?: if (state.isReasoning) "Thinking…" else "Working…",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyChat(state: SessionState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Glyphs.Sparkle,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.extendedColors.brand,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Empty session",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (state.phase == ConnectionPhase.Connected) {
                "Send the first instruction and the agent starts working on the host."
            } else {
                "Waiting for the gateway. Check that jcode is running and the gateway is enabled."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Composer(
    state: SessionState,
    onOpenModels: () -> Unit,
    onSubmit: (String) -> Unit,
    onStop: () -> Unit,
    onCancelQueued: (String) -> Unit,
    onCancelAllQueued: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val canSend = text.isNotBlank() && state.phase == ConnectionPhase.Connected
    val running = state.isProcessing

    fun submitCurrent() {
        if (!canSend) return
        onSubmit(text)
        text = ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.hasPendingInterrupts) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.pendingInterrupts.size} queued for this turn",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.pendingInterrupts.size > 1) {
                        TextButton(onClick = onCancelAllQueued) {
                            Text("Clear all", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                state.pendingInterrupts.forEach { queued ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                                MaterialTheme.shapes.small,
                            )
                            .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = queued.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onCancelQueued(queued.clientId) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Glyphs.Close,
                                contentDescription = "Remove queued message",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
        }

        if (running) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text("Steer", style = MaterialTheme.typography.labelSmall) },
                )
                Text(
                    text = "reaches this turn at the next safe point",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp, max = 160.dp),
                placeholder = {
                    Text(
                        text = if (running) "Queue a message for this turn…" else "Ask jcode…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = MaterialTheme.shapes.extraLarge,
                maxLines = 6,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = { submitCurrent() },
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    cursorColor = MaterialTheme.extendedColors.brand,
                ),
            )

            if (running) {
                FilledIconButton(
                    onClick = { submitCurrent() },
                    enabled = canSend,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(Glyphs.Send, contentDescription = "Queue prompt", modifier = Modifier.size(22.dp))
                }
                FilledIconButton(
                    onClick = onStop,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Glyphs.Stop, contentDescription = "Stop the agent", modifier = Modifier.size(22.dp))
                }
            } else {
                FilledIconButton(
                    onClick = { submitCurrent() },
                    enabled = canSend,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(Glyphs.Send, contentDescription = "Send prompt", modifier = Modifier.size(22.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(10.dp),
                    )
                    .clickable(onClick = onOpenModels)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Glyphs.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = shortModel(state.modelName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun toolSummary(tool: ToolCall): String {
    val detail = extractDetail(tool.input)
    return if (detail.isNullOrBlank()) tool.name else "${tool.name} · $detail"
}

/** Keeps a long host path readable in one line: `…/parent/project`. */
internal fun projectLabel(path: String): String {
    val clean = path.trim().trimEnd('/', '\\')
    val parts = clean.split('/', '\\').filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> clean
        parts.size <= 2 -> parts.joinToString("/")
        else -> "…/" + parts.takeLast(2).joinToString("/")
    }
}

private fun extractDetail(input: String): String? {
    val obj = runCatching {
        Json { ignoreUnknownKeys = true; isLenient = true }
            .parseToJsonElement(input) as? JsonObject
    }.getOrNull() ?: return input.takeIf { it.isNotBlank() && !it.trim().startsWith("{") }?.take(80)
    fun field(vararg names: String): String? {
        for (name in names) {
            val value = (obj[name] as? JsonPrimitive)?.contentOrNull
            if (!value.isNullOrBlank()) return value
        }
        return null
    }
    return field("command", "path", "file_path", "pattern", "query", "url", "prompt")
        ?.replace('\n', ' ')
        ?.take(80)
}

fun prettySessionName(sessionId: String?): String {
    if (sessionId.isNullOrBlank()) return "New session"
    val parts = sessionId.removePrefix("session_").split("_")
    val animal = parts.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Session"
    val stamp = parts.getOrNull(1)
    return if (stamp != null) "$animal · ${stamp.takeLast(4)}" else animal
}

fun sessionTimeLabel(sessionId: String?): String {
    if (sessionId == null) return ""
    val stamp = sessionId.removePrefix("session_").split("_").getOrNull(1)?.toLongOrNull() ?: return ""
    val delta = System.currentTimeMillis() - stamp
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        delta < minute -> "just now"
        delta < hour -> "${delta / minute}m ago"
        delta < day -> "${delta / hour}h ago"
        delta < 7 * day -> "${delta / day}d ago"
        else -> "${delta / (7 * day)}w ago"
    }
}

fun shortModel(model: String?): String {
    if (model.isNullOrBlank()) return "model"
    return model.substringAfterLast('/').substringAfterLast(':').take(22)
}

fun compactTokens(value: Long): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}.${(value % 1_000_000) / 100_000}M"
    value >= 1_000 -> "${value / 1_000}.${(value % 1_000) / 100}k"
    else -> value.toString()
}

private fun friendlyPhase(phase: String): String = when (phase) {
    "authenticating" -> "Authenticating with the provider…"
    "waiting" -> "Waiting for the provider…"
    "streaming" -> "Streaming…"
    "tool" -> "Running a tool…"
    else -> phase.replaceFirstChar { it.uppercase() } + "…"
}
