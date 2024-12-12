package com.example.nasbackup.views

import androidx.lifecycle.ViewModel
import com.example.nasbackup.datastore.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jcifs.smb.SmbFile

@HiltViewModel
class BackupNowViewModel @Inject constructor(
    private val backupManager: BackupManager
) : ViewModel() {

    fun performBackupAsync(nasDirectory: SmbFile, onComplete: (Boolean) -> Unit) {
        backupManager.performBackupAsync(nasDirectory, onComplete)
    }
}
