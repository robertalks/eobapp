package com.eobapp.app

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.Surface
import com.eobapp.app.ui.theme.LocalEobColors
import com.eobapp.app.ui.screens.AppSplashScreen
import com.eobapp.app.ui.screens.HomeScreen
import com.eobapp.app.ui.screens.SettingsScreen
import com.eobapp.app.ui.screens.ShellyDetailScreen
import com.eobapp.app.network.PowerDeviceStatus
import com.eobapp.app.ui.theme.EobTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Permission granted or denied — either way the worker is already scheduled and will
            // skip posting if the permission is absent.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        OverrideMonitorWorker.schedule(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            EobTheme {
                val c = LocalEobColors.current
                Surface(color = c.ground) {
                    EobApp(viewModel)
                }
            }
        }
    }
}

/** Home if the app knows where the scheduler is, otherwise Settings first. */
@Composable
fun EobApp(viewModel: MainViewModel) {
    val state by viewModel.home.collectAsState()
    val navController = rememberNavController()
    // Decided once: changing a NavHost's start destination later would rebuild its graph.
    val start = remember { if (viewModel.settings.isConfigured()) "home" else "settings" }

    // Show the splash until the first load resolves (status or error), but only when
    // the app is configured — unconfigured goes straight to Settings.
    val showSplash = viewModel.settings.isConfigured() &&
        state.status == null && state.error == null

    if (showSplash) {
        AppSplashScreen()
        return
    }

    NavHost(navController = navController, startDestination = start) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate("settings") { launchSingleTop = true } },
                onNavigateToShelly = { name ->
                    navController.navigate("shelly/${Uri.encode(name)}")
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                // On first launch there's nothing behind Settings to go back to.
                canGoBack = navController.previousBackStackEntry != null,
                onBack = { navController.popBackStack() },
                onSaved = {
                    // Whether opened from Home or as the first screen, end with Home alone on
                    // the stack, so Back from Home leaves the app.
                    navController.navigate("home") {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }
        composable("shelly/{name}") { backStackEntry ->
            val name = Uri.decode(backStackEntry.arguments?.getString("name") ?: "")
            val state by viewModel.home.collectAsState()
            val device = state.powerDevices.firstOrNull { it.name == name }
            if (device != null) {
                ShellyDetailScreen(
                    device = device,
                    dailyAll = state.powerDaily,
                    loading = state.loading,
                    onBack = { navController.popBackStack() },
                    onRefresh = { viewModel.refreshPowerDevice30(name) }
                )
            } else {
                ShellyDetailScreen(
                    device = PowerDeviceStatus(name = name),
                    dailyAll = emptyList(),
                    loading = state.loading,
                    onBack = { navController.popBackStack() },
                    onRefresh = { viewModel.refreshPowerDevice30(name) }
                )
            }
        }
    }
}
