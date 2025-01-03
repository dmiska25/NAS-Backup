package com.example.nasbackup.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.nasbackup.entity.BackupJob

@Dao
interface BackupJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(backupJob: BackupJob)

    @Query("SELECT * FROM BackupJob WHERE jobId = :jobId")
    fun getJob(jobId: String): BackupJob?

    @Query(
        "SELECT * FROM BackupJob WHERE smbFileContext = :smbFileContextEncoded " +
            "AND fileSelection = :fileSelectionEncoded " +
            "and state in ('RUNNING', 'STARTING', 'QUEUED')"
    )
    fun findUnfinishedJob(smbFileContextEncoded: String, fileSelectionEncoded: String): BackupJob?

    @Query("SELECT * FROM BackupJob WHERE state in ('RUNNING', 'STARTING')")
    fun findActiveJobs(): List<BackupJob>

    @Query(
        "SELECT * FROM BackupJob WHERE state in ('RUNNING', 'STARTING', 'QUEUED') " +
            "ORDER BY createdAt ASC LIMIT 1"
    )
    fun findNextJobToProcess(): BackupJob?

    @Query("DELETE FROM BackupJob")
    fun clearAllJobs()
}
