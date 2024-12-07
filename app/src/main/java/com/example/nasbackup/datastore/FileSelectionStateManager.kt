package com.example.nasbackup.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.nasbackup.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileSelectionStateManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val dataStore = context.dataStore

    // Use StateFlow for reactivity
    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            // Fetch initial value from DataStore
            val initialFiles = dataStore.data
                .map { preferences -> preferences[SELECTED_FILES_KEY] ?: emptySet() }
                .first()
            _selectedFiles.value = initialFiles

            // Observe changes in DataStore and update StateFlow
            dataStore.data.map { preferences ->
                preferences[SELECTED_FILES_KEY] ?: emptySet()
            }.collect { files ->
                _selectedFiles.value = files
            }
        }
    }

    suspend fun persistSelectedFiles(files: Set<String>) {
        dataStore.edit { preferences ->
            preferences[SELECTED_FILES_KEY] = files
        }
        _selectedFiles.value = files
    }

    companion object {
        private val SELECTED_FILES_KEY = stringSetPreferencesKey("selected_files")
    }
}

