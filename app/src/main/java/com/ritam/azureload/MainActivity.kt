package com.ritam.azureload

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
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
import androidx.core.content.FileProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ritam.azureload.core.model.DownloadItem
import com.ritam.azureload.core.model.DownloadStatus
import com.ritam.azureload.core.network.CapabilityDetector
import com.ritam.azureload.feature.add.AddDownloadScreen
import com.ritam.azureload.feature.browser.BrowserScreen
import com.ritam.azureload.feature.dashboard.DashboardScreen
import com.ritam.azureload.feature.detail.DetailScreen
import com.ritam.azureload.feature.diagnostics.DiagnosticsScreen
import com.ritam.azureload.feature.settings.SettingsScreen
import com.ritam.azureload.ui.navigation.Screen
import com.ritam.azureload.ui.theme.AzureloadTheme
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder

class MainActivity : ComponentActivity() {

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val sharedUrlState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AzureloadApp
        val repository = app.repository
        val coordinator = app.downloadCoordinator
        val stagingManager = app.stagingFileManager
        val settingsManager = app.settingsManager

        // Register network callback for Wi-Fi and connectivity change detection
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    coordinator.onNetworkStateChanged()
                }
            }
            networkCallback?.let {
                connectivityManager?.registerDefaultNetworkCallback(it)
            }
        } catch (_: Exception) {}

        // Extract candidate URL from share intent if launched via Send or View intent
        val initialUrl = extractUrlFromIntent(intent)
        if (!initialUrl.isNullOrBlank()) {
            sharedUrlState.value = initialUrl
        }

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
                    val speedHistories by coordinator.speedHistory.collectAsState()
                    val totalSpeedHistory by coordinator.totalSpeedHistory.collectAsState()
                    val defaultConcurrency by settingsManager.defaultConcurrencyFlow.collectAsState()
                    val coroutineScope = rememberCoroutineScope()
                    val sharedUrl by sharedUrlState

                    // If shared from another app, navigate to Add screen directly
                    LaunchedEffect(sharedUrl) {
                        val target = sharedUrl
                        if (!target.isNullOrBlank()) {
                            navController.navigate(Screen.Add.createRoute(url = target))
                            sharedUrlState.value = null
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
                                speedHistories = speedHistories,
                                totalSpeedHistory = totalSpeedHistory,
                                onNavigateToAdd = { navController.navigate(Screen.Add.createRoute()) },
                                onNavigateToBrowser = { navController.navigate(Screen.Browser.createRoute()) },
                                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                                onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
                                onNavigateToDetail = { id -> navController.navigate(Screen.Detail.createRoute(id)) },
                                onPause = { coordinator.pauseDownload(it) },
                                onResume = { coordinator.resumeDownload(it) },
                                onRetry = { coordinator.retryDownload(it) },
                                onCancel = { coordinator.cancelDownload(it) },
                                onDelete = { coordinator.cancelDownload(it) },
                                onPauseAll = { coordinator.pauseAll() },
                                onResumeAll = { coordinator.resumeAll() },
                                onClearCompleted = { deleteFiles -> coordinator.clearCompleted(deleteFiles) },
                                onOpenFile = { openDownloadedFile(this@MainActivity, it.destinationPath, it.mimeType) },
                                onShareFile = { shareDownloadedFile(this@MainActivity, it.destinationPath, it.mimeType) }
                            )
                        }

                        composable(
                            route = Screen.Add.route,
                            arguments = listOf(
                                navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("cookie") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("userAgent") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("fileName") { type = NavType.StringType; nullable = true; defaultValue = null }
                            )
                        ) { backStackEntry ->
                            fun decodeSafely(s: String?): String? = s?.let {
                                try { URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                            }

                            val initialUrlArg = decodeSafely(backStackEntry.arguments?.getString("url"))
                            val initialCookie = decodeSafely(backStackEntry.arguments?.getString("cookie"))
                            val initialUserAgent = decodeSafely(backStackEntry.arguments?.getString("userAgent"))
                            val initialFileName = decodeSafely(backStackEntry.arguments?.getString("fileName"))

                            AddDownloadScreen(
                                initialUrl = initialUrlArg,
                                initialCookie = initialCookie,
                                initialUserAgent = initialUserAgent,
                                initialFileName = initialFileName,
                                defaultConcurrency = defaultConcurrency,
                                onBack = { navController.popBackStack() },
                                onSubmitDownload = { url, fileName, concurrency, headers, sha256, speedLimit ->
                                    coroutineScope.launch {
                                        val newDownload = DownloadItem(
                                            url = url,
                                            fileName = fileName,
                                            concurrency = concurrency,
                                            customHeaders = headers,
                                            expectedSha256 = sha256,
                                            destinationPath = "",
                                            stagingPath = "",
                                            speedLimitBytesPerSec = speedLimit,
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
                                onInterceptDownload = { downloadUrl, cookie, userAgent, contentDisposition ->
                                    val suggestedName = CapabilityDetector.extractFileName(contentDisposition, downloadUrl)
                                    navController.navigate(Screen.Add.createRoute(
                                        url = downloadUrl,
                                        cookie = cookie,
                                        userAgent = userAgent,
                                        fileName = suggestedName
                                    ))
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
                                speedHistory = speedHistories[id] ?: emptyList(),
                                onBack = { navController.popBackStack() },
                                onPause = { coordinator.pauseDownload(it) },
                                onResume = { coordinator.resumeDownload(it) },
                                onRetry = { coordinator.retryDownload(it) },
                                onCancel = { coordinator.cancelDownload(it) },
                                onOpenFile = { openDownloadedFile(this@MainActivity, it.destinationPath, it.mimeType) },
                                onShareFile = { shareDownloadedFile(this@MainActivity, it.destinationPath, it.mimeType) },
                                onUpdateSpeedLimit = { dlId, limit -> coordinator.updateSpeedLimit(dlId, limit) }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                settingsManager = settingsManager,
                                onBack = { navController.popBackStack() }
                            )
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val extracted = extractUrlFromIntent(intent)
        if (!extracted.isNullOrBlank()) {
            sharedUrlState.value = extracted
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            try { connectivityManager?.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        if (Intent.ACTION_SEND == intent.action) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            if (!text.isNullOrBlank()) {
                val matcher = Regex("https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]+").find(text)
                return matcher?.value
            }
        } else if (Intent.ACTION_VIEW == intent.action) {
            val data = intent.dataString
            if (!data.isNullOrBlank() && (data.startsWith("http://") || data.startsWith("https://"))) {
                return data
            }
        }
        return null
    }

    private fun openDownloadedFile(context: Context, filePath: String, mimeType: String?) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File not found: $filePath", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val type = resolveMimeType(file, mimeType)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareDownloadedFile(context: Context, filePath: String, mimeType: String?) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File not found: $filePath", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val type = resolveMimeType(file, mimeType)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                this.type = type
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun resolveMimeType(file: File, mimeType: String?): String {
        val cleanMime = mimeType?.substringBefore(';')?.trim()
        if (!cleanMime.isNullOrBlank() && cleanMime != "application/octet-stream" && cleanMime != "*/*") {
            return cleanMime
        }
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (!cleanMime.isNullOrBlank()) cleanMime else "*/*"
    }
}
