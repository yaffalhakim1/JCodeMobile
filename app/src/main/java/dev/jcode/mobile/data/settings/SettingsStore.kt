package dev.jcode.mobile.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.jcode.mobile.core.security.TokenCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "jcode_settings")

@Serializable
data class ServerProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val tokenSealed: String,
    val version: String? = null,
    val lastSessionId: String? = null,
    val lastSessionTitle: String? = null,
    val recentProjects: List<String> = emptyList(),
    val addedAt: Long = System.currentTimeMillis(),
) {
    val endpoint: String get() = "$host:$port"

    fun token(): String? = TokenCipher.open(tokenSealed)
}

@Serializable
data class AppSettings(
    val servers: List<ServerProfile> = emptyList(),
    val selectedId: String? = null,
    val deviceId: String = "",
    val deviceName: String = "",
    val themeMode: ThemeChoice = ThemeChoice.System,
    val themeFamily: String = "JCode",
) {
    val selected: ServerProfile? get() = servers.firstOrNull { it.id == selectedId }
}

/** What the user picked: follow the phone, or pin it. */
enum class ThemeChoice(val stored: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    /** One definition, so the picker and the app cannot disagree about the mode. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Dark -> true
        Light -> false
    }

    companion object {
        fun fromStored(stored: String?): ThemeChoice =
            entries.firstOrNull { it.stored == stored } ?: System
    }
}

class SettingsStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val servers = preferences[SERVERS]?.let { raw ->
            runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
        AppSettings(
            servers = servers,
            selectedId = preferences[SELECTED]?.takeIf { id -> servers.any { it.id == id } },
            deviceId = preferences[DEVICE_ID].orEmpty(),
            deviceName = preferences[DEVICE_NAME].orEmpty(),
            themeMode = ThemeChoice.fromStored(preferences[THEME_MODE]),
            themeFamily = preferences[THEME_FAMILY] ?: "JCode",
        )
    }

    suspend fun ensureDeviceIdentity(): AppSettings {
        var identity = AppSettings()
        context.dataStore.edit { preferences ->
            val deviceId = preferences[DEVICE_ID] ?: UUID.randomUUID().toString().also {
                preferences[DEVICE_ID] = it
            }
            val deviceName = preferences[DEVICE_NAME] ?: androidDeviceName().also {
                preferences[DEVICE_NAME] = it
            }
            val servers = preferences[SERVERS]?.let { raw ->
                runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrDefault(emptyList())
            } ?: emptyList()
            identity = AppSettings(
                servers = servers,
                selectedId = preferences[SELECTED],
                deviceId = deviceId,
                deviceName = deviceName,
            )
        }
        return identity
    }

    suspend fun upsertServer(profile: ServerProfile, select: Boolean = true) {
        context.dataStore.edit { preferences ->
            val servers = preferences[SERVERS]?.let { raw ->
                runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrDefault(emptyList())
            } ?: emptyList()
            val merged = servers.filterNot { it.id == profile.id } + profile
            preferences[SERVERS] = json.encodeToString(merged)
            if (select || preferences[SELECTED] == null) preferences[SELECTED] = profile.id
        }
    }

    suspend fun removeServer(id: String) {
        context.dataStore.edit { preferences ->
            val servers = preferences[SERVERS]?.let { raw ->
                runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrDefault(emptyList())
            } ?: emptyList()
            val remaining = servers.filterNot { it.id == id }
            preferences[SERVERS] = json.encodeToString(remaining)
            if (preferences[SELECTED] == id) {
                val next = remaining.firstOrNull()?.id
                if (next != null) {
                    preferences[SELECTED] = next
                } else {
                    preferences.remove(SELECTED)
                }
            }
        }
    }

    suspend fun selectServer(id: String) {
        context.dataStore.edit { preferences -> preferences[SELECTED] = id }
    }

    suspend fun rememberProject(serverId: String, path: String) {
        if (path.isBlank()) return
        context.dataStore.edit { preferences ->
            val servers = preferences[SERVERS]?.let { raw ->
                runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = servers.map { server ->
                if (server.id == serverId) {
                    server.copy(
                        recentProjects = (listOf(path) + server.recentProjects.filterNot { it == path })
                            .take(8),
                    )
                } else {
                    server
                }
            }
            preferences[SERVERS] = json.encodeToString(updated)
        }
    }

    suspend fun setThemeMode(choice: ThemeChoice) {
        context.dataStore.edit { preferences -> preferences[THEME_MODE] = choice.stored }
    }

    suspend fun setThemeFamily(family: String) {
        context.dataStore.edit { preferences -> preferences[THEME_FAMILY] = family }
    }

    suspend fun updateSession(serverId: String, sessionId: String?, title: String?) {
        context.dataStore.edit { preferences ->
            val servers = preferences[SERVERS]?.let { raw ->
                runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = servers.map { server ->
                if (server.id == serverId) {
                    server.copy(lastSessionId = sessionId, lastSessionTitle = title)
                } else {
                    server
                }
            }
            preferences[SERVERS] = json.encodeToString(updated)
        }
    }

    private fun androidDeviceName(): String {
        val manufacturer = android.os.Build.MANUFACTURER.orEmpty()
        val model = android.os.Build.MODEL.orEmpty()
        val name = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ")
        return name.ifBlank { "Android device" }
    }

    private companion object {
        val SERVERS = stringPreferencesKey("servers_json")
        val SELECTED = stringPreferencesKey("selected_server")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_FAMILY = stringPreferencesKey("theme_family")
    }
}
