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

    NavHost(navController = navController, startDestination = NavRoutes.MAIN_MENU.name, modifier = modifier) {
        composable(NavRoutes.MAIN_MENU.name) {
            MainMenuScreen(nav = navController)
        }
        navigation(startDestination = NavRoutes.CONFIGURATION_MAIN.name, route = NavRoutes.CONFIGURATION.name) {
            composable(NavRoutes.CONFIGURATION_MAIN.name) {
                ConfigurationScreen(nav = navController)
            }
            composable(NavRoutes.CONFIGURATION_SELECT_FILES.name) {
                FileSelectionScreen(nav = navController)
            }
        }
    }
}

enum class NavRoutes(route: String) {
    MAIN_MENU("MainMenu"),
    CONFIGURATION("Configuration"),
    CONFIGURATION_MAIN("ConfigurationMain"),
    CONFIGURATION_SELECT_FILES("SelectFiles")
}
