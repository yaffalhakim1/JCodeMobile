package dev.jcode.mobile.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jcode.mobile.data.chat.PermissionView
import dev.jcode.mobile.data.chat.TodoView
import dev.jcode.mobile.data.chat.ToolCall
import dev.jcode.mobile.data.chat.ToolStatus
import dev.jcode.mobile.ui.markdown.MarkdownText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val questionTools = setOf(
    "question",
    "ask_user",
    "request_user_input",
    "user_input",
    "choice",
    "confirm",
)

fun isQuestionTool(name: String): Boolean = name.lowercase() in questionTools

fun isInlineResponseTool(name: String): Boolean = name.lowercase() == "user_response"

@Composable
fun TodoPanel(todos: List<TodoView>, modifier: Modifier = Modifier) {
    if (todos.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(todos.size <= 8) }
    val done = todos.count { it.isDone }
    val active = todos.firstOrNull { it.isActive } ?: todos.firstOrNull { !it.isDone && !it.isCancelled }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Plan",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$done/${todos.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                if (!expanded && active != null) {
                    Text(
                        active.content,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse plan" else "Expand plan",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            LinearProgressIndicator(
                progress = { if (todos.isEmpty()) 0f else done.toFloat() / todos.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            AnimatedVisibility(expanded) {
                Column(
                    Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    var lastGroup: String? = null
                    todos.forEach { todo ->
                        if (todo.group != null && todo.group != lastGroup) {
                            Text(
                                todo.group,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            lastGroup = todo.group
                        }
                        TodoRow(todo)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoRow(todo: TodoView) {
    val color = when {
        todo.isDone -> MaterialTheme.colorScheme.primary
        todo.isActive -> MaterialTheme.colorScheme.tertiary
        todo.isCancelled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val mark = when {
        todo.isDone -> "✓"
        todo.isActive -> "◐"
        todo.isCancelled -> "×"
        else -> "○"
    }
    Row(verticalAlignment = Alignment.Top) {
        Text(mark, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(20.dp))
        Text(
            todo.content,
            style = MaterialTheme.typography.bodySmall,
            color = if (todo.isCancelled) color else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (todo.isDone || todo.isCancelled) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        if (todo.priority == "high") {
            Text(
                "high",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

data class QuestionSpec(
    val prompt: String,
    val title: String?,
    val options: List<String>,
)

fun parseQuestion(tool: ToolCall): QuestionSpec? {
    val obj = runCatching {
        Json { ignoreUnknownKeys = true; isLenient = true }
            .parseToJsonElement(tool.input) as? JsonObject
    }.getOrNull() ?: return null

    fun field(vararg names: String): String? {
        for (name in names) {
            val value = (obj[name] as? JsonPrimitive)?.contentOrNull
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    val prompt = field("question", "prompt", "message", "text", "content")
        ?: tool.input.takeIf { it.isNotBlank() && !it.trim().startsWith("{") }
        ?: return null
    val options = mutableListOf<String>()
    for (key in listOf("options", "choices", "answers")) {
        val array = obj[key] as? JsonArray ?: continue
        array.forEach { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }?.let(options::add)
                is JsonObject -> {
                    val label = (element["label"] as? JsonPrimitive)?.contentOrNull
                        ?: (element["value"] as? JsonPrimitive)?.contentOrNull
                        ?: (element["text"] as? JsonPrimitive)?.contentOrNull
                    label?.takeIf { it.isNotBlank() }?.let(options::add)
                }
                else -> Unit
            }
        }
    }
    return QuestionSpec(
        prompt = prompt,
        title = field("title", "header"),
        options = options,
    )
}

@Composable
fun QuestionCard(
    tool: ToolCall,
    answered: Boolean,
    onSubmit: (String) -> Unit,
    onDeny: () -> Unit,
) {
    val spec = remember(tool.input) { parseQuestion(tool) }
    var text by rememberSaveable(tool.id) { mutableStateOf("") }
    var selected by rememberSaveable(tool.id) { mutableStateOf("") }

    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                spec?.title ?: "Question",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                spec?.prompt ?: tool.input.ifBlank { "The agent is waiting for your answer." },
                style = MaterialTheme.typography.bodyMedium,
            )

            if (answered || tool.status == ToolStatus.Succeeded || tool.status == ToolStatus.Failed) {
                Text(
                    "Answered",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (spec != null && spec.options.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    spec.options.forEach { option ->
                        val chosen = option == selected
                        Surface(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = option },
                            color = if (chosen) {
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                option,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { if (selected.isNotBlank()) onSubmit(selected) },
                            enabled = selected.isNotBlank(),
                        ) { Text("Answer") }
                        TextButton(onClick = onDeny) { Text("Decline") }
                    }
                }
            } else {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Your answer…") },
                    maxLines = 5,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (text.isNotBlank()) onSubmit(text.trim()) },
                        enabled = text.isNotBlank(),
                    ) { Text("Send answer") }
                    TextButton(onClick = onDeny) { Text("Decline") }
                }
            }
        }
    }
}

@Composable
fun InlineResponseCard(tool: ToolCall) {
    val obj = remember(tool.input) {
        runCatching {
            Json { ignoreUnknownKeys = true; isLenient = true }
                .parseToJsonElement(tool.input) as? JsonObject
        }.getOrNull()
    }
    val title = (obj?.get("title") as? JsonPrimitive)?.contentOrNull
    val content = (obj?.get("content") as? JsonPrimitive)?.contentOrNull
        ?: tool.output.takeIf { it.isNotBlank() }
        ?: return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!title.isNullOrBlank()) {
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        MarkdownText(content)
    }
}

@Composable
fun PermissionSheet(
    permissions: List<PermissionView>,
    onDismiss: (String) -> Unit,
    onClear: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Permissions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (permissions.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
        Text(
            "Approvals are host-side. The gateway protocol has no approve/deny frame, so this app can " +
                "show a request but cannot answer it. Decide on the PC; requests appear here when jcode " +
                "notifies them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (permissions.isEmpty()) {
            Text(
                "No permission requests.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        permissions.forEach { permission ->
            Surface(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(permission.message, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "id ${permission.id.take(16)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onDismiss(permission.id) }) { Text("Hide") }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Approve on the host", style = MaterialTheme.typography.titleSmall)
        Text(
            "Run this in a terminal on the PC, or tap /permissions in the jcode TUI.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            Modifier
                .fillMaxWidth()
                .clickable { clipboard.setText(AnnotatedString("jcode permissions")) },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                "jcode permissions",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(10.dp),
            )
        }
        Text(
            "Tap to copy",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

