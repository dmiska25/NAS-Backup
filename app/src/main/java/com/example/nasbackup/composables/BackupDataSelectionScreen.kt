package com.example.nasbackup.composables

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.nasbackup.utils.checkStoragePermission
import com.example.nasbackup.utils.listDirectories
import com.example.nasbackup.utils.requestStoragePermission
import com.example.nasbackup.views.BackupNowFlowViewModel
import com.example.nasbackup.views.DataSelectionViewModel

@Composable
fun BackupDataSelectionScreen(
    nav: NavHostController,
    parentVM: BackupNowFlowViewModel = hiltViewModel(),
    viewModel: DataSelectionViewModel = hiltViewModel(),
    onNext: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(checkStoragePermission(context)) }

    // Local file browsing
    var currentDirectory by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    val initialDirectory = remember { currentDirectory }
    var directories by remember { mutableStateOf(listDirectories(currentDirectory)) }

    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val nextButtonEnabled by viewModel.nextButtonEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        PageHeader(
            title = "Select Files to Back Up",
            onBack = { nav.popBackStack() }
        )

        if (!hasPermission) {
            Button(onClick = {
                requestStoragePermission(context) {
                    hasPermission = it
                    if (it) {
                        currentDirectory = Environment.getExternalStorageDirectory()
                        directories = listDirectories(currentDirectory)
                    }
                }
            }, modifier = Modifier.padding(bottom = 16.dp)) {
                Text("Grant Permission")
            }
        } else {
            // "Go Up" if possible
            if (currentDirectory.parentFile != null && currentDirectory != initialDirectory) {
                Button(
                    onClick = {
                        currentDirectory.parentFile?.let { parentDir ->
                            currentDirectory = parentDir
                            directories = listDirectories(parentDir)
                        }
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text("Go Up")
                }
            }

            // Directory listing
            Column(modifier = Modifier.weight(1f)) {
                LazyColumn(modifier = Modifier.weight(3f)) {
                    items(directories) { dir ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = dir.isDirectory) {
                                    // If folder, open it
                                    if (dir.isDirectory) {
                                        currentDirectory = dir
                                        directories = listDirectories(dir)
                                    }
                                }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dir.name, style = MaterialTheme.typography.bodyLarge)
                                if (dir.isDirectory) {
                                    Text(
                                        "(Folder)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            val isChecked = selectedFiles.contains(dir.absolutePath)
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        viewModel.addFile(dir.absolutePath)
                                    } else {
                                        viewModel.removeFile(dir.absolutePath)
                                    }
                                }
                            )
                        }
                    }
                }

                // Show selected items
                // TODO: We'll probably need to limit this in size
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Selected Items:",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(selectedFiles.toList()) { item ->
                            Text(
                                text = item,
                                modifier = Modifier.padding(4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Next
            Button(
                onClick = {
                    viewModel.persistToParent(parentVM)
                    onNext()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                enabled = nextButtonEnabled
            ) {
                Text("Next")
            }
        }
    }
}
