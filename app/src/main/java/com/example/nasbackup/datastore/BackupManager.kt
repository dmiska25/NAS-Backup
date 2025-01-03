package com.example.nasbackup.datastore

import com.example.nasbackup.dao.BackupJobDao
import com.example.nasbackup.domain.SmbFileContext
import com.example.nasbackup.entity.BackupJob
import com.example.nasbackup.exception.FileNoLongerExistsException
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileOutputStream

@Singleton
class BackupManager @Inject constructor(
    private val backupJobDao: BackupJobDao
) {
    companion object {
        val mapper = ObjectMapper()
    }

    /**
     * Performs a backup while preserving the original file structure under the "BackupNow" folder.
     *
     * 1) Creates or ensures the "BackupNow" folder on the NAS.
     * 2) Recursively discovers files from fileSelection, building an indexed list of (File, relativePath).
     * 3) Copies each file in order, creating subdirectories on the NAS as needed.
     * 4) Calls [onProgress] after each file is copied and returns null for totalFiles if scanning.
     */
    fun performBackupWithProgress(
        job: BackupJob,
        onProgress: (fileIndex: Int, totalFiles: Int?) -> Unit
    ): Boolean {
        return try {
            val destinationRoot = SmbFileContext.fromJson(job.smbFileContext).toSmbFile()
            val fileSelection = mapper.readValue(job.fileSelection, Set::class.java) as Set<String>
            if (fileSelection.isEmpty()) return false

            // 1) Ensure the "BackupNow+timestamp" folder on the NAS
            val backupNowPath = "${destinationRoot.canonicalPath}/BackupNow_${job.createdAt}/"
            val backupNowFolder = SmbFile(backupNowPath, destinationRoot.context)
            if (!backupNowFolder.exists()) {
                backupNowFolder.mkdirs()
            }

            // 1.1) Ensure the "files" folder inside the "BackupNow" folder
            val filesFolder =
                SmbFile("${backupNowFolder.canonicalPath}/files/", backupNowFolder.context)
            if (!filesFolder.exists()) {
                filesFolder.mkdirs()
            }

            // 2) Recursively gather all local files, along with their relative paths
            val indexedFiles = mutableListOf<IndexedFile>()
            if (job.foundFiles != null) {
                indexedFiles.addAll(
                    mapper.readValue(job.foundFiles, List::class.java) as List<IndexedFile>
                )
            } else {
                backupJobDao.insertOrUpdate(job.copy(state = "STARTING"))
                onProgress(0, null)

                for (sourcePath in fileSelection) {
                    val localFile = File(sourcePath)
                    if (localFile.exists()) {
                        discoverAllFiles(
                            localFile,
                            localFile.parentFile?.absolutePath ?: "",
                            indexedFiles
                        )
                    }
                }

                if (indexedFiles.size == 0) throw Exception("No files found in the selection.")
                backupJobDao.insertOrUpdate(
                    job.copy(
                        totalFiles = indexedFiles.size,
                        foundFiles = mapper.writeValueAsString(indexedFiles),
                        state = "RUNNING"
                    )
                )
            }

            // 3) Copy each file, preserving subfolder structure
            // Note: 1-based indexing here
            var index = (job.currentFileIndex - 1).coerceAtLeast(0)
            indexedFiles.subList(index, indexedFiles.size).forEach {
                index++
                backupJobDao.insertOrUpdate(job.copy(currentFileIndex = index))
                onProgress(index, indexedFiles.size)
                copyToNas(it, filesFolder)
            }

            backupJobDao.insertOrUpdate(
                job.copy(state = "COMPLETED", endedAt = System.currentTimeMillis())
            )
            println("Backup completed successfully.")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            backupJobDao.insertOrUpdate(
                job.copy(state = "FAILED", endedAt = System.currentTimeMillis())
            )
            println("Backup failed: ${e.message}")
            false
        }
    }

    /**
     * Recursively discovers all files in [root]. For each file:
     * - [root]'s parent is used as the "basePath" to compute a relative path.
     * - Store (file, relativePath) in [outIndexedFiles].
     *
     * @param root A file or directory to discover
     * @param basePath The absolute path of the top-level folder (used to compute relative paths)
     */
    private fun discoverAllFiles(
        root: File,
        basePath: String,
        outIndexedFiles: MutableList<IndexedFile>
    ) {
        if (root.isDirectory) {
            val files = root.listFiles() ?: return
            for (child in files) {
                discoverAllFiles(child, basePath, outIndexedFiles)
            }
        } else {
            // Compute relative path by subtracting basePath from the file's parent
            val parentPath = root.parentFile?.absolutePath ?: basePath
            val relativeDir = if (parentPath.startsWith(basePath)) {
                parentPath.removePrefix(basePath)
            } else {
                parentPath
            }

            // e.g. for a file /User/Photos/IMG_001.jpg
            // basePath = /User/Photos
            // relativeDir = "" (empty) if same folder, or /Subfolder
            outIndexedFiles.add(
                IndexedFile(
                    localFile = root,
                    relativeDir = relativeDir.trimStart(File.separatorChar)
                )
            )
        }
    }

    /**
     * Copies [indexedFile] to the NAS [backupNowFolder], recreating the subfolder structure.
     *
     * e.g., if relativeDir = "Some/Folder", then we create "BackupNow/Some/Folder/" before copying.
     */
    private fun copyToNas(indexedFile: IndexedFile, backupNowFolder: SmbFile) {
        try {
            // 1) Build the nested NAS directory
            val finalDirectory = if (indexedFile.relativeDir.isNotBlank()) {
                val subPath = backupNowFolder.canonicalPath +
                    indexedFile.relativeDir.trimStart('/') +
                    "/"
                // e.g. "smb://server/share/BackupNow/Some/Folder/"
                val subDir = SmbFile(subPath, backupNowFolder.context)
                if (!subDir.exists()) {
                    subDir.mkdirs()
                }
                subDir
            } else {
                backupNowFolder
            }

            // 2) Copy file
            val targetFile = SmbFile(
                finalDirectory.canonicalPath + indexedFile.localFile.name,
                finalDirectory.context
            )

            // verify source file exists
            if (!indexedFile.localFile.exists()) {
                throw FileNoLongerExistsException(
                    "File ${indexedFile.localFile.absolutePath} does not exist."
                )
            }

            FileInputStream(indexedFile.localFile).use { input ->
                SmbFileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            println("Copied ${indexedFile.localFile.absolutePath} to ${targetFile.canonicalPath}")
        } catch (e: FileNoLongerExistsException) {
            println(
                "Skipping file ${indexedFile.localFile.absolutePath} because it no longer exists."
            )
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to copy ${indexedFile.localFile.name}: ${e.message}")
        }
    }

    /**
     * Represents a local file along with a relative folder path that needs to be replicated in the backup.
     * e.g., localFile = /User/Photos/Subfolder/IMG_001.jpg, relativeDir = "Subfolder"
     */
    data class IndexedFile(
        val localFile: File,
        val relativeDir: String
    )
}
