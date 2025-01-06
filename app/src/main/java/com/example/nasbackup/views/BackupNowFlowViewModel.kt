package com.example.nasbackup.views

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nasbackup.datastore.FileSelectionStateManager
import com.example.nasbackup.domain.SmbFileContext
import com.example.nasbackup.service.BackupForegroundService
import com.example.nasbackup.utils.checkNotificationPermission
import com.example.nasbackup.utils.checkStoragePermission
import com.fasterxml.jackson.databind.ObjectMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BackupNowFlowViewModel @Inject constructor(
    private val fileSelectionStateManager: FileSelectionStateManager
) : ViewModel() {

    companion object {
        val mapper = ObjectMapper()
    }

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> get() = _selectedFiles

    private val _smbFileContext = MutableStateFlow<SmbFileContext?>(null)
    val smbFileContext: StateFlow<SmbFileContext?> get() = _smbFileContext

    fun setSelectedFiles(files: Set<String>) {
        _selectedFiles.value = files
    }

    fun setSmbFileContext(context: SmbFileContext) {
        _smbFileContext.value = context
    }

    fun saveFileSelectionToDataStore() {
        viewModelScope.launch {
            fileSelectionStateManager.persistSelectedFiles(_selectedFiles.value)
        }
    }

    fun initiateBackup(context: Context) {
        if (_selectedFiles.value.isEmpty() ||
            _smbFileContext.value == null
        ) {
            Toast.makeText(
                context,
                "Cannot start backup, missing required info",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!checkStoragePermission(context)) {
            Toast.makeText(
                context,
                "Storage permission not granted",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!checkNotificationPermission(context)) {
            Toast.makeText(
                context,
                "Notification permission not granted",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Toast.makeText(
            context,
            "Backup initiated. Check notification bar for progress.",
            Toast.LENGTH_LONG
        ).show()

        val encodedDir = _smbFileContext.value!!.toJson()
        val encodedFileSelection = mapper.writeValueAsString(_selectedFiles.value)

        val intent = Intent(context, BackupForegroundService::class.java).apply {
            action = BackupForegroundService.ACTION_START_BACKUP
            putExtra(BackupForegroundService.EXTRA_SMB_PATH, encodedDir)
            putExtra(BackupForegroundService.EXTRA_FILE_SELECTION, encodedFileSelection)
        }
        context.startForegroundService(intent)
    }
}
