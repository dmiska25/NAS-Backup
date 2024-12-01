package com.example.nasbackup.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun MainMenuScreen(onNavigate: (String) -> Unit) {
    Column {
        Text("Main Menu")
        Button(onClick = { onNavigate("Configuration") }) {
            Text("Configuration")
        }
        Button(onClick = { onNavigate("BackupNow") }) {
            Text("Backup Now")
        }
        Button(onClick = { onNavigate("Restore") }) {
            Text("Restore")
        }
    }
}
