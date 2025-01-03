package com.example.nasbackup.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.nasbackup.dataStore
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
import kotlinx.coroutines.runBlocking

@Singleton
class BackupNowStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    // Synchronously load initial values before setting up the flows
    private val initialCredentials = runBlocking { getSavedCredentialsFromDataStore() }
    private val initialBackupDirectory = runBlocking { getSavedBackupDirectoryFromDataStore() }

    // Initialize StateFlows with the initial loaded values
    private val _savedCredentials = MutableStateFlow<SmbCredentials?>(initialCredentials)
    val savedCredentials: StateFlow<SmbCredentials?> = _savedCredentials

    private val _savedBackupDirectory = MutableStateFlow<String?>(initialBackupDirectory)
    val savedBackupDirectory: StateFlow<String?> = _savedBackupDirectory

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Now we set up collectors to update these flows when DataStore changes:
        scope.launch {
            dataStore.data.map { prefs ->
                val ip = prefs[IP_KEY]
                val share = prefs[SHARE_KEY]
                val user = prefs[USER_KEY]
                val pass = prefs[PASS_KEY]
                if (ip != null && share != null && user != null && pass != null) {
                    SmbCredentials(ip, share, user, pass)
                } else {
                    null
                }
            }.collect { credentials ->
                _savedCredentials.value = credentials
            }
        }

        scope.launch {
            dataStore.data.map { prefs ->
                prefs[DIR_KEY]
            }.collect { directory ->
                _savedBackupDirectory.value = directory
            }
        }
    }

    suspend fun persistCredentials(
        ipAddress: String,
        shareName: String,
        username: String,
        password: String
    ) {
        dataStore.edit { prefs ->
            prefs[IP_KEY] = ipAddress
            prefs[SHARE_KEY] = shareName
            prefs[USER_KEY] = username
            prefs[PASS_KEY] = password
        }
        _savedCredentials.value = SmbCredentials(ipAddress, shareName, username, password)
    }

    suspend fun persistBackupDirectory(directory: String) {
        dataStore.edit { prefs ->
            prefs[DIR_KEY] = directory
        }
        _savedBackupDirectory.value = directory
    }

    private suspend fun getSavedCredentialsFromDataStore(): SmbCredentials? {
        val prefs = dataStore.data.first()
        val ip = prefs[IP_KEY]
        val share = prefs[SHARE_KEY]
        val user = prefs[USER_KEY]
        val pass = prefs[PASS_KEY]
        return if (ip != null && share != null && user != null && pass != null) {
            SmbCredentials(ip, share, user, pass)
        } else {
            null
        }
    }

    private suspend fun getSavedBackupDirectoryFromDataStore(): String? {
        val prefs = dataStore.data.first()
        return prefs[DIR_KEY]
    }

    companion object {
        private val IP_KEY = stringPreferencesKey("smb_ip")
        private val SHARE_KEY = stringPreferencesKey("smb_share")
        private val USER_KEY = stringPreferencesKey("smb_user")
        private val PASS_KEY = stringPreferencesKey("smb_password")
        private val DIR_KEY = stringPreferencesKey("smb_selected_directory")
    }
}

data class SmbCredentials(
    val ipAddress: String,
    val shareName: String,
    val username: String,
    val password: String
)
