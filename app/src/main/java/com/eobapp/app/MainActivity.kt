package com.eobapp.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.eobapp.app.ui.screens.HomeScreen
import com.eobapp.app.ui.screens.SettingsScreen
import com.eobapp.app.ui.theme.EobTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Permission granted or denied — either way the worker is already scheduled and will
            // skip posting if the permission is absent.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        OverrideMonitorWorker.schedule(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            EobTheme {
                EobApp(viewModel)
            }
        }
    }
}

/** Home if the app knows where the scheduler is, otherwise Settings first. */
@Composable
fun EobApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    // Decided once: changing a NavHost's start destination later would rebuild its graph.
    val start = remember { if (viewModel.settings.isConfigured()) "home" else "settings" }

    NavHost(navController = navController, startDestination = start) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate("settings") { launchSingleTop = true } }
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
    }
}
