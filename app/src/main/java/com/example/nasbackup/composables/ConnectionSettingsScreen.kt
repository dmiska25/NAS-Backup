package com.example.nasbackup.composables

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.nasbackup.views.BackupNowFlowViewModel
import com.example.nasbackup.views.ConnectionSettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsScreen(
    nav: NavHostController,
    parentVM: BackupNowFlowViewModel = hiltViewModel(),
    viewModel: ConnectionSettingsViewModel = hiltViewModel(),
    onNext: () -> Unit
) {
    val context = LocalContext.current

    val scope = rememberCoroutineScope()

    val savedConnections by viewModel.savedConnections.collectAsState()
    val ipAddress by viewModel.ipAddress.collectAsState()
    val shareName by viewModel.shareName.collectAsState()
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()

    val canTestConn by viewModel.canTestConnection.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val isConnectionTestSuccessful by viewModel.isConnectionTestSuccessful.collectAsState()

    val directories by viewModel.directories.collectAsState()
    val currentDirectory by viewModel.currentDirectory.collectAsState()
    val selectedBackupDirectory by viewModel.selectedBackupDirectory.collectAsState()
    val canNavigateUp = viewModel.allowedToNavigateUp()

    var expandedDropdown by remember { mutableStateOf(false) }
    var selectedConnectionLabel by remember {
        mutableStateOf(
            ConnectionSettingsViewModel.NO_SELECTION
        )
    }

    var showNewFolderInput by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    // Make the entire screen scrollable
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        PageHeader(
            title = "Connection Settings",
            onBack = { nav.popBackStack() }
        )

        // Dropdown for existing connections OR "New Connection"
        ExposedDropdownMenuBox(
            expanded = expandedDropdown,
            onExpandedChange = { expandedDropdown = !expandedDropdown }
        ) {
            TextField(
                readOnly = true,
                value = selectedConnectionLabel,
                onValueChange = {},
                label = { Text("Saved Connections") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown)
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expandedDropdown,
                onDismissRequest = { expandedDropdown = false }
            ) {
                savedConnections.forEach { conn ->
                    DropdownMenuItem(
                        text = {
                            Text("${conn.ipAddress}/${conn.shareName} (${conn.username})")
                        },
                        onClick = {
                            selectedConnectionLabel = "${conn.ipAddress}/${conn.shareName}"
                            viewModel.onSelectSavedConnection(conn)
                            expandedDropdown = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(ConnectionSettingsViewModel.NEW_CONNECTION) },
                    onClick = {
                        selectedConnectionLabel = ConnectionSettingsViewModel.NEW_CONNECTION
                        viewModel.onSelectNewConnection()
                        expandedDropdown = false
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedConnectionLabel != ConnectionSettingsViewModel.NO_SELECTION) {
            TextFieldWithLabel("IP Address", ipAddress) { viewModel.ipAddress.value = it }
            TextFieldWithLabel("Share Name", shareName) { viewModel.shareName.value = it }
            TextFieldWithLabel("Username", username) { viewModel.username.value = it }
            TextFieldWithLabel("Password", password) { viewModel.password.value = it }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Test Connection
        Button(
            onClick = {
                viewModel.testConnection { isSuccess ->
                    Handler(Looper.getMainLooper()).post {
                        if (isSuccess) {
                            Toast.makeText(
                                context,
                                "Connection test successful",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Connection test failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            },
            enabled = !isTestingConnection && canTestConn
        ) {
            if (isTestingConnection) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Test Connection")
            }
        }

        // SMB directory browser if test passed
        if (isConnectionTestSuccessful) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Select Backup Location (NAS)")

            // "Go Up"
            if (canNavigateUp) {
                Button(
                    onClick = {
                        viewModel.navigateUpDirectory {}
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text("Go Up")
                }
            }

            // "New Folder"
            Button(
                onClick = { showNewFolderInput = !showNewFolderInput },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(if (!showNewFolderInput) "New Folder" else "Cancel")
            }

            if (showNewFolderInput) {
                TextFieldWithLabel(
                    label = "Folder Name",
                    value = newFolderName,
                    onValueChange = { newFolderName = it }
                )
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createNewFolder(newFolderName) {
                                newFolderName = ""
                                showNewFolderInput = false
                            }
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    enabled = newFolderName.isNotBlank()
                ) {
                    Text("Create Folder")
                }
            }

            // Limit the height of directory listing
            Box(
                modifier = Modifier
                    .height(200.dp)
            ) {
                LazyColumn {
                    items(directories) { dir ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.isDirectoryAsync(dir, onIsTrue = {
                                        viewModel.navigateToDirectory(dir)
                                    })
                                }
                                .padding(8.dp)
                        ) {
                            Text(dir.name)
                        }
                    }
                }
            }

            // Confirm location
            Button(
                onClick = { viewModel.confirmBackupLocation() },
                modifier = Modifier.fillMaxWidth(),
                enabled = currentDirectory != null
            ) {
                Text("Confirm Location")
            }
            selectedBackupDirectory?.let {
                Text("Selected Directory: ${it.canonicalPath}")
            }
        }

        // "Save Connection" button
        Button(
            onClick = {
                scope.launch {
                    viewModel.saveConnection { success ->
                        if (success) {
                            selectedConnectionLabel = "$ipAddress/$shareName"
                            Toast.makeText(
                                nav.context,
                                "Connection saved successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                nav.context,
                                "Failed to save connection",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            },
            modifier = Modifier.padding(bottom = 8.dp),
            enabled = isConnectionTestSuccessful &&
                selectedBackupDirectory != null &&
                selectedConnectionLabel == ConnectionSettingsViewModel.NEW_CONNECTION
        ) {
            Text("Save Connection")
        }

        // "Continue" only if connection tested & location chosen
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                // Persist to parent
                viewModel.persistToParent(parentVM)
                onNext()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = (isConnectionTestSuccessful && selectedBackupDirectory != null)
        ) {
            Text("Next")
        }
    }
}
