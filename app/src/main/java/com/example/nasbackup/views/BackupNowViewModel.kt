package com.example.nasbackup.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nasbackup.datastore.BackupManager
import com.example.nasbackup.datastore.BackupNowStateManager
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class BackupNowViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val backupNowStateManager: BackupNowStateManager
) : ViewModel() {

    // UI state
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

    init {
        // Restore previously saved state
        viewModelScope.launch {
            // Collect credentials and update state accordingly
            backupNowStateManager.savedCredentials.collect { creds ->
                if (creds != null) {
                    // Attempt to reconnect silently
                    val success = testSMBConnection(
                        creds.ipAddress,
                        creds.shareName,
                        creds.username,
                        creds.password
                    ) {
                        _currentDirectory.value = it
                        _directories.value = it?.listFiles()?.toList() ?: emptyList()
                    }

                    if (success) {
                        _isConnectionSetupComplete.value = true
                        _isConnectionTestSuccessful.value = true
                    }
                }
            }
        }
        viewModelScope.launch {
            // Collect selected directory and update state accordingly
            backupNowStateManager.savedBackupDirectory.collect { selectedDir ->
                if (selectedDir != null) {
                    // Attempt to navigate to selectedDir if connection is setup
                    if (_isConnectionSetupComplete.value) {
                        // verify directory
                        try {
                            _isBackupLocationSelected.value = true
                            // currentDirectory is already set above if connection was successful
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    // Credentials
    var ipAddress: String = ""
    var shareName: String = ""
    var username: String = ""
    var password: String = ""

    fun updateIpAddress(value: String) {
        ipAddress = value
    }

    fun updateShareName(value: String) {
        shareName = value
    }

    fun updateUsername(value: String) {
        username = value
    }

    fun updatePassword(value: String) {
        password = value
    }

    fun showConnectionSetup() {
        _showConnectionSetup.value = true
    }

    fun showBackupLocationSetup() {
        _showBackupLocationSetup.value = true
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
        // Persist the selected directory
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
                // Save state after successful backup
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
            if (isDir && onIsTrue != null) {
                onIsTrue()
            }
            if (!isDir && onIsFalse != null) {
                onIsFalse()
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
