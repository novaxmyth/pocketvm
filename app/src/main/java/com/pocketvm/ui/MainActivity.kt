package com.pocketvm.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pocketvm.ui.screen.ApkInstallerScreen
import com.pocketvm.ui.screen.AppDrawerScreen
import com.pocketvm.ui.screen.DashboardScreen
import com.pocketvm.ui.screen.SettingsScreen
import com.pocketvm.ui.screen.VirtualPhoneScreen
import com.pocketvm.ui.theme.PocketVmTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PocketVmTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "dashboard"
                    ) {
                        composable("dashboard") {
                            DashboardScreen(
                                onOpenVm = { vmId ->
                                    navController.navigate("vm/$vmId")
                                },
                                onOpenSettings = { vmId ->
                                    navController.navigate("vm/$vmId/settings")
                                }
                            )
                        }

                        composable(
                            route = "vm/{vmId}",
                            arguments = listOf(navArgument("vmId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val vmId = backStackEntry.arguments?.getString("vmId") ?: return@composable
                            VirtualPhoneScreen(
                                vmId = vmId,
                                onNavigateToApps = { navController.navigate("vm/$vmId/apps") },
                                onNavigateToSettings = { navController.navigate("vm/$vmId/settings") },
                                onNavigateToInstall = { navController.navigate("vm/$vmId/install") },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "vm/{vmId}/apps",
                            arguments = listOf(navArgument("vmId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val vmId = backStackEntry.arguments?.getString("vmId") ?: return@composable
                            AppDrawerScreen(
                                vmId = vmId,
                                onNavigateToInstall = { navController.navigate("vm/$vmId/install") },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "vm/{vmId}/settings",
                            arguments = listOf(navArgument("vmId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val vmId = backStackEntry.arguments?.getString("vmId") ?: return@composable
                            SettingsScreen(
                                vmId = vmId,
                                onNavigateBack = { navController.popBackStack() },
                                onVmDeleted = {
                                    navController.popBackStack("dashboard", false)
                                }
                            )
                        }

                        composable(
                            route = "vm/{vmId}/install",
                            arguments = listOf(navArgument("vmId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val vmId = backStackEntry.arguments?.getString("vmId") ?: return@composable
                            ApkInstallerScreen(
                                vmId = vmId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
