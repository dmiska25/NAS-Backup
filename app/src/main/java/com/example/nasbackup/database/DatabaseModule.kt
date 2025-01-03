package com.example.nasbackup.database

import android.content.Context
import androidx.room.Room
import com.example.nasbackup.dao.BackupJobDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "myapp.db"
        ).build()
    }

    @Provides
    fun provideBackupJobDao(db: AppDatabase): BackupJobDao {
        return db.backupJobDao()
    }
}
