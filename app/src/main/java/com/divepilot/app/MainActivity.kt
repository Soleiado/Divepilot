package com.divepilot.app

import android.content.pm.ActivityInfo
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.divepilot.app.util.LocaleManager
import com.divepilot.app.ble.BleCommand
import com.divepilot.app.ble.BleManager
import com.divepilot.app.ble.DecodedFrame
import com.divepilot.app.ui.BleCaptureScreen
import com.divepilot.app.ui.CameraScreen
import com.divepilot.app.ui.ColorCorrectionScreen
import com.divepilot.app.ui.CompassScreen
import com.divepilot.app.ui.DiveComputerSettingsScreen
import com.divepilot.app.ui.GalleryScreen
import com.divepilot.app.ui.GuideScreen
import com.divepilot.app.ui.JournalDetailScreen
import com.divepilot.app.ui.JournalScreen
import com.divepilot.app.ui.SettingsScreen
import com.divepilot.app.ui.theme.DivePilotTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Empêche la mise en veille pendant l'utilisation de l'application.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            DivePilotTheme {
                val navController = rememberNavController()
                val activity = LocalActivity.current
                val backStackEntry by navController.currentBackStackEntryAsState()

                // Caméra et boussole : paysage + barres système masquées (immersif).
                LaunchedEffect(backStackEntry?.destination?.route) {
                    val route = backStackEntry?.destination?.route
                    activity?.requestedOrientation = when (route) {
                        "camera", "compass" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                    activity?.let { act ->
                        val controller = WindowInsetsControllerCompat(act.window, act.window.decorView)
                        if (route == "camera" || route == "compass") {
                            controller.hide(WindowInsetsCompat.Type.systemBars())
                            controller.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        } else {
                            controller.show(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                }

                // Navigation déclenchée par les boutons du boîtier.
                LaunchedEffect(Unit) {
                    BleManager.get(applicationContext).frames.collect { frame ->
                        if (frame is DecodedFrame.Command) {
                            val current = navController.currentBackStackEntry?.destination?.route
                            when (frame.command) {
                                BleCommand.SHUTTER ->
                                    if (current != "camera") {
                                        navController.navigate("camera") { launchSingleTop = true }
                                    }
                                BleCommand.COMPASS ->
                                    if (current != "compass") {
                                        navController.navigate("compass") { launchSingleTop = true }
                                    }
                                else -> Unit
                            }
                        }
                    }
                }

                NavHost(navController = navController, startDestination = "capture") {
                    composable("capture") {
                        BleCaptureScreen(
                            onOpenCamera = { navController.navigate("camera") },
                            onOpenJournal = { navController.navigate("journal") },
                            onOpenGallery = { navController.navigate("gallery") },
                            onOpenGuide = { navController.navigate("guide") },
                            onOpenColorCorrection = { navController.navigate("colorCorrection") },
                            onOpenSettings = { navController.navigate("settings") },
                        )
                    }
                    composable("camera") {
                        CameraScreen(
                            onBack = { navController.popBackStack() },
                            onOpenCompass = { navController.navigate("compass") { launchSingleTop = true } },
                        )
                    }
                    composable("compass") {
                        CompassScreen(onBack = { navController.popBackStack() })
                    }
                    composable("gallery") {
                        GalleryScreen(onBack = { navController.popBackStack() })
                    }
                    composable("guide") {
                        GuideScreen(onBack = { navController.popBackStack() })
                    }
                    composable("colorCorrection") {
                        ColorCorrectionScreen(onBack = { navController.popBackStack() })
                    }
                    composable("journal") {
                        JournalScreen(
                            onBack = { navController.popBackStack() },
                            onOpenDetail = { id -> navController.navigate("journalDetail/$id") },
                        )
                    }
                    composable(
                        route = "journalDetail/{logId}",
                        arguments = listOf(navArgument("logId") { type = NavType.LongType }),
                    ) { entry ->
                        val logId = entry.arguments?.getLong("logId") ?: 0L
                        JournalDetailScreen(logId = logId, onBack = { navController.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenDiveComputer = { navController.navigate("diveComputerSettings") },
                        )
                    }
                    composable("diveComputerSettings") {
                        DiveComputerSettingsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
