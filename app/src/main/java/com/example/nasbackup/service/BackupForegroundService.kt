package com.example.nasbackup.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.nasbackup.dao.BackupJobDao
import com.example.nasbackup.database.AppDatabase
import com.example.nasbackup.datastore.BackupManager
import com.example.nasbackup.entity.BackupJob
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BackupForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "BackupChannelId"
        const val CHANNEL_NAME = "Backup Service Channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_BACKUP = "com.example.nasbackup.services.action.START_BACKUP"

        /* Extra keys for the intent */
        /**
         * SmbFileContext Backup Location Object serialized to JSON String
         */
        const val EXTRA_SMB_PATH = "com.example.nasbackup.services.extra.SMB_PATH"

        /**
         * Array of string file paths to backup
         */
        const val EXTRA_FILE_SELECTION = "com.example.nasbackup.services.extra.FILE_SELECTION"
    }

    @Inject
    lateinit var backupManager: BackupManager

    @Inject
    lateinit var backupJobDao: BackupJobDao

    @Inject
    lateinit var appDatabase: AppDatabase

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Volatile
    private var isProcessingJobs = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_BACKUP) {
            val smbFileContextEncoded = intent.getStringExtra(EXTRA_SMB_PATH)
            val fileSelectionEncoded = intent.getStringExtra(EXTRA_FILE_SELECTION)

            serviceScope.launch {
                handleBackupJobRequest(smbFileContextEncoded, fileSelectionEncoded)
            }
        } else if (intent == null) {
            // No intent, and not processing jobs. Start processing jobs.
            // we'll be able to start from where we left off on any previously
            // running and/or queued jobs.
            startProcessingJobs()
        }

        return START_STICKY
    }

    private fun handleBackupJobRequest(
        smbFileContextEncoded: String?,
        fileSelectionEncoded: String?
    ) {
        // Validate the input
        if (smbFileContextEncoded.isNullOrEmpty() || fileSelectionEncoded.isNullOrEmpty()) {
            if (isProcessingJobs) {
                return
            } else {
                val notification = buildNotification(
                    progress = 0,
                    isIndeterminate = true,
                    contentText = "Backup failed: Invalid backup location or file selection",
                    setProgress = false
                )
                startForeground(NOTIFICATION_ID, notification)

                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
                return
            }
        }

        appDatabase.runInTransaction {
            // Check if a backup job already exists for this selection
            val existing = backupJobDao.findUnfinishedJob(
                smbFileContextEncoded,
                fileSelectionEncoded
            )?.also { println("Found existing job, skipping creation.") }

            if (existing == null) {
                // queue the new job
                backupJobDao.insertOrUpdate(
                    createQueueJob(smbFileContextEncoded, fileSelectionEncoded)
                )
            }
        }

        startProcessingJobs()
    }

    private fun processJobs() {
        var process = true
        val activeJobs = backupJobDao.findActiveJobs()
        if (activeJobs.size > 1) {
            println(
                "Multiple active jobs found. This is an invalid state. " +
                    "Marking failed and Stopping processing."
            )
            activeJobs.forEach {
                backupJobDao.insertOrUpdate(it.copy(state = "FAILED"))
            }
            startForeground(
                NOTIFICATION_ID,
                buildNotification(
                    progress = 0,
                    isIndeterminate = true,
                    contentText = "Backup failed: Multiple active jobs found",
                    setProgress = false
                )
            )
            process = false
        }

        val notification = buildNotification(
            progress = 0,
            isIndeterminate = true,
            contentText = "Starting backup..."
        )
        startForeground(NOTIFICATION_ID, notification)

        while (process) {
            val job = backupJobDao.findNextJobToProcess()
            if (job == null) {
                break
            }

            var success = false
            try {
                success =
                    backupManager.performBackupWithProgress(
                        job
                    ) { fileIndex, totalFiles ->
                        if (totalFiles == null) {
                            updateNotification(
                                contentText = "Scanning files...",
                                isIndeterminate = true
                            )
                            return@performBackupWithProgress
                        }
                        val progressPercent =
                            if (totalFiles > 0) (fileIndex * 100) / totalFiles else 0
                        updateNotification(
                            contentText = "Backing up file $fileIndex of $totalFiles",
                            progress = progressPercent,
                            isIndeterminate = false
                        )
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                val finalText = if (success) "Backup Complete" else "Backup Failed"
                val finalNotification = buildNotification(
                    progress = 100,
                    isIndeterminate = false,
                    contentText = finalText,
                    ongoing = false,
                    setProgress = false
                )

                startForeground(NOTIFICATION_ID, finalNotification)
            }
        }

        isProcessingJobs = false
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun updateNotification(
        contentText: String,
        progress: Int = 0,
        isIndeterminate: Boolean = false
    ) {
        val notification = buildNotification(progress, isIndeterminate, contentText)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        progress: Int,
        isIndeterminate: Boolean,
        contentText: String,
        ongoing: Boolean = true,
        setProgress: Boolean = true
    ): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NAS Backup")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .apply {
                if (setProgress) {
                    setProgress(100, progress, isIndeterminate)
                }
            }
            .setOngoing(ongoing)
            .build()
    }

    override fun onDestroy() {
        isProcessingJobs = false
        stopForeground(STOP_FOREGROUND_DETACH)
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Shows backup progress"
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startProcessingJobs() {
        if (!isProcessingJobs) {
            println("Starting processing jobs.")
            isProcessingJobs = true
            serviceScope.launch {
                processJobs()
            }
        } else {
            println("Already processing jobs.")
        }
    }

    private fun createQueueJob(
        smbFileContextEncoded: String,
        fileSelectionEncoded: String
    ): BackupJob {
        return BackupJob(
            jobId = UUID.randomUUID(),
            state = "QUEUED",
            totalFiles = null,
            currentFileIndex = 0,
            smbFileContext = smbFileContextEncoded,
            fileSelection = fileSelectionEncoded,
            createdAt = System.currentTimeMillis(),
            startedAt = System.currentTimeMillis()
        )
    }
}
