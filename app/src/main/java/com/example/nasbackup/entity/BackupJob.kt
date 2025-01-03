package com.example.nasbackup.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity
data class BackupJob(
    @PrimaryKey val jobId: UUID,
    /*
     * One of "QUEUED", "STARTING, "RUNNING", "COMPLETED", "FAILED", "CANCELLED"
     */
    val state: String,
    val totalFiles: Int?,
    /*
     * 1-based index of the current file being processed
     */
    val currentFileIndex: Int,
    /*
     * JSON-encoded SmbFileContext
     */
    val smbFileContext: String,
    val fileSelection: String,
    val foundFiles: String? = null,
    val cancellationRequestedAt: Long? = null,
    val createdAt: Long,
    val startedAt: Long? = null,
    val endedAt: Long? = null
)
