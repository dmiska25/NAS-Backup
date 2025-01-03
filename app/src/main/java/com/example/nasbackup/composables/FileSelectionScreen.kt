package com.example.nasbackup.composables

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.nasbackup.utils.checkStoragePermission
import com.example.nasbackup.utils.listDirectories
import com.example.nasbackup.utils.requestStoragePermission
import com.example.nasbackup.views.FileSelectionViewModel

@Composable
fun FileSelectionScreen(
    nav: NavHostController,
    viewModel: FileSelectionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(checkStoragePermission(context)) }
    var currentDirectory by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    val initialDirectory by remember { mutableStateOf(currentDirectory) }
    var directories by remember { mutableStateOf(listDirectories(currentDirectory)) }
    val tempSelectedFiles by viewModel.tempSelectedFiles

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        PageHeader("File Selection", onBack = { nav.navigate(NavRoutes.CONFIGURATION) })

        // Permission Request Button
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
            // Go Up Button
            if (currentDirectory.parentFile != null && currentDirectory != initialDirectory) {
                Button(onClick = {
                    currentDirectory = currentDirectory.parentFile!!
                    directories = listDirectories(currentDirectory)
                }, modifier = Modifier.padding(bottom = 16.dp)) {
                    Text("Go Up")
                }
            }

            // Main Content: Scrollable Directory List and Fixed Footer
            Column(modifier = Modifier.weight(1f)) {
                // Directory list (Takes ~3/4 of the remaining space)
                LazyColumn(modifier = Modifier.weight(3f)) {
                    items(directories) { dir ->
                        Row(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled =
                                    !tempSelectedFiles.contains(dir.absolutePath) &&
                                        dir.isDirectory
                                ) {
                                    if (dir.isDirectory) {
                                        currentDirectory = dir
                                        directories = listDirectories(dir)
                                    }
                                }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dir.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (
                                    dir.isDirectory &&
                                    !tempSelectedFiles.contains(dir.absolutePath)
                                ) {
                                    Text(
                                        text = "(Folder)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            Checkbox(
                                checked = tempSelectedFiles.contains(dir.absolutePath),
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        viewModel.addFile(dir.absolutePath)
                                    } else {
                                        viewModel.removeFile(dir.absolutePath)
                                    }
                                }
                            )
                        }
                    }
                }

                // Selected Items (Takes ~1/4 of the remaining space)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Selected Items:",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LazyColumn(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = 8.dp)
                    ) {
                        items(tempSelectedFiles.toList()) { item ->
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }

            // Confirm and persist the selection (Fixed at bottom)
            Button(
                onClick = {
                    viewModel.confirmSelection()
                    nav.navigate(
                        NavRoutes.CONFIGURATION
                    ) // Navigate back to the configuration screen
                },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Set Selection")
            }
        }
    }
}
