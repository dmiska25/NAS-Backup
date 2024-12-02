package com.example.nasbackup.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation

@Composable
fun AppNavigation(modifier: Modifier) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = NavRoutes.MAIN_MENU, modifier = modifier) {
        composable(NavRoutes.MAIN_MENU) {
            MainMenuScreen(nav = navController)
        }
        navigation(startDestination = NavRoutes.CONFIGURATION_MAIN, route = NavRoutes.CONFIGURATION) {
            composable(NavRoutes.CONFIGURATION_MAIN) {
                ConfigurationScreen(nav = navController)
            }
            composable(NavRoutes.CONFIGURATION_SELECT_FILES) {
                FileSelectionScreen(nav = navController)
            }
        }
    }
}

object NavRoutes {
    const val MAIN_MENU = "MAIN_MENU"
    const val CONFIGURATION = "CONFIGURATION"
    const val CONFIGURATION_MAIN = "CONFIGURATION_MAIN"
    const val CONFIGURATION_SELECT_FILES = "CONFIGURATION_SELECT_FILES"
}
