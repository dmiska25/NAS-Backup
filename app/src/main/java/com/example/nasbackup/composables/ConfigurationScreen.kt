package com.example.nasbackup.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

@Composable
fun ConfigurationScreen(nav: NavHostController) {
    Column {
        PageHeader(title = "Backup Configuration", onBack = { nav.navigate(NavRoutes.MAIN_MENU) })
        Button(onClick = { nav.navigate(NavRoutes.CONFIGURATION_SELECT_FILES) }) {
            Text("Files")
        }
    }
}
