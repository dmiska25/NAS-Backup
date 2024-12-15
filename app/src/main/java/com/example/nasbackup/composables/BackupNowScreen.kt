package com.example.nasbackup.composables

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.nasbackup.views.BackupNowViewModel
import jcifs.smb.SmbFile

@Composable
fun BackupNowScreen(nav: NavHostController, viewModel: BackupNowViewModel = hiltViewModel()) {
    val context = LocalContext.current

    val isConnectionSetupComplete by viewModel.isConnectionSetupComplete.collectAsState()
    val isBackupLocationSelected by viewModel.isBackupLocationSelected.collectAsState()
    val isConnectionTestSuccessful by viewModel.isConnectionTestSuccessful.collectAsState()
    val showConnectionSetup by viewModel.showConnectionSetup.collectAsState()
    val showBackupLocationSetup by viewModel.showBackupLocationSetup.collectAsState()
    val directories by viewModel.directories.collectAsState()
    val currentDirectory by viewModel.currentDirectory.collectAsState()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        PageHeader(title = "Backup Now", onBack = { nav.navigate(NavRoutes.MAIN_MENU) })

        // Buttons
        Button(
            onClick = { viewModel.showConnectionSetup() },
            enabled = !isConnectionSetupComplete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connection Setup")
        }

        Button(
            onClick = { viewModel.showBackupLocationSetup() },
            enabled = isConnectionSetupComplete && !isBackupLocationSelected,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Backup Location")
        }

        Button(
            onClick = {
                viewModel.performBackupAsync {
                    if (it) {
                        Toast.makeText(context, "Backup Successful", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Backup Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            enabled = isConnectionSetupComplete && isBackupLocationSelected,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Backup Now")
        }

        // Conditional UI
        if (showConnectionSetup) {
            ConnectionSetupUI(
                ipAddressInitial = viewModel.ipAddress,
                shareNameInitial = viewModel.shareName,
                usernameInitial = viewModel.username,
                passwordInitial = viewModel.password,
                isConnectionTestSuccessful = isConnectionTestSuccessful,
                onIpAddressChange = { viewModel.updateIpAddress(it) },
                onShareNameChange = { viewModel.updateShareName(it) },
                onUsernameChange = { viewModel.updateUsername(it) },
                onPasswordChange = { viewModel.updatePassword(it) },
                onTestConnection = { viewModel.testConnection() },
                onConfirm = { viewModel.confirmConnection() }
            )
        }

        if (showBackupLocationSetup) {
            BackupLocationSelectionUI(
                directories = directories,
                onNavigateToDirectory = { viewModel.navigateToDirectory(it) },
                onConfirm = { viewModel.confirmBackupLocation() },
                currentDirectory = currentDirectory
            )
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

        Button(onClick = onTestConnection, modifier = Modifier.fillMaxWidth()) {
            Text("Test Connection")
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

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Select Backup Location")

        LazyColumn {
            items(directories) { dir ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isLoading = true
                            try {
                                viewModel.isDirectoryAsync(dir, onIsTrue = {
                                    onNavigateToDirectory(dir)
                                })
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                isLoading = false
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
