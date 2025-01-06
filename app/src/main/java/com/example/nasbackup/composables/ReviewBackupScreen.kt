package com.example.nasbackup.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.nasbackup.views.BackupNowFlowViewModel

@Composable
fun ReviewBackupScreen(nav: NavHostController, parentVM: BackupNowFlowViewModel = hiltViewModel()) {
    val context = LocalContext.current

    val selectedFiles by parentVM.selectedFiles.collectAsState()
    val smbFileContext by parentVM.smbFileContext.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        PageHeader(
            title = "Review Backup",
            onBack = { nav.popBackStack() }
        )

        Text("Selected Files:")
        selectedFiles.forEach {
            Text(" - $it")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("Connection Info:")
        Text("IP: ${smbFileContext?.ipAddress}")
        Text("Share: ${smbFileContext?.shareName}")
        Text("Username: ${smbFileContext?.username}")
        if (smbFileContext?.password == null || smbFileContext!!.password!!.isBlank()) {
            Text("Password: (blank)")
        } else {
            Text("Password: (hidden)")
        }
        Text("Backup Directory: ${smbFileContext?.route}")

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            parentVM.initiateBackup(context)
            // If successful, go back to main menu
            nav.popBackStack(route = NavRoutes.MAIN_MENU, inclusive = false)
        }) {
            Text("Backup Now")
        }
    }
}
