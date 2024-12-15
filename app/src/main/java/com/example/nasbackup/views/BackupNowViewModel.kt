package com.example.nasbackup.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nasbackup.datastore.BackupManager
import com.example.nasbackup.datastore.BackupNowStateManager
import com.example.nasbackup.datastore.SmbCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Properties
import javax.inject.Inject
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class BackupNowViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val backupNowStateManager: BackupNowStateManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isConnectionSetupComplete = MutableStateFlow(false)
    val isConnectionSetupComplete: StateFlow<Boolean> = _isConnectionSetupComplete

    private val _isBackupLocationSelected = MutableStateFlow(false)
    val isBackupLocationSelected: StateFlow<Boolean> = _isBackupLocationSelected

    private val _isConnectionTestSuccessful = MutableStateFlow(false)
    val isConnectionTestSuccessful: StateFlow<Boolean> = _isConnectionTestSuccessful

    private val _showConnectionSetup = MutableStateFlow(false)
    val showConnectionSetup: StateFlow<Boolean> = _showConnectionSetup

    private val _showBackupLocationSetup = MutableStateFlow(false)
    val showBackupLocationSetup: StateFlow<Boolean> = _showBackupLocationSetup

    private val _currentDirectory = MutableStateFlow<SmbFile?>(null)
    val currentDirectory: StateFlow<SmbFile?> = _currentDirectory

    private val _directories = MutableStateFlow<List<SmbFile>>(emptyList())
    val directories: StateFlow<List<SmbFile>> = _directories

    // Credentials stored in memory
    var ipAddress: String = ""
    var shareName: String = ""
    var username: String = ""
    var password: String = ""

    private var initialSetupDone = false

    init {
        // Wait until BackupNowStateManager emits its initial values for credentials and directory
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                backupNowStateManager.savedCredentials,
                backupNowStateManager.savedBackupDirectory
            ) { credentials, directory ->
                credentials to directory
            }.collectLatest { (creds, savedDir) ->
                // The state manager has loaded something (creds might be null if not saved before)
                if (!initialSetupDone) {
                    handleInitialState(creds, savedDir)
                    initialSetupDone = true
                } else {
                    // If you want to handle changes after the initial load, you can do so here.
                    // For example, if creds or savedDir change again due to other actions.
                }
            }
        }
    }

    private suspend fun handleInitialState(creds: SmbCredentials?, savedDir: String?) {
        if (creds != null) {
            // Load credentials into memory
            ipAddress = creds.ipAddress
            shareName = creds.shareName
            username = creds.username
            password = creds.password

            // Test connection silently
            val connectionSuccess = testSMBConnection(
                creds.ipAddress,
                creds.shareName,
                creds.username,
                creds.password
            ) { rootDirectory ->
                _currentDirectory.value = rootDirectory
                _directories.value = rootDirectory?.listFiles()?.toList() ?: emptyList()
            }

            if (connectionSuccess) {
                _isConnectionTestSuccessful.value = true
                _isConnectionSetupComplete.value = true

                if (savedDir != null && savedDir.isNotBlank()) {
                    val directoryValid = verifyBackupDirectory(savedDir)
                    if (directoryValid) {
                        _isBackupLocationSelected.value = true
                    } else {
                        // Directory no longer valid; remove from disk
                        backupNowStateManager.persistBackupDirectory("")
                        _isBackupLocationSelected.value = false
                    }
                }
            } else {
                // Connection test failed
                _isConnectionTestSuccessful.value = false
                _isConnectionSetupComplete.value = false
            }
        } else {
            // No credentials saved, so start fresh
            _isConnectionTestSuccessful.value = false
            _isConnectionSetupComplete.value = false
        }

        // Done loading initial state
        _isLoading.value = false
    }

    private suspend fun verifyBackupDirectory(directoryPath: String): Boolean = withContext(
        Dispatchers.IO
    ) {
        try {
            val smbFile = createSmbFile(directoryPath)
            smbFile.exists() && smbFile.isDirectory
        } catch (e: Exception) {
            false
        }
    }

    private fun createSmbFile(path: String): SmbFile {
        val properties = Properties().apply {
            put("jcifs.smb.client.minVersion", "SMB202")
            put("jcifs.smb.client.maxVersion", "SMB311")
        }
        val config = PropertyConfiguration(properties)
        val baseContext: CIFSContext = BaseContext(config)
        val authContext = baseContext.withCredentials(
            NtlmPasswordAuthenticator("", username, password)
        )
        return SmbFile(path, authContext)
    }

    fun showConnectionSetup() {
        if (!_isLoading.value) {
            _showConnectionSetup.value = true
        }
    }

    fun showBackupLocationSetup() {
        if (!_isLoading.value) {
            _showBackupLocationSetup.value = true
        }
    }

    fun testConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            val success =
                testSMBConnection(ipAddress, shareName, username, password) { rootDirectory ->
                    _currentDirectory.value = rootDirectory
                    _directories.value = rootDirectory?.listFiles()?.toList() ?: emptyList()
                }
            _isConnectionTestSuccessful.value = success
        }
    }

    fun confirmConnection() {
        if (_isConnectionTestSuccessful.value) {
            _isConnectionSetupComplete.value = true
            _showConnectionSetup.value = false
            // Persist credentials
            viewModelScope.launch {
                backupNowStateManager.persistCredentials(ipAddress, shareName, username, password)
            }
        }
    }

    fun confirmBackupLocation() {
        _isBackupLocationSelected.value = true
        _showBackupLocationSetup.value = false
        val currentDirectorySnapshot = _currentDirectory.value
        if (currentDirectorySnapshot != null) {
            viewModelScope.launch {
                backupNowStateManager.persistBackupDirectory(currentDirectorySnapshot.canonicalPath)
            }
        }
    }

    fun navigateToDirectory(directory: SmbFile) {
        viewModelScope.launch(Dispatchers.IO) {
            _currentDirectory.value = directory
            _directories.value = directory.listFiles()?.toList() ?: emptyList()
        }
    }

    fun performBackupAsync(onComplete: (Boolean) -> Unit) {
        val dir = _currentDirectory.value ?: return
        backupManager.performBackupAsync(dir) { success ->
            if (success) {
                viewModelScope.launch {
                    backupNowStateManager.persistCredentials(
                        ipAddress,
                        shareName,
                        username,
                        password
                    )
                    backupNowStateManager.persistBackupDirectory(dir.canonicalPath)
                }
            }
            onComplete(success)
        }
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

    private suspend fun testSMBConnection(
        ipAddress: String,
        shareName: String,
        username: String,
        password: String,
        onSuccess: (SmbFile?) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val smbUrl = "smb://$ipAddress/$shareName/"
                val properties = Properties().apply {
                    put("jcifs.smb.client.minVersion", "SMB202")
                    put("jcifs.smb.client.maxVersion", "SMB311")
                }
                val config = PropertyConfiguration(properties)
                val baseContext: CIFSContext = BaseContext(config)
                val authContext = baseContext.withCredentials(
                    NtlmPasswordAuthenticator("", username, password)
                )
                val smbFile = SmbFile(smbUrl, authContext)

                if (smbFile.exists()) {
                    onSuccess(smbFile)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
