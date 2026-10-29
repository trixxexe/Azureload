package com.ritam.azureload.feature.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

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

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            trailingIcon = {
                                IconButton(onClick = {
                                    var dest = urlInput.trim()
                                    if (!dest.startsWith("http://") && !dest.startsWith("https://")) {
                                        dest = "https://$dest"
                                    }
                                    webView?.loadUrl(dest)
                                }) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Go")
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, contentDescription = "Close Browser")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { loadingProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                IconButton(
                    onClick = { webView?.goBack() },
                    enabled = canGoBack
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                IconButton(
                    onClick = { webView?.goForward() },
                    enabled = canGoForward
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val urlToDownload = webView?.url
                    if (!urlToDownload.isNullOrBlank()) {
                        val cookie = CookieManager.getInstance().getCookie(urlToDownload)
                        val userAgent = webView?.settings?.userAgentString
                        onInterceptDownload(urlToDownload, cookie, userAgent, null)
                    }
                }) {
                    Icon(Icons.Default.Download, contentDescription = "Download Current Page", tint = MaterialTheme.colorScheme.primary)
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
            }
        )
    }
}
