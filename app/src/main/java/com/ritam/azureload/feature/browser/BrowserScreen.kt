package com.ritam.azureload.feature.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ritam.azureload.ui.components.LiquidGlassCard
import com.ritam.azureload.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    initialUrl: String? = null,
    onBack: () -> Unit,
    onInterceptDownload: (url: String, cookie: String?, userAgent: String?, contentDisposition: String?) -> Unit
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var currentUrl by remember { mutableStateOf(initialUrl ?: "https://www.google.com") }
    var urlInput by remember { mutableStateOf(currentUrl) }
    var pageTitle by remember { mutableStateOf("Browser") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableStateOf(0) }

    // Memory Leak Protection: Guarantee cleanup and destruction of WebView upon composition disposal
    DisposableEffect(Unit) {
        onDispose {
            try {
                webView?.apply {
                    stopLoading()
                    setDownloadListener(null)
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadUrl("about:blank")
                    removeAllViews()
                    destroy()
                }
            } catch (_: Exception) {}
            webView = null
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // Liquid Glass Address Bar
                val addressShape = remember { SquircleShape(18.dp) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(addressShape)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(Color(0x28FFFFFF), Color(0x0E1A293E))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x12FFFFFF))),
                            addressShape
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Browser", tint = AzureOnSurface, modifier = Modifier.size(20.dp))
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        singleLine = true,
                        placeholder = { Text("Search or type URL...", fontSize = 13.sp, color = AzureOnSurfaceVariant) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = AzureOnSurface),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        )
                    )

                    IconButton(
                        onClick = {
                            var dest = urlInput.trim()
                            if (!dest.startsWith("http://") && !dest.startsWith("https://")) {
                                dest = "https://$dest"
                            }
                            webView?.loadUrl(dest)
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(SquircleShape(10.dp))
                            .background(AzurePrimary.copy(alpha = 0.25f))
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Go", tint = AzurePrimary, modifier = Modifier.size(18.dp))
                    }
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { loadingProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(SquircleShape(2.dp)),
                        color = NeonCyan,
                        trackColor = Color(0x20FFFFFF)
                    )
                }
            }
        },
        bottomBar = {
            // Floating Liquid Glass Navigation Capsule Dock
            val dockShape = remember { SquircleShape(26.dp) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(dockShape)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(Color(0x3517223A), Color(0x250C1322))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(Color(0x60FFFFFF), Color(0x15FFFFFF))),
                            dockShape
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { webView?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = if (canGoBack) AzureOnSurface else AzureOnSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }

                    IconButton(
                        onClick = { webView?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (canGoForward) AzureOnSurface else AzureOnSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }

                    IconButton(
                        onClick = { webView?.reload() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = AzureOnSurface)
                    }

                    // Sniff / Intercept download action button
                    IconButton(
                        onClick = {
                            val urlToDownload = webView?.url
                            if (!urlToDownload.isNullOrBlank()) {
                                val cookie = CookieManager.getInstance().getCookie(urlToDownload)
                                val userAgent = webView?.settings?.userAgentString
                                onInterceptDownload(urlToDownload, cookie, userAgent, null)
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(SquircleShape(12.dp))
                            .background(AzurePrimary.copy(alpha = 0.22f))
                            .border(1.dp, AzurePrimary.copy(alpha = 0.5f), SquircleShape(12.dp))
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download Current Page",
                            tint = AzurePrimary
                        )
                    }
                }
            }
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true

                    setDownloadListener { url, userAgent, contentDisposition, _, _ ->
                        val cookie = CookieManager.getInstance().getCookie(url)
                        onInterceptDownload(url, cookie, userAgent, contentDisposition)
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val reqUrl = request?.url?.toString() ?: return false
                            val path = request.url?.path?.lowercase() ?: ""
                            val downloadExtensions = listOf(
                                ".apk", ".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", ".xz",
                                ".iso", ".dmg", ".bin", ".exe", ".msi", ".pdf", ".mp4", ".mkv", ".mp3"
                            )
                            if (downloadExtensions.any { path.endsWith(it) }) {
                                val cookie = CookieManager.getInstance().getCookie(reqUrl)
                                val userAgent = view?.settings?.userAgentString
                                onInterceptDownload(reqUrl, cookie, userAgent, null)
                                return true
                            }
                            return false
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            url?.let {
                                currentUrl = it
                                urlInput = it
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            canGoBack = canGoBack()
                            canGoForward = canGoForward()
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadingProgress = newProgress
                            isLoading = newProgress < 100
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            title?.let { pageTitle = it }
                        }
                    }

                    loadUrl(currentUrl)
                    webView = this
                }
            },
            update = { view ->
                webView = view
            },
            onRelease = { view ->
                try {
                    view.stopLoading()
                    view.setDownloadListener(null)
                    view.webViewClient = WebViewClient()
                    view.webChromeClient = WebChromeClient()
                    view.loadUrl("about:blank")
                    view.removeAllViews()
                    view.destroy()
                } catch (_: Exception) {}
            }
        )
    }
}
