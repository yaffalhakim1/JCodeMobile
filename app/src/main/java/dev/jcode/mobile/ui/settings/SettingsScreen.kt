package dev.jcode.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import dev.jcode.mobile.data.chat.SessionState
import dev.jcode.mobile.data.settings.AppSettings
import dev.jcode.mobile.data.settings.ServerProfile
import dev.jcode.mobile.data.settings.ThemeChoice
import dev.jcode.mobile.ui.chat.ModelsSheet
import dev.jcode.mobile.ui.chat.PermissionSheet
import dev.jcode.mobile.ui.chat.compactTokens
import dev.jcode.mobile.ui.chat.providerOf
import dev.jcode.mobile.ui.chat.shortModel
import dev.jcode.mobile.ui.components.Glyphs
import dev.jcode.mobile.ui.components.SectionLabel
import dev.jcode.mobile.ui.components.StatusDot
import dev.jcode.mobile.ui.components.phaseLabel
import dev.jcode.mobile.ui.theme.ThemeFamily
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: JCodeRepository,
    settings: AppSettings,
    state: SessionState,
    profile: ServerProfile?,
    onBack: () -> Unit,
    onAddMachine: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showPermissions by rememberSaveable { mutableStateOf(false) }
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    val providers = remember(state.availableModels) {
        state.availableModels.map(::providerOf).distinct().sorted()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Glyphs.Back, contentDescription = "Back")
                    }
                },
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("Machine")

            CardSurface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                MaterialTheme.shapes.medium,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (profile?.name ?: "J").take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = profile?.name ?: "No machine",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        )
                        Text(
                            text = profile?.let { "${it.host}:${it.port}" } ?: "Pair a machine to begin",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatusDot(state.phase, size = 6)
                        Text(
                            text = phaseLabel(state.phase),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.serverVersion ?: profile?.version ?: "version unknown",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch { profile?.let { repository.connect(it, it.lastSessionId) } }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = MaterialTheme.shapes.large,
                    ) { Text("Reconnect", style = MaterialTheme.typography.labelLarge) }
                    OutlinedButton(
                        onClick = { repository.disconnect() },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = MaterialTheme.shapes.large,
                    ) { Text("Disconnect", style = MaterialTheme.typography.labelLarge) }
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("Appearance")

            CardSurface {
                Text(
                    "Mode",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice.entries.forEach { choice ->
                        val selected = settings.themeMode == choice
                        Surface(
                            onClick = { scope.launch { repository.setThemeMode(choice) } },
                            shape = MaterialTheme.shapes.small,
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ) {
                            Text(
                                text = choice.name,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "Palette",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.height(8.dp))
                ThemeFamilyPicker(
                    selected = ThemeFamily.fromStored(settings.themeFamily),
                    dark = settings.themeMode.isDark(isSystemInDarkTheme()),
                    onSelect = { family -> scope.launch { repository.setThemeFamily(family.name) } },
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("Session")

            CardSurface {
                SettingRow(
                    title = "Model & provider",
                    value = shortModel(state.modelName) + " · " + (state.providerName ?: "auto"),
                    onClick = { showModels = true },
                )
                InfoRow(
                    "Providers",
                    if (providers.isEmpty()) "not reported by the host" else providers.joinToString(", "),
                )
                InfoRow(
                    "Project bridge",
                    "${profile?.host ?: "host"}:7645 · tools\\projects_bridge.py",
                )
                SettingRow(
                    title = "Permissions",
                    value = if (state.permissions.isEmpty()) "none pending" else "${state.permissions.size} pending",
                    onClick = { showPermissions = true },
                )
                InfoRow("Session", state.sessionTitle ?: state.sessionId ?: "new session")
                InfoRow("Tokens", "${compactTokens(state.tokenInput)} in · ${compactTokens(state.tokenOutput)} out")
                InfoRow("Plan", if (state.todos.isEmpty()) "no plan" else "${state.todoDone}/${state.todoTotal} steps")
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("Machines")

            CardSurface {
                settings.servers.forEach { server ->
                    val active = server.id == settings.selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { repository.selectServer(server.id) } }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            )
                            Text(
                                text = "${server.host}:${server.port}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (active) {
                            Icon(
                                Glyphs.Check,
                                contentDescription = "Active",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAddMachine,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = MaterialTheme.shapes.large,
                    ) { Text("Add machine", style = MaterialTheme.typography.labelLarge) }
                    OutlinedButton(
                        onClick = { confirmForget = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = MaterialTheme.shapes.large,
                        enabled = profile != null,
                    ) { Text("Forget current", style = MaterialTheme.typography.labelLarge) }
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("About")
            Text(
                text = "JCode for Android 0.1.0. A thin client for the jcode daemon on your PC. " +
                    "Pairing tokens are sealed with AES-256-GCM using a key held by the Android keystore. " +
                    "Traffic is plain WebSocket, so keep it on Tailscale or another private network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Revoke this device on the host with: jcode pair --revoke \"${settings.deviceName}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showModels) {
        ModalBottomSheet(
            onDismissRequest = { showModels = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ModelsSheet(
                state = state,
                onModel = { repository.setModel(it); showModels = false },
                onEffort = { repository.setReasoningEffort(it); showModels = false },
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

    val current = profile
    if (confirmForget && current != null) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget this machine?") },
            text = {
                Text(
                    "This removes the saved token from the phone. The host keeps its paired-device record " +
                        "until you revoke it with `jcode pair --revoke`.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmForget = false
                    scope.launch {
                        repository.removeServer(current.id)
                        onBack()
                    }
                }) { Text("Forget") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CardSurface(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                MaterialTheme.shapes.medium,
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Glyphs.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.34f),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.66f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
