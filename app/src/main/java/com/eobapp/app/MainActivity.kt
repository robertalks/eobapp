package com.eobapp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15+ forces edge-to-edge for apps targeting API 35 or higher. Opting in
        // explicitly gives consistent system-bar styling on older versions too; each screen's
        // Scaffold already pads content clear of the bars.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
