package dev.jcode.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jcode.mobile.data.chat.SessionState
import dev.jcode.mobile.ui.components.Glyphs
import dev.jcode.mobile.ui.theme.CodeStyle
import dev.jcode.mobile.ui.theme.extendedColors

/**
 * Model and provider selection.
 *
 * One sheet because model, provider and effort are a single decision about who
 * answers the turn. Providers are a filter over the catalog, not a separate
 * screen: the model string already carries its route.
 */
@Composable
fun ModelsSheet(
    state: SessionState,
    onModel: (String) -> Unit,
    onEffort: (String) -> Unit,
) {
    var provider by rememberSaveable { mutableStateOf("all") }
    val providers = remember(state.availableModels) {
        state.availableModels.map(::providerOf).distinct().sorted()
    }
    val visibleModels = if (provider == "all") {
        state.availableModels
    } else {
        state.availableModels.filter { providerOf(it) == provider }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Model & provider", style = MaterialTheme.typography.titleLarge)
        Text(
            text = state.providerName ?: "Provider selected by the host",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.availableModels.isEmpty()) {
            Text(
                text = "The server did not report a model catalog.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        if (providers.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf("all") + providers).forEach { candidate ->
                    val selected = provider == candidate
                    Surface(
                        onClick = { provider = candidate },
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ) {
                        Text(
                            text = if (candidate == "all") "All providers" else candidate,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }

        visibleModels.forEach { model ->
            val active = model == state.modelName
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (active) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        MaterialTheme.shapes.medium,
                    )
                    .clickable { onModel(model) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = modelWithoutProvider(model),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = providerOf(model),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (active) {
                    Icon(
                        Glyphs.Check,
                        contentDescription = "Selected",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (state.reasoningEffort != null) {
            Spacer(Modifier.height(8.dp))
            Text("Reasoning effort", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Current: ${state.reasoningEffort}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("low", "medium", "high").forEach { effort ->
                    val selected = effort == state.reasoningEffort
                    Surface(
                        onClick = { onEffort(effort) },
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ) {
                        Text(
                            effort,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename session") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("Title") },
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(title) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

fun providerOf(model: String): String {
    val colon = model.indexOf(':')
    val slash = model.indexOf('/')
    val index = when {
        colon > 0 && slash > 0 -> minOf(colon, slash)
        colon > 0 -> colon
        slash > 0 -> slash
        else -> -1
    }
    return if (index > 0) model.substring(0, index) else "default"
}

fun modelWithoutProvider(model: String): String {
    val provider = providerOf(model)
    return if (provider == "default") model else model.substring(provider.length + 1)
}
