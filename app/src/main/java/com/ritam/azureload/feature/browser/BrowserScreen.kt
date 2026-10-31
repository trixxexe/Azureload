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
import com.ritam.azureload.ui.components.LiquidGlassIconButton
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

    var isDestroyed by remember { mutableStateOf(false) }

    val destroyWebViewSafely: (WebView?) -> Unit = remember {
        { view ->
            if (view != null && !isDestroyed) {
                isDestroyed = true
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
        }
    }

    // Memory Leak Protection: Guarantee cleanup and destruction of WebView upon composition disposal
    DisposableEffect(Unit) {
        onDispose {
            destroyWebViewSafely(webView)
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
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                // Liquid Glass Address Bar
                val addressShape = remember { SquircleShape(18.dp) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(addressShape)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(Color(0x25FFFFFF), Color(0x0AFFFFFF))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(Color(0x60FFFFFF), Color(0x12FFFFFF))),
                            addressShape
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassIconButton(
                        onClick = onBack,
                        icon = Icons.Default.Close,
                        contentDescription = "Close Browser",
                        modifier = Modifier.size(34.dp),
                        cornerRadius = 10.dp
                    )

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        singleLine = true,
                        placeholder = { Text("Search or type URL...", fontSize = 13.sp, color = TextMuted) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        )
                    )

                    LiquidGlassIconButton(
                        onClick = {
                            var dest = urlInput.trim()
                            if (!dest.startsWith("http://") && !dest.startsWith("https://")) {
                                dest = "https://$dest"
                            }
                            webView?.loadUrl(dest)
                        },
                        icon = Icons.Default.ArrowForward,
                        contentDescription = "Go",
                        modifier = Modifier.size(34.dp),
                        tint = PureBlack,
                        accentBg = ElectricCyan,
                        cornerRadius = 10.dp
                    )
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(SquircleShape(2.dp))
                            .background(Color(0x18FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(loadingProgress / 100f)
                                .fillMaxHeight()
                                .clip(SquircleShape(2.dp))
                                .background(
                                    Brush.horizontalGradient(listOf(ElectricCyan, VibrantAzure))
                                )
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Floating Liquid Glass Navigation Capsule Dock
            val dockShape = remember { SquircleShape(28.dp) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(dockShape)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(Color(0x38182236), Color(0x220E1524))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(Color(0x65FFFFFF), Color(0x14FFFFFF))),
                            dockShape
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassIconButton(
                        onClick = { webView?.goBack() },
                        icon = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(40.dp),
                        tint = if (canGoBack) TextPrimary else TextMuted
                    )

                    LiquidGlassIconButton(
                        onClick = { webView?.goForward() },
                        icon = Icons.Default.ArrowForward,
                        contentDescription = "Forward",
                        modifier = Modifier.size(40.dp),
                        tint = if (canGoForward) TextPrimary else TextMuted
                    )

                    LiquidGlassIconButton(
                        onClick = { webView?.reload() },
                        icon = Icons.Default.Refresh,
                        contentDescription = "Reload",
                        modifier = Modifier.size(40.dp),
                        tint = TextPrimary
                    )

                    // Sniff / Intercept download action button
                    LiquidGlassIconButton(
                        onClick = {
                            val urlToDownload = webView?.url
                            if (!urlToDownload.isNullOrBlank()) {
                                val cookie = CookieManager.getInstance().getCookie(urlToDownload)
                                val userAgent = webView?.settings?.userAgentString
                                onInterceptDownload(urlToDownload, cookie, userAgent, null)
                            }
                        },
                        icon = Icons.Default.Download,
                        contentDescription = "Download Current Page",
                        modifier = Modifier.size(40.dp),
                        tint = PureBlack,
                        accentBg = ElectricCyan,
                        cornerRadius = 12.dp
                    )
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
                destroyWebViewSafely(view)
                webView = null
            }
        )
    }
}
