package dev.jcode.mobile.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request as HttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

data class GatewayTarget(val host: String, val port: Int = DEFAULT_PORT) {
    val httpBase: String get() = "http://$host:$port"
    val wsUrl: String get() = "ws://$host:$port/ws"

    companion object {
        const val DEFAULT_PORT = 7643
    }
}

data class PairInvite(val host: String, val port: Int, val code: String)

data class PairResult(val token: String, val serverName: String, val serverVersion: String)

data class HealthInfo(val version: String, val gateway: Boolean)

class GatewayException(message: String, val statusCode: Int? = null) : Exception(message)

object HttpClients {
    val api: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val webSocket: OkHttpClient = api.newBuilder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
}

object GatewayInput {
    /** Parses a full `jcode://pair?...` URI from a QR code. */
    fun parsePairUri(raw: String): PairInvite? {
        val text = raw.trim()
        if (!text.startsWith("jcode://", ignoreCase = true)) return null
        return runCatching {
            val uri = URI(text)
            val query = uri.rawQuery.orEmpty().split("&").mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) null else part.substring(0, index) to part.substring(index + 1)
            }.toMap()
            val host = query["host"]?.takeIf { it.isNotBlank() } ?: return null
            val port = query["port"]?.toIntOrNull() ?: GatewayTarget.DEFAULT_PORT
            val code = query["code"]?.takeIf { it.isNotBlank() } ?: return null
            PairInvite(host, port, code)
        }.getOrNull()
    }

    /**
     * Accepts `host`, `host:port`, `http://host:port`, `ws://host:port`, or a
     * pasted `jcode://pair` URI. Returns the host and port the user meant.
     */
    fun parseEndpoint(rawHost: String, rawPort: String): GatewayTarget? {
        val hostText = rawHost.trim()
        if (hostText.isEmpty()) return null
        parsePairUri(hostText)?.let { return GatewayTarget(it.host, it.port) }

        val fallbackPort = rawPort.trim().toIntOrNull() ?: GatewayTarget.DEFAULT_PORT
        if (hostText.contains("://")) {
            return runCatching {
                val uri = URI(hostText)
                val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
                val port = if (uri.port > 0) uri.port else fallbackPort
                GatewayTarget(host, port)
            }.getOrNull()
        }

        // Bare IPv6 in brackets, optionally with a port.
        if (hostText.startsWith("[")) {
            val close = hostText.indexOf(']')
            if (close > 0) {
                val host = hostText.substring(1, close)
                val port = hostText.substring(close + 1).removePrefix(":").toIntOrNull() ?: fallbackPort
                return GatewayTarget(host, port)
            }
        }

        // Bare host with a numeric trailing port.
        val lastColon = hostText.lastIndexOf(':')
        if (lastColon > 0 && hostText.indexOf(':') == lastColon) {
            val maybePort = hostText.substring(lastColon + 1).toIntOrNull()
            if (maybePort != null) {
                return GatewayTarget(hostText.substring(0, lastColon), maybePort)
            }
        }

        return GatewayTarget(hostText, fallbackPort)
    }
}

object GatewayApi {
    suspend fun health(target: GatewayTarget): HealthInfo = withContext(Dispatchers.IO) {
        val request = HttpRequest.Builder()
            .url("${target.httpBase}/health")
            .get()
            .build()
        HttpClients.api.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GatewayException(httpMessage(response.code, body), response.code)
            }
            val json = runCatching { Wire.json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject }
                .getOrNull()
            HealthInfo(
                version = json?.get("version")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                    ?: "unknown",
                gateway = json?.get("gateway")?.let {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
                } ?: false,
            )
        }
    }

    suspend fun pair(
        target: GatewayTarget,
        code: String,
        deviceId: String,
        deviceName: String,
    ): PairResult = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("code", code)
            put("device_id", deviceId)
            put("device_name", deviceName)
        }.toString()
        val request = HttpRequest.Builder()
            .url("${target.httpBase}/pair")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        HttpClients.api.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching {
                Wire.json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
            }.getOrNull()
            fun field(name: String): String? =
                (json?.get(name) as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401 -> "Invalid or expired pairing code. Run `jcode pair` again."
                    else -> field("error") ?: "Pairing failed (HTTP ${response.code})"
                }
                throw GatewayException(message, response.code)
            }
            val token = field("token")?.takeIf { it.isNotBlank() }
                ?: throw GatewayException("Server returned no token")
            PairResult(
                token = token,
                serverName = field("server_name") ?: "jcode",
                serverVersion = field("server_version") ?: "unknown",
            )
        }
    }

    private fun httpMessage(code: Int, body: String): String {
        val json = runCatching {
            Wire.json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
        }.getOrNull()
        val error = (json?.get("error") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        return error ?: "HTTP $code"
    }
}
