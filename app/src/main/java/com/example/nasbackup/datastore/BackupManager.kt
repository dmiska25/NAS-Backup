package com.example.nasbackup.datastore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileSelectionStateManager: FileSelectionStateManager
) {
    /**
     * Perform backup operation on a separate thread using coroutines.
     */
    fun performBackupAsync(nasDirectory: SmbFile, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = performBackup(nasDirectory)
            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    /**
     * Synchronous backup logic (called internally by performBackupAsync).
     */
    private fun performBackup(nasDirectory: SmbFile): Boolean {
        return try {
            val selectedFiles = fileSelectionStateManager.selectedFiles.value
            if (selectedFiles.isEmpty()) {
                println("No files selected for backup.")
                return false
            }

            // Create the "BackupNow" folder on the NAS
            val uri = "${nasDirectory.canonicalPath}/BackupNow/"
            println("BACKUP NOW URI: $uri")
            val backupNowFolder = SmbFile(uri, nasDirectory.context)
            if (!backupNowFolder.exists()) {
                backupNowFolder.mkdirs()
            }
            val hasWriteAccess = (backupNowFolder.getAttributes() and SmbFile.FILE_WRITE_DATA) != 0
            println("BackupNow folder attributes: ${backupNowFolder.getAttributes()}")
            println("BackupNow folder write access: $hasWriteAccess")

            println("BackupNow folder is writable: ${backupNowFolder.canWrite()}")

            // Iterate through each selected file/directory and copy it to the NAS
            for (file in selectedFiles) {
                val localFile = File(file)
                if (localFile.exists()) {
                    copyToNas(localFile, backupNowFolder)
                } else {
                    println("File does not exist: $file")
                }
            }

            println("Backup completed successfully.")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            println("Backup failed: ${e.message}")
            false
        }
    }

    private fun copyToNas(localFile: File, nasDirectory: SmbFile) {
        try {
            if (localFile.isDirectory) {
                // Recursively copy directory
                val uri = "${nasDirectory.canonicalPath}${localFile.name}"
                println("URI: $uri")
                val targetDir = SmbFile(uri, nasDirectory.context) // Use the parent's context

                targetDir.mkdirs()
                localFile.listFiles()?.forEach { child ->
                    copyToNas(child, targetDir) // Pass the correct context
                }
            } else {
                // Copy individual file
                val targetFile =
                    SmbFile("${nasDirectory.canonicalPath}/${localFile.name}", nasDirectory.context)
                FileInputStream(localFile).use { input ->
                    SmbFileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                println("Copied ${localFile.name} to ${targetFile.canonicalPath}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to copy ${localFile.name}: ${e.message}")
        }
    }
}
