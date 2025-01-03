package com.example.nasbackup.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.nasbackup.dao.BackupJobDao
import com.example.nasbackup.entity.BackupJob

@Database(entities = [BackupJob::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun backupJobDao(): BackupJobDao
}
