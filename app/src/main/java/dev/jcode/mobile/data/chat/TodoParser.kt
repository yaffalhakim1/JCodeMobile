package dev.jcode.mobile.data.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Extracts the plan from a `todo` tool call.
 *
 * The tool accepts a full `todos` array or incremental `add`/`update`/`remove`
 * patches, and its output is the pretty-printed full list. So the input is
 * tried first (a full list), then the output (always authoritative).
 */
object TodoParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(input: String, output: String): List<TodoView>? =
        parseDocument(input) ?: parseDocument(output)

    private fun parseDocument(text: String): List<TodoView>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val element = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
        when (element) {
            is JsonArray -> return element.mapNotNull(::toView)
            is JsonObject -> {
                val todos = element["todos"] as? JsonArray ?: return null
                return todos.mapNotNull(::toView)
            }
            else -> Unit
        }

        val slice = firstArray(trimmed) ?: return null
        val array = runCatching { json.parseToJsonElement(slice) as? JsonArray }.getOrNull()
            ?: return null
        return array.mapNotNull(::toView)
    }

    private fun toView(element: kotlinx.serialization.json.JsonElement): TodoView? {
        val obj = element as? JsonObject ?: return null
        val content = obj.str("content")?.takeIf { it.isNotBlank() } ?: return null
        return TodoView(
            id = obj.str("id") ?: content.take(24),
            content = content,
            status = obj.str("status") ?: "pending",
            priority = obj.str("priority") ?: "medium",
            group = obj.str("group") ?: obj.str("lane"),
        )
    }

    /** First balanced JSON array in `text`, ignoring brackets inside strings. */
    private fun firstArray(text: String): String? {
        val start = text.indexOf('[')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun JsonObject.str(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.contentOrNull
    }
}
