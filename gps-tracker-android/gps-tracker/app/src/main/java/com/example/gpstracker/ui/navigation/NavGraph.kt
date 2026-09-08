package com.example.gpstracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.gpstracker.ui.dashboard.DashboardScreen
import com.example.gpstracker.ui.history.HistoryScreen
import com.example.gpstracker.ui.history.TripDetailScreen
import com.example.gpstracker.ui.offlinemaps.OfflineMapsScreen
import com.example.gpstracker.ui.settings.SettingsScreen

private object Routes {
    const val DASHBOARD = "dashboard"
    const val HISTORY = "history"
    const val TRIP_DETAIL = "trip_detail/{tripId}"
    const val SETTINGS = "settings"
    const val OFFLINE_MAPS = "offline_maps"
    fun tripDetail(tripId: Long) = "trip_detail/$tripId"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { BottomBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            composable(Routes.DASHBOARD) { DashboardScreen() }
            composable(Routes.HISTORY) {
                HistoryScreen(onTripClick = { tripId ->
                    navController.navigate(Routes.tripDetail(tripId))
                })
            }
            composable(Routes.TRIP_DETAIL) { backStackEntry ->
                val tripId = backStackEntry.arguments?.getString("tripId")?.toLongOrNull() ?: return@composable
                TripDetailScreen(tripId = tripId)
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.OFFLINE_MAPS) { OfflineMapsScreen() }
        }
    }
}

@Composable
private fun BottomBar(navController: androidx.navigation.NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        NavigationBarItem(
            selected = currentDestination?.hierarchy?.any { it.route == Routes.DASHBOARD } == true,
            onClick = {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Дашборд") }
        )
        NavigationBarItem(
            selected = currentDestination?.hierarchy?.any { it.route == Routes.HISTORY } == true,
            onClick = {
                navController.navigate(Routes.HISTORY) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
            label = { Text("История") }
        )
        NavigationBarItem(
            selected = currentDestination?.hierarchy?.any { it.route == Routes.OFFLINE_MAPS } == true,
            onClick = {
                navController.navigate(Routes.OFFLINE_MAPS) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
            label = { Text("Карты") }
        )
        NavigationBarItem(
            selected = currentDestination?.hierarchy?.any { it.route == Routes.SETTINGS } == true,
            onClick = {
                navController.navigate(Routes.SETTINGS) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("Настройки") }
        )
    }
}
