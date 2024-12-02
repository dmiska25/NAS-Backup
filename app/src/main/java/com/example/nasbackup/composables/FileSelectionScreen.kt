package com.example.nasbackup.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

@Composable
fun FileSelectionScreen(nav: NavHostController) {
    Column {
        PageHeader("File Selection", onBack = { nav.navigate(NavRoutes.CONFIGURATION)})
    }
}