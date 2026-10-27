package dev.jcode.mobile.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.jcode.mobile.ui.theme.CodeStyle
import dev.jcode.mobile.ui.theme.Mono

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
    data class Bullets(val items: List<String>) : MdBlock
    data class Numbers(val items: List<String>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Rule : MdBlock
}

object MarkdownParser {
    fun parse(source: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val lines = source.replace("\r\n", "\n").split('\n')
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()

            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                val marker = trimmed.take(3)
                val language = trimmed.removePrefix(marker).trim().ifEmpty { null }
                index++
                val code = StringBuilder()
                while (index < lines.size && !lines[index].trim().startsWith(marker)) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[index])
                    index++
                }
                if (index < lines.size) index++
                blocks += MdBlock.Code(language, code.toString())
                continue
            }

            if (trimmed.isEmpty()) {
                index++
                continue
            }

            val heading = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
            if (heading != null) {
                blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
                index++
                continue
            }

            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                blocks += MdBlock.Rule
                index++
                continue
            }

            if (trimmed.startsWith("> ")) {
                val quote = mutableListOf<String>()
                while (index < lines.size && lines[index].trim().startsWith(">")) {
                    quote += lines[index].trim().removePrefix(">").removePrefix(" ").trimEnd()
                    index++
                }
                blocks += MdBlock.Quote(quote.joinToString("\n"))
                continue
            }

            if (isBullet(trimmed)) {
                val items = mutableListOf<String>()
                while (index < lines.size && isBullet(lines[index].trim())) {
                    items += stripBullet(lines[index].trim())
                    index++
                }
                blocks += MdBlock.Bullets(items)
                continue
            }

            if (isNumbered(trimmed)) {
                val items = mutableListOf<String>()
                while (index < lines.size && isNumbered(lines[index].trim())) {
                    items += stripNumbered(lines[index].trim())
                    index++
                }
                blocks += MdBlock.Numbers(items)
                continue
            }

            val paragraph = mutableListOf<String>()
            while (index < lines.size) {
                val candidate = lines[index]
                val candidateTrimmed = candidate.trim()
                if (candidateTrimmed.isEmpty() ||
                    candidateTrimmed.startsWith("```") ||
                    candidateTrimmed.startsWith("~~~") ||
                    Regex("^(#{1,6})\\s+").containsMatchIn(candidateTrimmed) ||
                    candidateTrimmed.startsWith("> ") ||
                    isBullet(candidateTrimmed) ||
                    isNumbered(candidateTrimmed) ||
                    candidateTrimmed == "---" ||
                    candidateTrimmed == "***" ||
                    candidateTrimmed == "___"
                ) {
                    break
                }
                paragraph += candidateTrimmed
                index++
            }
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(paragraph.joinToString(" "))
            }
        }
        return blocks
    }

    private fun isBullet(line: String): Boolean =
        line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")

    private fun stripBullet(line: String): String = line.substring(2).trim()

    private fun isNumbered(line: String): Boolean =
        Regex("^\\d+[.)]\\s+.*$").matches(line)

    private fun stripNumbered(line: String): String =
        line.replaceFirst(Regex("^\\d+[.)]\\s+"), "")
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    baseStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    SelectionContainer {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Paragraph -> InlineText(block.text, baseStyle, color)
                    is MdBlock.Heading -> InlineText(
                        block.text,
                        when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        },
                        color,
                    )
                    is MdBlock.Code -> CodeBlock(block.language, block.code)
                    is MdBlock.Bullets -> BulletList(block.items, color, baseStyle)
                    is MdBlock.Numbers -> NumberedList(block.items, color, baseStyle)
                    is MdBlock.Quote -> QuoteBlock(block.text, color, baseStyle)
                    MdBlock.Rule -> androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BulletList(items: List<String>, color: Color, style: TextStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEach { item ->
            Row(Modifier.fillMaxWidth()) {
                Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                BoxedInline(item, style, color)
            }
        }
    }
}

@Composable
private fun NumberedList(items: List<String>, color: Color, style: TextStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "${index + 1}.",
                    color = MaterialTheme.colorScheme.primary,
                    style = style,
                    modifier = Modifier.padding(end = 8.dp),
                )
                BoxedInline(item, style, color)
            }
        }
    }
}

@Composable
private fun BoxedInline(item: String, style: TextStyle, color: Color) {
    val task = Regex("^\\[( |x|X)]\\s+(.*)$").find(item)
    if (task != null) {
        val checked = task.groupValues[1].isNotBlank()
        Row {
            Text(if (checked) "☑ " else "☐ ", color = MaterialTheme.colorScheme.primary, style = style)
            InlineText(task.groupValues[2], style, color)
        }
    } else {
        InlineText(item, style, color)
    }
}

@Composable
private fun QuoteBlock(text: String, color: Color, style: TextStyle) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        InlineText(text, style.copy(color = color.copy(alpha = 0.82f)), color.copy(alpha = 0.82f))
    }
}

@Composable
private fun CodeBlock(language: String?, code: String) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                language?.uppercase() ?: "CODE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                Text("Copy", style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            code,
            style = CodeStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        )
    }
}

@Composable
fun InlineText(text: String, style: TextStyle, color: Color) {
    val annotated = remember(text, style, color) { inlineAnnotated(text, style, color) }
    Text(annotated, style = style, color = color)
}

private fun inlineAnnotated(text: String, style: TextStyle, color: Color): AnnotatedString =
    buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("`", index) -> {
                    val end = text.indexOf('`', index + 1)
                    if (end > index) {
                        withStyle(
                            SpanStyle(
                                fontFamily = Mono,
                                background = color.copy(alpha = 0.10f),
                                fontSize = style.fontSize * 0.94f,
                            ),
                        ) {
                            append(text.substring(index + 1, end))
                        }
                        index = end + 1
                    } else {
                        append(text[index])
                        index++
                    }
                }

                text.startsWith("**", index) || text.startsWith("__", index) -> {
                    val marker = text.substring(index, index + 2)
                    val end = text.indexOf(marker, index + 2)
                    if (end > index) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(text.substring(index + 2, end))
                        }
                        index = end + 2
                    } else {
                        append(text[index])
                        index++
                    }
                }

                text.startsWith("~~", index) -> {
                    val end = text.indexOf("~~", index + 2)
                    if (end > index) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(text.substring(index + 2, end))
                        }
                        index = end + 2
                    } else {
                        append(text[index])
                        index++
                    }
                }

                text[index] == '*' || text[index] == '_' -> {
                    val marker = text[index]
                    val end = text.indexOf(marker, index + 1)
                    if (end > index + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(index + 1, end))
                        }
                        index = end + 1
                    } else {
                        append(text[index])
                        index++
                    }
                }

                text[index] == '[' -> {
                    val close = text.indexOf(']', index + 1)
                    val open = if (close > 0) text.indexOf('(', close + 1) else -1
                    val end = if (open == close + 1) text.indexOf(')', open + 1) else -1
                    if (close > index && end > open) {
                        withStyle(
                            SpanStyle(
                                color = Color(0xFF6FB6FF),
                                textDecoration = TextDecoration.Underline,
                            ),
                        ) {
                            append(text.substring(index + 1, close))
                        }
                        index = end + 1
                    } else {
                        append(text[index])
                        index++
                    }
                }

                else -> {
                    append(text[index])
                    index++
                }
            }
        }
    }
