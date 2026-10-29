package com.ritam.azureload

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ritam.azureload.core.model.DownloadItem
import com.ritam.azureload.core.model.DownloadStatus
import com.ritam.azureload.feature.add.AddDownloadScreen
import com.ritam.azureload.feature.browser.BrowserScreen
import com.ritam.azureload.feature.dashboard.DashboardScreen
import com.ritam.azureload.feature.detail.DetailScreen
import com.ritam.azureload.feature.diagnostics.DiagnosticsScreen
import com.ritam.azureload.feature.settings.SettingsScreen
import com.ritam.azureload.ui.navigation.Screen
import com.ritam.azureload.ui.theme.AzureloadTheme
import kotlinx.coroutines.launch
import java.net.URLDecoder

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AzureloadApp
        val repository = app.repository
        val coordinator = app.downloadCoordinator
        val stagingManager = app.stagingFileManager

        // Extract candidate URL from share intent if launched via Send intent
        val sharedUrl = extractUrlFromIntent(intent)

        setContent {
            AzureloadTheme {
                // Request Notification Permission on Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { _ -> }

                    LaunchedEffect(Unit) {
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val downloads by repository.getAllDownloads().collectAsState(initial = emptyList())
                    val speeds by coordinator.speeds.collectAsState()
                    val coroutineScope = rememberCoroutineScope()

                    // If shared from another app, navigate to Add screen directly
                    LaunchedEffect(sharedUrl) {
                        if (!sharedUrl.isNullOrBlank()) {
                            navController.navigate(Screen.Add.createRoute(sharedUrl))
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Dashboard.route
                    ) {
                        composable(Screen.Dashboard.route) {
                            DashboardScreen(
                                downloads = downloads,
                                speeds = speeds,
                                onNavigateToAdd = { navController.navigate(Screen.Add.createRoute()) },
                                onNavigateToBrowser = { navController.navigate(Screen.Browser.createRoute()) },
                                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                                onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
                                onNavigateToDetail = { id -> navController.navigate(Screen.Detail.createRoute(id)) },
                                onPause = { coordinator.pauseDownload(it) },
                                onResume = { coordinator.resumeDownload(it) },
                                onCancel = { coordinator.cancelDownload(it) },
                                onDelete = { coordinator.cancelDownload(it) }
                            )
                        }

                        composable(
                            route = Screen.Add.route,
                            arguments = listOf(navArgument("url") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            })
                        ) { backStackEntry ->
                            val rawUrl = backStackEntry.arguments?.getString("url")
                            val decodedUrl = rawUrl?.let {
                                try { URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                            }

                            AddDownloadScreen(
                                initialUrl = decodedUrl,
                                onBack = { navController.popBackStack() },
                                onSubmitDownload = { url, fileName, concurrency, headers, sha256 ->
                                    coroutineScope.launch {
                                        val newDownload = DownloadItem(
                                            url = url,
                                            fileName = fileName,
                                            concurrency = concurrency,
                                            customHeaders = headers,
                                            expectedSha256 = sha256,
                                            destinationPath = "",
                                            stagingPath = "",
                                            status = DownloadStatus.QUEUED
                                        )
                                        val id = repository.insertDownload(newDownload)
                                        coordinator.startDownload(id)
                                    }
                                }
                            )
                        }

                        composable(
                            route = Screen.Browser.route,
                            arguments = listOf(navArgument("url") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            })
                        ) { backStackEntry ->
                            val rawUrl = backStackEntry.arguments?.getString("url")
                            val decodedUrl = rawUrl?.let {
                                try { URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                            }

                            BrowserScreen(
                                initialUrl = decodedUrl,
                                onBack = { navController.popBackStack() },
                                onInterceptDownload = { downloadUrl, cookie, userAgent, _ ->
                                    navController.navigate(Screen.Add.createRoute(downloadUrl))
                                }
                            )
                        }

                        composable(
                            route = Screen.Detail.route,
                            arguments = listOf(navArgument("id") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getLong("id") ?: -1L
                            val download = downloads.firstOrNull { it.id == id }
                            val chunks by repository.observeChunksForDownload(id).collectAsState(initial = emptyList())
                            val currentSpeed = speeds[id] ?: 0L

                            DetailScreen(
                                download = download,
                                chunks = chunks,
                                speed = currentSpeed,
                                onBack = { navController.popBackStack() },
                                onPause = { coordinator.pauseDownload(it) },
                                onResume = { coordinator.resumeDownload(it) },
                                onCancel = { coordinator.cancelDownload(it) }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }

                        composable(Screen.Diagnostics.route) {
                            DiagnosticsScreen(
                                stagingDir = stagingManager.getStagingDirectory(),
                                activeJobsCount = coordinator.getActiveJobCount(),
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        if (Intent.ACTION_SEND == intent.action && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            // Extract URL from text (handles both direct URLs and URLs embedded in shared messages)
            val matcher = Regex("https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]+").find(text)
            return matcher?.value
        }
        return null
    }
}
