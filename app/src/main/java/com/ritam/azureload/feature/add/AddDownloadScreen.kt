package com.ritam.azureload.feature.add

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritam.azureload.core.model.ServerCapability
import com.ritam.azureload.core.network.CapabilityDetector
import com.ritam.azureload.core.network.ErrorClassifier
import com.ritam.azureload.core.storage.FileNameSanitizer
import com.ritam.azureload.feature.dashboard.formatBytes
import com.ritam.azureload.feature.dashboard.formatSpeed
import com.ritam.azureload.ui.components.LiquidGlassButton
import com.ritam.azureload.ui.components.LiquidGlassCard
import com.ritam.azureload.ui.components.LiquidGlassErrorCard
import com.ritam.azureload.ui.components.LiquidGlassPill
import com.ritam.azureload.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadScreen(
    initialUrl: String? = null,
    initialCookie: String? = null,
    initialUserAgent: String? = null,
    initialFileName: String? = null,
    defaultConcurrency: Int = 4,
    onBack: () -> Unit,
    onSubmitDownload: (url: String, fileName: String, concurrency: Int, headers: Map<String, String>, sha256: String?, speedLimit: Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val prefilledHeaders = remember(initialCookie, initialUserAgent) {
        val list = mutableListOf<String>()
        if (!initialCookie.isNullOrBlank()) list.add("Cookie: $initialCookie")
        if (!initialUserAgent.isNullOrBlank()) list.add("User-Agent: $initialUserAgent")
        list.joinToString("\n")
    }

    var url by remember(initialUrl) { mutableStateOf(initialUrl.orEmpty()) }
    var fileName by remember(initialFileName) { mutableStateOf(initialFileName.orEmpty()) }
    var concurrency by remember(defaultConcurrency) { mutableStateOf(defaultConcurrency.coerceIn(1, 16).toFloat()) }
    var customHeadersText by remember(prefilledHeaders) { mutableStateOf(prefilledHeaders) }
    var expectedSha256 by remember { mutableStateOf("") }
    var speedLimitBytes by remember { mutableStateOf(0L) }

    var isInspecting by remember { mutableStateOf(false) }
    var capabilityResult by remember { mutableStateOf<ServerCapability?>(null) }
    var inspectionError by remember { mutableStateOf<String?>(null) }

    val capabilityDetector = remember { CapabilityDetector() }

    fun inspectUrl() {
        if (url.isBlank()) return
        isInspecting = true
        inspectionError = null
        coroutineScope.launch {
            try {
                val headers = parseHeaders(customHeadersText)
                val cap = capabilityDetector.detect(url, headers)
                capabilityResult = cap
                if (fileName.isBlank() && !cap.suggestedFileName.isNullOrBlank()) {
                    fileName = FileNameSanitizer.sanitize(cap.suggestedFileName)
                }
            } catch (e: Exception) {
                val classified = ErrorClassifier.classify(e)
                inspectionError = classified.toDisplayString()
            } finally {
                isInspecting = false
            }
        }
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            inspectUrl()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Add Download", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = AzureOnSurface,
                    navigationIconContentColor = AzureOnSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Source URL Card
            LiquidGlassCard(cornerRadius = 20.dp) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Source Link",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AzurePrimary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://example.com/file.zip", color = AzureOnSurfaceVariant, fontSize = 13.sp) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    url = clip
                                    inspectUrl()
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = AzurePrimary)
                            }
                        },
                        singleLine = true,
                        shape = SquircleShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0x10FFFFFF),
                            focusedContainerColor = Color(0x1EFFFFFF),
                            unfocusedBorderColor = Color(0x25FFFFFF),
                            focusedBorderColor = AzurePrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LiquidGlassButton(
                        onClick = { inspectUrl() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = url.isNotBlank() && !isInspecting,
                        isSolid = true,
                        accentColor = AzurePrimary,
                        cornerRadius = 14.dp
                    ) {
                        if (isInspecting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PureBlack)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Probing Server Capabilities...", color = PureBlack, fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = PureBlack)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Inspect Server Capabilities", color = PureBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Capability preview
            AnimatedVisibility(visible = capabilityResult != null || inspectionError != null) {
                if (inspectionError != null) {
                    val classified = remember(inspectionError) {
                        ErrorClassifier.parseOrClassify(inspectionError)
                    }
                    LiquidGlassErrorCard(
                        error = classified,
                        onRetry = { inspectUrl() }
                    )
                } else capabilityResult?.let { cap ->
                    LiquidGlassCard(
                        cornerRadius = 18.dp,
                        isHighlighted = true,
                        highlightColor = if (cap.supportsRange) AzureSuccess else AzureSecondary
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LiquidGlassPill(
                                    tint = if (cap.supportsRange) AzureSuccess else AzureSecondary,
                                    cornerRadius = 8.dp
                                ) {
                                    Icon(
                                        imageVector = if (cap.supportsRange) Icons.Default.Speed else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (cap.supportsRange) AzureSuccess else AzureSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (cap.supportsRange) "Byte-Range Supported (Multi-connection enabled)" else "Single Connection (Ranges unsupported)",
                                        fontWeight = FontWeight.Bold,
                                        color = if (cap.supportsRange) AzureSuccess else AzureSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Detected Size: " + (if (cap.contentLength > 0) formatBytes(cap.contentLength) else "Unknown (Dynamic stream)"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AzureOnSurface
                            )
                            if (cap.eTag != null) {
                                Text(text = "ETag: ${cap.eTag}", fontSize = 11.sp, color = AzureOnSurfaceVariant)
                            }
                            if (cap.contentType != null) {
                                Text(text = "MIME: ${cap.contentType}", fontSize = 11.sp, color = AzureOnSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Filename Input
            LiquidGlassCard(cornerRadius = 18.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Target File Name", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AzurePrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g. document.pdf", color = AzureOnSurfaceVariant, fontSize = 13.sp) },
                        shape = SquircleShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0x10FFFFFF),
                            focusedContainerColor = Color(0x1EFFFFFF),
                            unfocusedBorderColor = Color(0x25FFFFFF),
                            focusedBorderColor = AzurePrimary
                        )
                    )
                }
            }

            // Concurrency Slider
            LiquidGlassCard(cornerRadius = 18.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Concurrency Connections", fontWeight = FontWeight.SemiBold, color = AzureOnSurface)
                        Text("${concurrency.toInt()} Threads", fontWeight = FontWeight.Bold, color = NeonCyan)
                    }
                    Slider(
                        value = concurrency,
                        onValueChange = { concurrency = it },
                        valueRange = 1f..16f,
                        steps = 14,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = AzurePrimary
                        )
                    )
                    Text(
                        text = "Divides known-size files into non-overlapping byte ranges. Conservative default is 4 threads.",
                        fontSize = 11.sp,
                        color = AzureOnSurfaceVariant
                    )
                }
            }

            // Speed Throttle Option Card
            LiquidGlassCard(cornerRadius = 18.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Download Speed Throttle", fontWeight = FontWeight.SemiBold, color = AzureOnSurface)
                        Text(
                            text = if (speedLimitBytes > 0) formatSpeed(speedLimitBytes) else "Unlimited",
                            fontWeight = FontWeight.Bold,
                            color = if (speedLimitBytes > 0) AzureSecondary else AzurePrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            "Unlimited" to 0L,
                            "512 KB/s" to 512L * 1024L,
                            "1 MB/s" to 1L * 1024L * 1024L,
                            "2 MB/s" to 2L * 1024L * 1024L,
                            "5 MB/s" to 5L * 1024L * 1024L
                        )
                        presets.forEach { (label, bytes) ->
                            val isSelected = speedLimitBytes == bytes
                            LiquidGlassPill(
                                tint = if (isSelected) AzurePrimary else null,
                                isSolid = isSelected,
                                cornerRadius = 10.dp,
                                onClick = { speedLimitBytes = bytes }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) PureBlack else AzureOnSurface
                                )
                            }
                        }
                    }
                }
            }

            // Advanced: Custom Headers
            LiquidGlassCard(cornerRadius = 18.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Custom Headers (Optional)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AzurePrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customHeadersText,
                        onValueChange = { customHeadersText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Authorization: Bearer xyz\nCookie: session=123", color = AzureOnSurfaceVariant, fontSize = 12.sp) },
                        minLines = 2,
                        maxLines = 4,
                        shape = SquircleShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0x10FFFFFF),
                            focusedContainerColor = Color(0x1EFFFFFF),
                            unfocusedBorderColor = Color(0x25FFFFFF),
                            focusedBorderColor = AzurePrimary
                        )
                    )
                }
            }

            // Advanced: Expected SHA-256
            LiquidGlassCard(cornerRadius = 18.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Expected SHA-256 Checksum (Optional)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AzurePrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = expectedSha256,
                        onValueChange = { expectedSha256 = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", color = AzureOnSurfaceVariant, fontSize = 11.sp) },
                        singleLine = true,
                        shape = SquircleShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0x10FFFFFF),
                            focusedContainerColor = Color(0x1EFFFFFF),
                            unfocusedBorderColor = Color(0x25FFFFFF),
                            focusedBorderColor = AzurePrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Submit Button
            LiquidGlassButton(
                onClick = {
                    if (url.isBlank()) return@LiquidGlassButton
                    val finalName = if (fileName.isNotBlank()) {
                        FileNameSanitizer.sanitize(fileName)
                    } else {
                        FileNameSanitizer.sanitize(CapabilityDetector.extractFileName(null, url))
                    }
                    val headers = parseHeaders(customHeadersText)
                    val sha = expectedSha256.trim().ifBlank { null }
                    onSubmitDownload(url, finalName, concurrency.toInt(), headers, sha, speedLimitBytes)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                cornerRadius = 16.dp,
                enabled = url.isNotBlank(),
                isSolid = true,
                accentColor = AzurePrimary
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = PureBlack, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Download", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = PureBlack)
            }
        }
    }
}

private fun parseHeaders(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return raw.lines()
        .mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            else null
        }.toMap()
}
