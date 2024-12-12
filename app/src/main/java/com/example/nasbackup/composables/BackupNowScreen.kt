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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.nasbackup.views.BackupNowViewModel
import java.util.Properties
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BackupNowScreen(nav: NavHostController, viewModel: BackupNowViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var isConnectionSetupComplete by remember { mutableStateOf(false) }
    var isBackupLocationSelected by remember { mutableStateOf(false) }
    var showConnectionSetup by remember { mutableStateOf(false) }
    var showBackupLocationSetup by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf("") }
    var shareName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isConnectionTestSuccessful by remember { mutableStateOf(false) }
    var currentDirectory by remember { mutableStateOf<SmbFile?>(null) }
    var directories by remember { mutableStateOf<List<SmbFile>>(emptyList()) }
    var selectedDirectory by remember { mutableStateOf<SmbFile?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        PageHeader(title = "Backup Now", onBack = { nav.navigate(NavRoutes.MAIN_MENU) })

        // Buttons for navigating between steps
        Button(
            onClick = { showConnectionSetup = true },
            enabled = !isConnectionSetupComplete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connection Setup")
        }

        Button(
            onClick = { showBackupLocationSetup = true },
            enabled = isConnectionSetupComplete && !isBackupLocationSelected,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Backup Location")
        }

        Button(
            onClick = {
                viewModel.performBackupAsync(
                    currentDirectory!!
                ) {
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

        // Conditional displays for each step
        if (showConnectionSetup) {
            ConnectionSetup(
                ipAddress = ipAddress,
                shareName = shareName,
                username = username,
                password = password,
                onIpAddressChange = { ipAddress = it },
                onShareNameChange = { shareName = it },
                onUsernameChange = { username = it },
                onPasswordChange = { password = it },
                isConnectionTestSuccessful = isConnectionTestSuccessful,
                onTestConnection = {
                    coroutineScope.launch(Dispatchers.IO) {
                        isConnectionTestSuccessful = testSMBConnection(
                            ipAddress,
                            shareName,
                            username,
                            password
                        ) { rootDirectory ->
                            currentDirectory = rootDirectory
                            directories = rootDirectory?.listFiles()?.toList() ?: emptyList()
                        }
                    }
                },
                onConfirm = {
                    if (isConnectionTestSuccessful) {
                        isConnectionSetupComplete = true
                        showConnectionSetup = false
                    }
                }
            )
        }

        if (showBackupLocationSetup) {
            BackupLocationSelection(
                currentDirectory = currentDirectory,
                directories = directories,
                onNavigateToDirectory = { directory ->
                    coroutineScope.launch(Dispatchers.IO) {
                        currentDirectory = directory
                        directories = directory.listFiles()?.toList() ?: emptyList()
                    }
                },
                onSelectDirectory = { selectedDirectory = it },
                onConfirm = {
                    isBackupLocationSelected = true
                    showBackupLocationSetup = false
                }
            )
        }
    }
}

@Composable
fun ConnectionSetup(
    ipAddress: String,
    shareName: String,
    username: String,
    password: String,
    onIpAddressChange: (String) -> Unit,
    onShareNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    isConnectionTestSuccessful: Boolean,
    onTestConnection: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextFieldWithLabel("IP Address", ipAddress, onIpAddressChange)
        TextFieldWithLabel("Share Name", shareName, onShareNameChange)
        TextFieldWithLabel("Username", username, onUsernameChange)
        TextFieldWithLabel("Password", password, onPasswordChange)

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
fun BackupLocationSelection(
    currentDirectory: SmbFile?,
    directories: List<SmbFile>,
    onNavigateToDirectory: (SmbFile) -> Unit,
    onSelectDirectory: (SmbFile) -> Unit,
    onConfirm: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
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
                            coroutineScope.launch(Dispatchers.IO) {
                                isLoading = true
                                try {
                                    if (dir.isDirectory) {
                                        onNavigateToDirectory(dir)
                                    } else {
                                        onSelectDirectory(dir)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
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
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Loading..." else "Confirm")
        }
    }
}

fun testSMBConnection(
    ipAddress: String,
    shareName: String,
    username: String,
    password: String,
    onSuccess: (SmbFile?) -> Unit
): Boolean {
    return try {
        val smbUrl = "smb://$ipAddress/$shareName/"
        println("Attempting to connect to SMB URL: $smbUrl")

        val properties = Properties().apply {
            put("jcifs.smb.client.minVersion", "SMB202")
            put("jcifs.smb.client.maxVersion", "SMB311")
        }
        val config = PropertyConfiguration(properties)
        val baseContext: CIFSContext = BaseContext(config)
        val authContext = baseContext.withCredentials(
            NtlmPasswordAuthenticator("", username, password)
        )
        val smbFile = SmbFile(smbUrl, authContext)

        if (smbFile.exists()) {
            println("Connection successful: $smbUrl")
            onSuccess(smbFile)
            true
        } else {
            println("Connection failed: $smbUrl")
            false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        println("Connection error: ${e.message}")
        false
    }
}
