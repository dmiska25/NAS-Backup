package com.example.nasbackup.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

@Composable
fun MainMenuScreen(nav: NavHostController) {
    Column {
        PageHeader(title = "Main Menu")
        // Four prominent buttons
        Button(onClick = { nav.navigate(NavRoutes.BACKUP_NOW_FLOW) }) {
            Text("Backup Now")
        }
        Button(onClick = { /* placeholder */ }) {
            Text("Schedule (Coming Soon)")
        }
        Button(onClick = { /* placeholder */ }) {
            Text("Restore (Coming Soon)")
        }
        Button(onClick = { /* placeholder */ }) {
            Text("Jobs (Coming Soon)")
        }
    }
}
