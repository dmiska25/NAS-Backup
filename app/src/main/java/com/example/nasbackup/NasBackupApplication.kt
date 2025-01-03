package com.example.nasbackup

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.HiltAndroidApp

// Extension property to create DataStore instance
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "file_selection_preferences"
)

@HiltAndroidApp
class NasBackupApplication : Application()
