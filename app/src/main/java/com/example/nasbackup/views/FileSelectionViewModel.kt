package com.example.nasbackup.views

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nasbackup.datastore.FileSelectionStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class FileSelectionViewModel
@Inject
constructor(
    private val stateManager: FileSelectionStateManager
) : ViewModel() {
    // Temporary state for the current session's selection
    private val _tempSelectedFiles = mutableStateOf<Set<String>>(emptySet())
    val tempSelectedFiles: State<Set<String>> = _tempSelectedFiles

    // Observe global state and update temporary state
    init {
        viewModelScope.launch {
            stateManager.selectedFiles.value.let { globalState ->
                _tempSelectedFiles.value = globalState
            }

            // Collect updates from global state manager
            stateManager.selectedFiles.collect { globalState ->
                _tempSelectedFiles.value = globalState
            }
        }
    }

    // Add a file to the temporary selection
    fun addFile(file: String) {
        _tempSelectedFiles.value += file
    }

    // Remove a file from the temporary selection
    fun removeFile(file: String) {
        _tempSelectedFiles.value -= file
    }

    // Persist the temporary state to the global state manager
    fun confirmSelection() {
        viewModelScope.launch {
            stateManager.persistSelectedFiles(_tempSelectedFiles.value)
        }
    }
}
