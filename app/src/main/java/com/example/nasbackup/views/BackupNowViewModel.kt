package com.example.nasbackup.views

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nasbackup.datastore.BackupNowStateManager
import com.example.nasbackup.datastore.FileSelectionStateManager
import com.example.nasbackup.datastore.SmbCredentials
import com.example.nasbackup.domain.SmbFileContext
import com.example.nasbackup.service.BackupForegroundService
import com.fasterxml.jackson.databind.ObjectMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    private val backupNowStateManager: BackupNowStateManager,
    private val fileSelectionStateManager: FileSelectionStateManager
) : ViewModel() {
    companion object {
        val mapper = ObjectMapper()
    }

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
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                backupNowStateManager.savedCredentials,
                backupNowStateManager.savedBackupDirectory
            ) { credentials, directory ->
                credentials to directory
            }.collectLatest { (creds, savedDir) ->
                if (!initialSetupDone) {
                    handleInitialState(creds, savedDir)
                    initialSetupDone = true
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

            val connectionSuccess = testSMBConnection(createSmbFile(null)) { rootDirectory ->
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

    private fun createSmbFile(path: String?): SmbFile = createSmbFileContext(path).toSmbFile()

    private fun createSmbFileContext(path: String?): SmbFileContext = SmbFileContext(
        ipAddress = ipAddress,
        shareName = shareName,
        username = username,
        password = password,
        route = path
    )

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
                testSMBConnection(createSmbFile(null)) { rootDirectory ->
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
        _currentDirectory.value?.let {
            // Set this directory as the selected/confirmed backup directory
            _selectedBackupDirectory.value = it
            _isBackupLocationSelected.value = true
            _showBackupLocationSetup.value = false
            viewModelScope.launch {
                backupNowStateManager.persistBackupDirectory(it.canonicalPath)
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

    fun performBackupViaService(context: Context) {
        val encodedDir = createSmbFileContext(
            path = _selectedBackupDirectory.value?.canonicalPath
        ).toJson()

        // Build the intent
        val intent = Intent(context, BackupForegroundService::class.java).apply {
            action = BackupForegroundService.ACTION_START_BACKUP
            putExtra(BackupForegroundService.EXTRA_SMB_PATH, encodedDir)
            putExtra(
                BackupForegroundService.EXTRA_FILE_SELECTION,
                mapper.writeValueAsString(fileSelectionStateManager.selectedFiles.value)
            )
        }

        // Start the service in the foreground
        context.startForegroundService(intent)
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
        smbFile: SmbFile,
        onSuccess: (SmbFile?) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
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
