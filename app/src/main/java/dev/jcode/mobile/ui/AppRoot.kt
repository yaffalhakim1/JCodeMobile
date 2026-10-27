package dev.jcode.mobile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jcode.mobile.data.JCodeRepository
import dev.jcode.mobile.ui.chat.ChatScreen
import dev.jcode.mobile.ui.connect.ConnectScreen
import dev.jcode.mobile.ui.sessions.SessionsScreen
import dev.jcode.mobile.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private enum class Screen {
    Sessions,
    Chat,
    Settings,
    Connect,
}

@Composable
fun AppRoot(repository: JCodeRepository) {
    val settings by repository.settings.collectAsStateWithLifecycle()
    val state by repository.state.collectAsStateWithLifecycle()
    val profile by repository.activeProfile.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var ready by remember { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf(Screen.Sessions.name) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        repository.bootstrap()
        ready = true
    }

    BackHandler(enabled = screen != Screen.Sessions.name) {
        screen = Screen.Sessions.name
    }

    if (!ready) {
        Splash()
        return
    }

    if (settings.servers.isEmpty()) {
        ConnectScreen(
            repository = repository,
            settings = settings,
            canGoBack = false,
            onBack = {},
            onPaired = { screen = Screen.Sessions.name },
        )
        return
    }

    when (Screen.valueOf(screen)) {
        Screen.Connect -> ConnectScreen(
            repository = repository,
            settings = settings,
            canGoBack = true,
            onBack = { screen = Screen.Sessions.name },
            onPaired = { screen = Screen.Sessions.name },
        )

        Screen.Settings -> SettingsScreen(
            repository = repository,
            settings = settings,
            state = state,
            profile = profile,
            onBack = { screen = Screen.Sessions.name },
            onAddMachine = { screen = Screen.Connect.name },
        )

        Screen.Chat -> ChatScreen(
            repository = repository,
            state = state,
            profile = profile,
            onBack = { screen = Screen.Sessions.name },
            onOpenConnect = { screen = Screen.Connect.name },
            onOpenSettings = { screen = Screen.Settings.name },
        )

        Screen.Sessions -> SessionsScreen(
            repository = repository,
            state = state,
            recentProjects = profile?.recentProjects.orEmpty(),
            onOpenSession = { id ->
                repository.resumeSession(id)
                screen = Screen.Chat.name
            },
            onNewSession = { workingDir ->
                scope.launch {
                    repository.newSession(workingDir)
                    screen = Screen.Chat.name
                }
            },
            onOpenSettings = { screen = Screen.Settings.name },
        )
    }
}

@Composable
private fun Splash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "J",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Connecting to your PC",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
