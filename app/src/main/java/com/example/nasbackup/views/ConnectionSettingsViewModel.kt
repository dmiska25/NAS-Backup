package com.example.nasbackup.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nasbackup.datastore.SmbConnectionsStateManager
import com.example.nasbackup.domain.SmbFileContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ConnectionSettingsViewModel @Inject constructor(
    private val smbConnectionsStateManager: SmbConnectionsStateManager
) : ViewModel() {
    // Observed from DataStore manager
    private val _savedConnections = smbConnectionsStateManager.savedConnections
    val savedConnections: StateFlow<List<SmbFileContext>> = _savedConnections

    // Currently selected approach: either a saved connection or new
    private var currentlySelectedConnection: SmbFileContext? = null

    // Inputs for a new connection
    val ipAddress = MutableStateFlow("")
    val shareName = MutableStateFlow("")
    val username = MutableStateFlow("")
    val password = MutableStateFlow("")

    // We expose a canTestConnection
    private val _canTestConnection = MutableStateFlow(false)
    val canTestConnection: StateFlow<Boolean> = _canTestConnection

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection

    private val _isConnectionTestSuccessful = MutableStateFlow(false)
    val isConnectionTestSuccessful: StateFlow<Boolean> = _isConnectionTestSuccessful

    private val _currentDirectory = MutableStateFlow<SmbFile?>(null)
    val currentDirectory: StateFlow<SmbFile?> = _currentDirectory

    private val _directories = MutableStateFlow<List<SmbFile>>(emptyList())
    val directories: StateFlow<List<SmbFile>> = _directories

    private val _initialDirectory = MutableStateFlow<SmbFile?>(null)
    val initialDirectory: StateFlow<SmbFile?> = _initialDirectory

    private val _selectedBackupDirectory = MutableStateFlow<SmbFile?>(null)
    val selectedBackupDirectory: StateFlow<SmbFile?> = _selectedBackupDirectory

    init {
        // recalcCanTest and set connection test false whenever fields change
        combine(
            ipAddress,
            shareName,
            username,
            password
        ) { _, _, _, _ -> }.onEach {
            recalcCanTest()
            _isConnectionTestSuccessful.value = false
        }.launchIn(viewModelScope)
    }

    fun onSelectSavedConnection(creds: SmbFileContext) {
        viewModelScope.launch(Dispatchers.IO) {
            currentlySelectedConnection = creds
            ipAddress.value = creds.ipAddress
            shareName.value = creds.shareName
            username.value = creds.username ?: ""
            password.value = creds.password ?: ""
            _selectedBackupDirectory.value = creds.route?.let {
                val smbFile = createSmbFile(it)
                if (smbFile.exists() && smbFile.isDirectory) {
                    smbFile
                } else {
                    null
                }
            }
            _isConnectionTestSuccessful.value = false
            recalcCanTest()
        }
    }

    fun onSelectNewConnection() {
        currentlySelectedConnection = null
        ipAddress.value = ""
        shareName.value = ""
        username.value = ""
        password.value = ""
        _isConnectionTestSuccessful.value = false
        recalcCanTest()
    }

    private fun recalcCanTest() {
        _canTestConnection.value =
            ipAddress.value.isNotBlank() &&
                shareName.value.isNotBlank()
    }

    fun saveConnection(onResult: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val newCreds = SmbFileContext(
                    ipAddress = ipAddress.value,
                    shareName = shareName.value,
                    username = username.value,
                    password = password.value,
                    route = _selectedBackupDirectory.value?.canonicalPath
                )
                smbConnectionsStateManager.saveConnection(newCreds)
                // Mark it as currently selected
                currentlySelectedConnection = newCreds
            } catch (e: Exception) {
                println("Error saving connection: $e")
                onResult(false)
                return@launch
            }
            onResult(true)
        }
    }

    fun testConnection(onResult: (success: Boolean) -> Unit) {
        if (!_canTestConnection.value) return
        _isTestingConnection.value = true
        _isConnectionTestSuccessful.value = false

        viewModelScope.launch(Dispatchers.IO) {
            val success = try {
                val smbFile = createSmbFile(null)
                smbFile.exists()
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                _isTestingConnection.value = false
                _isConnectionTestSuccessful.value = success
                if (success) {
                    loadDirectory(null)
                }
            }
            onResult(success)
        }
    }

    private fun createSmbFile(route: String?): SmbFile {
        val ctx = SmbFileContext(
            ipAddress = ipAddress.value,
            shareName = shareName.value,
            username = username.value,
            password = password.value,
            route = route
        )
        return ctx.toSmbFile()
    }

    fun loadDirectory(route: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val smbFile = createSmbFile(route)
                if (smbFile.exists() && smbFile.isDirectory) {
                    _currentDirectory.value = smbFile
                    val list = smbFile.listFiles()?.toList() ?: emptyList()

                    _directories.value = list
                    if (_initialDirectory.value == null) {
                        _initialDirectory.value = smbFile
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun navigateToDirectory(dir: SmbFile) {
        loadDirectory(dir.canonicalPath)
    }

    fun isDirectoryAsync(
        smbFile: SmbFile,
        onIsTrue: (() -> Unit)? = null,
        onIsFalse: (() -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val isDir = smbFile.isDirectory
            withContext(Dispatchers.Main) {
                if (isDir) onIsTrue?.invoke() else onIsFalse?.invoke()
            }
        }
    }

    fun allowedToNavigateUp(): Boolean {
        val curr = _currentDirectory.value ?: return false
        val init = _initialDirectory.value ?: return false
        return (curr.canonicalPath != init.canonicalPath && curr.parent != null)
    }

    fun navigateUpDirectory(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val curr = _currentDirectory.value ?: return@launch
            val parentPath = curr.parent ?: return@launch
            try {
                val parentDir = createSmbFile(parentPath)
                if (parentDir.exists() && parentDir.isDirectory) {
                    _currentDirectory.value = parentDir
                    _directories.value = parentDir.listFiles()?.toList() ?: emptyList()
                }
            } catch (_: Exception) {
            }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun createNewFolder(folderName: String, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val curr = _currentDirectory.value
            if (curr != null && curr.isDirectory) {
                val newDirPath = curr.canonicalPath + folderName.trimEnd('/') + "/"
                try {
                    val newDir = createSmbFile(newDirPath)
                    if (!newDir.exists()) {
                        newDir.mkdir()
                    }
                    _directories.value = curr.listFiles()?.toList() ?: emptyList()
                } catch (_: Exception) {
                }
            }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun confirmBackupLocation() {
        _selectedBackupDirectory.value = _currentDirectory.value
    }

    fun persistToParent(parentVM: BackupNowFlowViewModel) {
        parentVM.setSmbFileContext(
            SmbFileContext(
                ipAddress = ipAddress.value,
                shareName = shareName.value,
                username = username.value,
                password = password.value,
                route = _selectedBackupDirectory.value?.canonicalPath
            )
        )
    }

    companion object {
        const val NEW_CONNECTION: String = "New Connection"
        const val NO_SELECTION: String = "Select Saved Connection"
    }
}
