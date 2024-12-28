package com.example.nasbackup.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.nasbackup.datastore.BackupManager
import com.example.nasbackup.domain.SmbFileContext
import dagger.hilt.android.AndroidEntryPoint
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

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_BACKUP) {
            val smbFileContextEncoded = intent.getStringExtra(EXTRA_SMB_PATH)
            val fileSelection = intent.getStringArrayExtra(EXTRA_FILE_SELECTION)?.toSet()

            if (!smbFileContextEncoded.isNullOrEmpty() && !fileSelection.isNullOrEmpty()) {
                val notification = buildNotification(
                    progress = 0,
                    isIndeterminate = true,
                    contentText = "Starting backup..."
                )
                startForeground(NOTIFICATION_ID, notification)

                serviceScope.launch {
                    doBackup(smbFileContextEncoded, fileSelection)
                }
            } else {
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun doBackup(backupLocationEncoded: String, fileSelection: Set<String>) {
        val backupLocation = SmbFileContext.fromJson(backupLocationEncoded).toSmbFile()

        var success = false
        try {
            updateNotification(
                contentText = "Scanning files...",
                isIndeterminate = true
            )

            success =
                backupManager.performBackupWithProgress(
                    backupLocation,
                    fileSelection
                ) { fileIndex, totalFiles ->
                    val progressPercent = if (totalFiles > 0) (fileIndex * 100) / totalFiles else 0
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
                ongoing = false
            )

            startForeground(NOTIFICATION_ID, finalNotification)
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
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
        ongoing: Boolean = true
    ): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NAS Backup")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setProgress(100, progress, isIndeterminate)
            .setOngoing(ongoing)
            .build()
    }

    override fun onDestroy() {
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
}
