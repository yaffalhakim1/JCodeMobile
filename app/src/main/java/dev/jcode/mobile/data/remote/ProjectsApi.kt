package dev.jcode.mobile.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request as HttpRequest

data class ProjectInfo(
    val path: String,
    val name: String,
    val sessions: Int,
    val lastUsed: Long,
)

data class ProjectList(
    val projects: List<ProjectInfo>,
    val source: String?,
)

/**
 * The gateway has no project-list route, so the host runs a small companion
 * (`tools/projects_bridge.py`) that groups jcode sessions by `working_dir`.
 * This client only reads that list; a missing bridge degrades to the manual
 * path field.
 */
object ProjectsApi {
    const val DEFAULT_PORT = 7645

    suspend fun list(target: GatewayTarget): ProjectList = withContext(Dispatchers.IO) {
        val request = HttpRequest.Builder()
            .url("http://${target.host}:$DEFAULT_PORT/projects")
            .get()
            .build()
        HttpClients.api.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GatewayException("Project bridge returned HTTP ${response.code}", response.code)
            }
            decode(body)
        }
    }

    private fun decode(body: String): ProjectList {
        val root = runCatching { Wire.json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        val array = root?.get("projects") as? JsonArray ?: JsonArray(emptyList())
        val projects = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val path = obj.string("path")
            if (path.isBlank()) return@mapNotNull null
            ProjectInfo(
                path = path,
                name = obj.string("name").ifBlank { path },
                sessions = obj.string("sessions").toIntOrNull() ?: 0,
                lastUsed = obj.string("last_used").toDoubleOrNull()?.toLong() ?: 0L,
            )
        }
        return ProjectList(projects = projects, source = root?.string("source"))
    }
}

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()