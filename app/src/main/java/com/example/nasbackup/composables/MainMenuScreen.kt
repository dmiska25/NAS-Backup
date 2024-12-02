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
        Button(onClick = { nav.navigate(NavRoutes.CONFIGURATION) }) {
            Text("Configuration")
        }
    }
}
