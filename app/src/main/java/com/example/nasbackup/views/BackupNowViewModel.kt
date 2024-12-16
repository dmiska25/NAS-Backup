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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@HiltViewModel
class BackupNowViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val backupNowStateManager: BackupNowStateManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp

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

    private val _initialDirectory = MutableStateFlow<SmbFile?>(null)
    val initialDirectory: StateFlow<SmbFile?> = _initialDirectory

    private val _selectedBackupDirectory = MutableStateFlow<SmbFile?>(null)
    val selectedBackupDirectory: StateFlow<SmbFile?> = _selectedBackupDirectory

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
            ipAddress = creds.ipAddress
            shareName = creds.shareName
            username = creds.username
            password = creds.password

            val connectionSuccess = testSMBConnection(
                creds.ipAddress,
                creds.shareName,
                creds.username,
                creds.password
            ) { rootDirectory ->
                _currentDirectory.value = rootDirectory
                _directories.value = rootDirectory?.listFiles()?.toList() ?: emptyList()
                if (_initialDirectory.value == null && rootDirectory != null) {
                    _initialDirectory.value = rootDirectory
                }
            }

            if (connectionSuccess) {
                _isConnectionTestSuccessful.value = true
                _isConnectionSetupComplete.value = true

                // If we have a saved directory, verify and if valid, set as selected directory
                if (!savedDir.isNullOrBlank()) {
                    val directoryValid = verifyBackupDirectory(savedDir)
                    if (directoryValid) {
                        val savedFile = createSmbFile(savedDir)
                        _selectedBackupDirectory.value = savedFile
                        _isBackupLocationSelected.value = true
                        // We may want to navigate currentDirectory to the selected one so user sees it
                        navigateToDirectory(savedFile)
                    } else {
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
            // No credentials saved
            _isConnectionTestSuccessful.value = false
            _isConnectionSetupComplete.value = false
        }

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

    fun onIpAddressChange(ip: String) {
        ipAddress = ip
        onConnectionValueChange()
    }

    fun onShareNameChange(share: String) {
        shareName = share
        onConnectionValueChange()
    }

    fun onUsernameChange(user: String) {
        username = user
        onConnectionValueChange()
    }

    fun onPasswordChange(pass: String) {
        password = pass
        onConnectionValueChange()
    }

    fun onConnectionValueChange() {
        _isConnectionTestSuccessful.value = false
        _isConnectionSetupComplete.value = false
        _isBackupLocationSelected.value = false
        _currentDirectory.value = null
        _selectedBackupDirectory.value = null
    }

    fun showConnectionSetup() {
        if (_showConnectionSetup.value) {
            _showConnectionSetup.value = false
            return
        }

        if (!_isLoading.value) {
            _showConnectionSetup.value = true
            _showBackupLocationSetup.value = false // collapse backup location form
        }
    }

    fun showBackupLocationSetup() {
        if (_showBackupLocationSetup.value) {
            _showBackupLocationSetup.value = false
            return
        }

        if (!_isLoading.value && _isConnectionTestSuccessful.value) {
            _showBackupLocationSetup.value = true
            _showConnectionSetup.value = false // collapse connection setup form
        }
    }

    fun testConnection() {
        if (isTestingConnection.value) {
            return
        }

        _isConnectionTestSuccessful.value = false
        _isTestingConnection.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val success =
                testSMBConnection(ipAddress, shareName, username, password) { rootDirectory ->

                    // Re-verify backup directory if previously selected
                    val savedDir = backupNowStateManager.savedBackupDirectory.value
                    runBlocking {
                        if (!savedDir.isNullOrEmpty()) {
                            val directoryValid = verifyBackupDirectory(savedDir)
                            if (!directoryValid) {
                                // Invalidate the previously selected directory
                                backupNowStateManager.persistBackupDirectory("")
                                _isBackupLocationSelected.value = false
                            }
                        }

                        _currentDirectory.value = rootDirectory
                        _directories.value = rootDirectory?.listFiles()?.toList() ?: emptyList()
                    }
                }

            _isConnectionTestSuccessful.value = success
            _isTestingConnection.value = false
        }
    }

    fun confirmConnection() {
        if (_isConnectionTestSuccessful.value) {
            _isConnectionSetupComplete.value = true
            _showConnectionSetup.value = false // collapse the connection form
            viewModelScope.launch {
                backupNowStateManager.persistCredentials(ipAddress, shareName, username, password)
            }
        }
    }

    fun confirmBackupLocation() {
        val dirToConfirm = _currentDirectory.value
        if (dirToConfirm != null) {
            // Set this directory as the selected/confirmed backup directory
            _selectedBackupDirectory.value = dirToConfirm
            _isBackupLocationSelected.value = true
            _showBackupLocationSetup.value = false
            viewModelScope.launch {
                backupNowStateManager.persistBackupDirectory(dirToConfirm.canonicalPath)
            }
        }
    }

    fun navigateToDirectory(directory: SmbFile) {
        viewModelScope.launch(Dispatchers.IO) {
            _currentDirectory.value = directory
            _directories.value = directory.listFiles()?.toList() ?: emptyList()
        }
    }

    fun navigateUpDirectory(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (allowedToNavigateUp()) {
                goUpDirectory()
            }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun allowedToNavigateUp(): Boolean {
        val currentDir = _currentDirectory.value
        val initialDir = _initialDirectory.value
        if (currentDir?.parent != null && initialDir != null && currentDir != initialDir) {
            return true
        }
        return false
    }

    private fun goUpDirectory() {
        val currentDir = _currentDirectory.value ?: return
        val parentPath = currentDir.parent ?: return
        val parentDir = createSmbFile(parentPath)
        if (parentDir.exists() && parentDir.isDirectory) {
            _currentDirectory.value = parentDir
            _directories.value = parentDir.listFiles()?.toList() ?: emptyList()
        }
    }

    fun createNewFolder(folderName: String, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentDir = _currentDirectory.value
            if (currentDir != null && currentDir.isDirectory) {
                val newDirPath = currentDir.canonicalPath + folderName.trimEnd('/') + "/"
                try {
                    val newDir = createSmbFile(newDirPath)
                    if (!newDir.exists()) {
                        newDir.mkdir() // Create the new directory
                    }
                    // Refresh directory listing
                    _directories.value = currentDir.listFiles()?.toList() ?: emptyList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Could show a toast or some form of error handling here
                }
            }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun performBackupAsync(onComplete: (Boolean) -> Unit) {
        if (_isBackingUp.value) return
        val dir = _selectedBackupDirectory.value ?: return
        _isBackingUp.value = true
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
            _isBackingUp.value = false
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
