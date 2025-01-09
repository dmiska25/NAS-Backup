package com.example.nasbackup.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nasbackup.datastore.FileSelectionStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DataSelectionViewModel @Inject constructor(
    private val fileSelectionStateManager: FileSelectionStateManager
) : ViewModel() {

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles

    private val _nextButtonEnabled = MutableStateFlow(false)
    val nextButtonEnabled: StateFlow<Boolean> = _nextButtonEnabled

    init {
        // Load existing selection from DataStore
        viewModelScope.launch {
            _selectedFiles.value = fileSelectionStateManager.selectedFiles.value
            _nextButtonEnabled.value = _selectedFiles.value.isNotEmpty()
        }
    }

    fun addFile(path: String) {
        _selectedFiles.value = _selectedFiles.value + path
        _nextButtonEnabled.value = _selectedFiles.value.isNotEmpty()
    }

    fun removeFile(path: String) {
        _selectedFiles.value = _selectedFiles.value - path
        _nextButtonEnabled.value = _selectedFiles.value.isNotEmpty()
    }

    fun persistToParent(parent: BackupNowFlowViewModel) {
        parent.setSelectedFiles(_selectedFiles.value)
        viewModelScope.launch {
            try {
                fileSelectionStateManager.persistSelectedFiles(_selectedFiles.value)
            } catch (e: Exception) {
                println("Error saving file selection: $e")
            }
        }
    }
}
