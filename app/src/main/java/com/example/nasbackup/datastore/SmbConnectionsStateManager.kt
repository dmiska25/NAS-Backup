package com.example.nasbackup.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.nasbackup.dataStore
import com.example.nasbackup.domain.SmbFileContext
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class SmbConnectionsStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore
    private val objectMapper = jacksonObjectMapper()

    private val _savedConnections = MutableStateFlow<List<SmbFileContext>>(emptyList())
    val savedConnections: StateFlow<List<SmbFileContext>> get() = _savedConnections

    companion object {
        private val SMB_CONNECTIONS_KEY = stringSetPreferencesKey("smb_connections_list")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            // Load initial connections from DataStore
            val initialSet = dataStore.data
                .map { prefs -> prefs[SMB_CONNECTIONS_KEY] ?: emptySet() }
                .first()

            _savedConnections.value = parseJsonSet(initialSet)

            // Observe further changes
            dataStore.data.map { prefs ->
                prefs[SMB_CONNECTIONS_KEY] ?: emptySet()
            }.collect { set ->
                _savedConnections.value = parseJsonSet(set)
            }
        }
    }

    suspend fun saveConnection(credentials: SmbFileContext) {
        dataStore.edit { prefs ->
            // Add to existing set
            val oldSet = prefs[SMB_CONNECTIONS_KEY] ?: emptySet()
            val newSet = oldSet + serialize(credentials)
            prefs[SMB_CONNECTIONS_KEY] = newSet
        }
    }

    private fun parseJsonSet(jsonSet: Set<String>): List<SmbFileContext> {
        return jsonSet.mapNotNull { json ->
            try {
                objectMapper.readValue<SmbFileContext>(json)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun serialize(creds: SmbFileContext): String {
        return objectMapper.writeValueAsString(creds)
    }
}
