package com.example.nasbackup.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.example.nasbackup.views.BackupNowFlowViewModel

@Composable
fun AppNavigation(modifier: Modifier) {
    val navController = rememberNavController()

    // Single instance of the parent VM
    val backupNowFlowParentVM: BackupNowFlowViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.MAIN_MENU,
        modifier = modifier
    ) {
        // Main Menu
        composable(NavRoutes.MAIN_MENU) {
            MainMenuScreen(nav = navController)
        }

        // Backup Now Wizard
        navigation(
            startDestination = NavRoutes.BACKUP_DATA_SELECTION,
            route = NavRoutes.BACKUP_NOW_FLOW
        ) {
            composable(NavRoutes.BACKUP_DATA_SELECTION) {
                BackupDataSelectionScreen(
                    nav = navController,
                    parentVM = backupNowFlowParentVM,
                    onNext = { navController.navigate(NavRoutes.CONNECTION_SETTINGS) }
                )
            }
            composable(NavRoutes.CONNECTION_SETTINGS) {
                ConnectionSettingsScreen(
                    nav = navController,
                    parentVM = backupNowFlowParentVM,
                    onNext = { navController.navigate(NavRoutes.REVIEW_BACKUP) }
                )
            }
            composable(NavRoutes.REVIEW_BACKUP) {
                ReviewBackupScreen(
                    nav = navController,
                    parentVM = backupNowFlowParentVM
                )
            }
        }
    }
}

object NavRoutes {
    const val MAIN_MENU = "MAIN_MENU"

    // Backup Now wizard
    const val BACKUP_NOW_FLOW = "BACKUP_NOW_FLOW"
    const val BACKUP_DATA_SELECTION = "BACKUP_DATA_SELECTION"
    const val CONNECTION_SETTINGS = "CONNECTION_SETTINGS"
    const val REVIEW_BACKUP = "REVIEW_BACKUP"
}
