package com.example.nasbackup.composables

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.nasbackup.utils.checkNotificationPermission
import com.example.nasbackup.utils.requestNotificationPermission
import com.example.nasbackup.views.BackupNowViewModel
import jcifs.smb.SmbFile

@Composable
fun BackupNowScreen(nav: NavHostController, viewModel: BackupNowViewModel = hiltViewModel()) {
    val context = LocalContext.current

    val isLoading by viewModel.isLoading.collectAsState()
    val isBackupLocationSelected by viewModel.isBackupLocationSelected.collectAsState()
    val isConnectionTestSuccessful by viewModel.isConnectionTestSuccessful.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val showConnectionSetup by viewModel.showConnectionSetup.collectAsState()
    val showBackupLocationSetup by viewModel.showBackupLocationSetup.collectAsState()
    val directories by viewModel.directories.collectAsState()
    val currentDirectory by viewModel.currentDirectory.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isLoading) {
            LoadingScreen(text = "Verifying previous connection...")
        } else {
            // Main UI
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PageHeader(title = "Backup Now", onBack = { nav.navigate(NavRoutes.MAIN_MENU) })

                // Connection Setup: Always allow editing if not loading
                Button(
                    onClick = { viewModel.showConnectionSetup() },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connection Setup")
                }

                // Backup Location: Enabled if connection test is successful and not loading
                Button(
                    onClick = { viewModel.showBackupLocationSetup() },
                    enabled = isConnectionTestSuccessful && !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Backup Location")
                }

                // Backup Now: Enabled if connection is successful, location selected, and not loading
                Button(
                    onClick = {
                        if (
                            !checkNotificationPermission(context) &&
                            !requestNotificationPermission(context)
                        ) {
                            Toast.makeText(
                                context,
                                "Notification permission required to perform backup",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }

                        viewModel.performBackupViaService(context)
                    },
                    enabled = isConnectionTestSuccessful &&
                        isBackupLocationSelected &&
                        !isLoading &&
                        !isTestingConnection &&
                        !showConnectionSetup &&
                        !showBackupLocationSetup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Backup Now")
                }

                if (showConnectionSetup) {
                    ConnectionSetupUI(
                        ipAddressInitial = viewModel.ipAddress,
                        shareNameInitial = viewModel.shareName,
                        usernameInitial = viewModel.username,
                        passwordInitial = viewModel.password,
                        isConnectionTestSuccessful = isConnectionTestSuccessful,
                        isTestingConnection = isTestingConnection,
                        onIpAddressChange = { viewModel.onIpAddressChange(it) },
                        onShareNameChange = { viewModel.onShareNameChange(it) },
                        onUsernameChange = { viewModel.onUsernameChange(it) },
                        onPasswordChange = { viewModel.onPasswordChange(it) },
                        onTestConnection = { viewModel.testConnection() },
                        onConfirm = { viewModel.confirmConnection() }
                    )
                } else if (showBackupLocationSetup) {
                    BackupLocationSelectionUI(
                        directories = directories,
                        onNavigateToDirectory = { viewModel.navigateToDirectory(it) },
                        onConfirm = { viewModel.confirmBackupLocation() },
                        currentDirectory = currentDirectory
                    )
                }

                if (!viewModel.ipAddress.isBlank()) {
                    Text("IP Address: ${viewModel.ipAddress}")
                }
                if (viewModel.selectedBackupDirectory.value != null) {
                    val dir = viewModel.selectedBackupDirectory.value!!.canonicalPath
                    Text(
                        "Selected Directory: $dir"
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionSetupUI(
    ipAddressInitial: String,
    shareNameInitial: String,
    usernameInitial: String,
    passwordInitial: String,
    isConnectionTestSuccessful: Boolean,
    isTestingConnection: Boolean,
    onIpAddressChange: (String) -> Unit,
    onShareNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onConfirm: () -> Unit
) {
    var ipAddress by remember { mutableStateOf(ipAddressInitial) }
    var shareName by remember { mutableStateOf(shareNameInitial) }
    var username by remember { mutableStateOf(usernameInitial) }
    var password by remember { mutableStateOf(passwordInitial) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextFieldWithLabel("IP Address", ipAddress) {
            ipAddress = it
            onIpAddressChange(it)
        }
        TextFieldWithLabel("Share Name", shareName) {
            shareName = it
            onShareNameChange(it)
        }
        TextFieldWithLabel("Username", username) {
            username = it
            onUsernameChange(it)
        }
        TextFieldWithLabel("Password", password) {
            password = it
            onPasswordChange(it)
        }

        Button(
            onClick = onTestConnection,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTestingConnection
        ) {
            if (!isTestingConnection) {
                Text("Test Connection")
            } else {
                CircularProgressIndicator()
            }
        }

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = isConnectionTestSuccessful
        ) {
            Text("Confirm")
        }
    }
}

@Composable
private fun BackupLocationSelectionUI(
    directories: List<SmbFile>,
    onNavigateToDirectory: (SmbFile) -> Unit,
    onConfirm: () -> Unit,
    currentDirectory: SmbFile?,
    viewModel: BackupNowViewModel = hiltViewModel()
) {
    var isLoading by remember { mutableStateOf(false) }
    var showNewFolderInput by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Select Backup Location")

        if (viewModel.allowedToNavigateUp()) {
            Button(
                onClick = {
                    isLoading = true
                    viewModel.navigateUpDirectory {
                        isLoading = false
                    }
                },
                enabled = !isLoading
            ) {
                Text("Go Up")
            }
        }

        // Button to show/hide new folder input
        Button(onClick = { showNewFolderInput = !showNewFolderInput }) {
            Text(if (!showNewFolderInput) "New Folder" else "Cancel")
        }

        // If showing new folder input, display a text field and a create button
        if (showNewFolderInput) {
            TextFieldWithLabel(label = "Folder Name", value = newFolderName) {
                newFolderName = it
            }
            Button(
                onClick = {
                    if (newFolderName.isNotBlank()) {
                        isLoading = true
                        viewModel.createNewFolder(newFolderName) {
                            // Folder created or failed, either way reset input and reload directory
                            isLoading = false
                            newFolderName = ""
                            showNewFolderInput = false
                        }
                    }
                },
                enabled = !isLoading && newFolderName.isNotBlank()
            ) {
                Text("Create Folder")
            }
        }

        LazyColumn {
            items(directories) { dir ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isLoading) {
                                isLoading = true
                                try {
                                    viewModel.isDirectoryAsync(dir, onIsTrue = {
                                        onNavigateToDirectory(dir)
                                        isLoading = false
                                    }, onIsFalse = {
                                        // If it's not a directory, we do nothing
                                        isLoading = false
                                    })
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    isLoading = false
                                }
                            }
                        }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = dir.name)
                }
            }
        }

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && currentDirectory != null
        ) {
            Text(if (isLoading) "Loading..." else "Confirm")
        }
    }
}

@Composable
private fun LoadingScreen(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text)
    }
}
