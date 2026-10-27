package dev.jcode.mobile.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jcode.mobile.data.JCodeRepository
import dev.jcode.mobile.data.chat.ConnectionPhase
import dev.jcode.mobile.data.chat.SessionState
import dev.jcode.mobile.data.remote.ProjectInfo
import dev.jcode.mobile.ui.chat.prettySessionName
import dev.jcode.mobile.ui.chat.sessionTimeLabel
import dev.jcode.mobile.ui.components.ConnectionBanner
import dev.jcode.mobile.ui.components.Glyphs
import dev.jcode.mobile.ui.components.StateMessage
import dev.jcode.mobile.ui.components.StatusDot
import dev.jcode.mobile.ui.theme.extendedColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home screen: the sessions this machine owns.
 *
 * A dedicated list, not a sheet, because it is the place a session starts and
 * the place a back gesture is expected to land.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    repository: JCodeRepository,
    state: SessionState,
    recentProjects: List<String>,
    onOpenSession: (String) -> Unit,
    onNewSession: (String?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showNewSession by rememberSaveable { mutableStateOf(false) }
    var projects by remember { mutableStateOf<List<ProjectInfo>>(emptyList()) }
    var projectsLoading by remember { mutableStateOf(false) }
    var projectsError by remember { mutableStateOf<String?>(null) }
    val current = state.sessionId

    fun loadProjects() {
        scope.launch {
            projectsLoading = true
            projectsError = null
            repository.listProjects()
                .onSuccess { projects = it }
                .onFailure {
                    projectsError = "Project bridge not reachable on this host."
                }
            projectsLoading = false
        }
    }

    LaunchedEffect(showNewSession) {
        if (showNewSession) loadProjects()
    }

    // The gateway has no session-list event, so refresh only while idle: a
    // history request during a live turn would replace the streaming transcript.
    LaunchedEffect(state.phase, state.isProcessing) {
        if (state.phase != ConnectionPhase.Connected || state.isProcessing) return@LaunchedEffect
        while (true) {
            delay(8_000)
            repository.refreshHistory()
        }
    }

    val sessions = remember(state.allSessions, current) {
        val all = if (current != null && current !in state.allSessions) {
            state.allSessions + current
        } else {
            state.allSessions
        }
        all.sortedByDescending { sessionTimestamp(it) }
    }
    val currentRow = sessions.firstOrNull { it == current }
    val others = sessions.filterNot { it == current }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("JCode", style = MaterialTheme.typography.titleLarge)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            StatusDot(state.phase, size = 6)
                            Text(
                                text = state.serverName ?: "jcode host",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { repository.refreshHistory() }) {
                        Icon(Glyphs.Refresh, contentDescription = "Refresh sessions")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Glyphs.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewSession = true },
                icon = { Icon(Glyphs.Add, contentDescription = null) },
                text = { Text("New session") },
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.phase != ConnectionPhase.Connected) {
                ConnectionBanner(
                    state = state,
                    onRetry = { repository.retry() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            if (sessions.isEmpty()) {
                StateMessage(
                    title = "No sessions yet",
                    body = "Start a session and your agent begins working on this machine. " +
                        "The heavy lifting never leaves the host.",
                    glyph = Glyphs.Sparkle,
                    actionLabel = "Start a session",
                    onAction = { showNewSession = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (currentRow != null) {
                        item(key = "header-current") {
                            SectionHeader("Current", 1)
                        }
                        item(key = "current") {
                            SessionRow(
                                id = currentRow,
                                state = state,
                                running = state.isProcessing,
                                active = true,
                                onClick = { onOpenSession(currentRow) },
                            )
                        }
                    }
                    if (others.isNotEmpty()) {
                        item(key = "header-sessions") {
                            SectionHeader("Sessions", others.size)
                        }
                        items(others, key = { it }) { id ->
                            SessionRow(
                                id = id,
                                state = state,
                                running = false,
                                active = false,
                                onClick = { onOpenSession(id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewSession) {
        ModalBottomSheet(
            onDismissRequest = { showNewSession = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            NewSessionSheet(
                recentProjects = recentProjects,
                projects = projects,
                loading = projectsLoading,
                error = projectsError,
                onRefresh = ::loadProjects,
                onStart = { path ->
                    showNewSession = false
                    onNewSession(path)
                },
                onDismiss = { showNewSession = false },
            )
        }
    }
}

@Composable
private fun NewSessionSheet(
    recentProjects: List<String>,
    projects: List<ProjectInfo>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onStart: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("New session", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Pick a project on the host, or enter a folder path. The agent reads and edits " +
                "files there. Leave it blank to use the daemon's default directory.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Projects on this host",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh, enabled = !loading) {
                Text(if (loading) "Loading…" else "Refresh")
            }
        }

        when {
            loading && projects.isEmpty() -> Text(
                text = "Looking for projects…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            projects.isEmpty() -> Text(
                text = "No project list. Run `python tools\\projects_bridge.py --port 7645` on the PC " +
                    "and tap Refresh, or enter a path below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                projects.forEach { project ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                MaterialTheme.shapes.medium,
                            )
                            .clickable { onStart(project.path) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = project.path,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = when (project.sessions) {
                                0 -> "likely"
                                1 -> "1 session"
                                else -> "${project.sessions} sessions"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            singleLine = true,
            label = { Text("Project folder") },
            placeholder = { Text("/home/you/project") },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        if (recentProjects.isNotEmpty()) {
            Text(
                text = "Recent folders",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            recentProjects.forEach { project ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            MaterialTheme.shapes.medium,
                        )
                        .clickable { path = project }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = project,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (project == path) {
                        Icon(
                            Glyphs.Check,
                            contentDescription = "Selected",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Button(
                onClick = { onStart(path.trim().ifBlank { null }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Start session")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun SessionRow(
    id: String,
    state: SessionState,
    running: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (running || active) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(
                                if (running) MaterialTheme.extendedColors.brand
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                CircleShape,
                            ),
                    )
                }
                Text(
                    text = state.titleFor(id) ?: prettySessionName(id),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = sessionTimeLabel(id),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .size(3.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
                )
                Text(
                    text = id,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Icon(
            Glyphs.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

private fun sessionTimestamp(id: String): Long =
    id.removePrefix("session_").split("_").getOrNull(1)?.toLongOrNull() ?: 0L
