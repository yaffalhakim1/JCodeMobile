package dev.jcode.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jcode.mobile.data.chat.ConnectionPhase
import dev.jcode.mobile.data.chat.Notice
import dev.jcode.mobile.data.chat.SessionState

fun phaseColor(phase: ConnectionPhase): Color = when (phase) {
    ConnectionPhase.Connected -> Color(0xFF4DD9A6)
    ConnectionPhase.Connecting, ConnectionPhase.Reconnecting -> Color(0xFFFFC857)
    ConnectionPhase.Failed -> Color(0xFFFF6B6B)
    ConnectionPhase.Disconnected -> Color(0xFF7C8A84)
}

fun phaseLabel(phase: ConnectionPhase): String = when (phase) {
    ConnectionPhase.Connected -> "Connected"
    ConnectionPhase.Connecting -> "Connecting"
    ConnectionPhase.Reconnecting -> "Reconnecting"
    ConnectionPhase.Failed -> "Offline"
    ConnectionPhase.Disconnected -> "Disconnected"
}

@Composable
fun StatusDot(phase: ConnectionPhase, size: Int = 9, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size.dp)
            .background(phaseColor(phase), CircleShape),
    )
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.error,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text("Dismiss", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun NoticeRow(notice: Notice, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val accent = when (notice.kind) {
        Notice.Kind.Notification -> MaterialTheme.colorScheme.secondary
        Notice.Kind.Compaction -> MaterialTheme.colorScheme.tertiary
        Notice.Kind.Info -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(notice.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text("×", style = MaterialTheme.typography.titleMedium, color = accent)
            }
        }
    }
}

@Composable
fun ConnectionBanner(state: SessionState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (state.phase == ConnectionPhase.Connected) return
    val color = phaseColor(state.phase)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(state.phase)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(phaseLabel(state.phase), style = MaterialTheme.typography.titleSmall, color = color)
                val detail = when (state.phase) {
                    ConnectionPhase.Reconnecting -> "Retrying with backoff. Your turn keeps running on the host."
                    ConnectionPhase.Failed -> state.failureReason ?: "Cannot reach the server."
                    ConnectionPhase.Connecting -> "Opening the gateway socket."
                    else -> "Not connected."
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.phase == ConnectionPhase.Failed) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(4.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun StateMessage(
    title: String,
    body: String,
    glyph: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (glyph != null) {
            Icon(
                imageVector = glyph,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(4.dp))
            Button(onClick = onAction, shape = MaterialTheme.shapes.large) { Text(actionLabel) }
        }
    }
}
