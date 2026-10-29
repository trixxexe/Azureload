package com.ritam.azureload.feature.add

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritam.azureload.core.model.ServerCapability
import com.ritam.azureload.core.network.CapabilityDetector
import com.ritam.azureload.core.storage.FileNameSanitizer
import com.ritam.azureload.feature.dashboard.formatBytes
import com.ritam.azureload.ui.theme.AzureError
import com.ritam.azureload.ui.theme.AzureSuccess
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
    onSubmitDownload: (url: String, fileName: String, concurrency: Int, headers: Map<String, String>, sha256: String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val prefilledHeaders = remember {
        val list = mutableListOf<String>()
        if (!initialCookie.isNullOrBlank()) list.add("Cookie: $initialCookie")
        if (!initialUserAgent.isNullOrBlank()) list.add("User-Agent: $initialUserAgent")
        list.joinToString("\n")
    }

    var url by remember { mutableStateOf(initialUrl.orEmpty()) }
    var fileName by remember { mutableStateOf(initialFileName.orEmpty()) }
    var concurrency by remember { mutableStateOf(defaultConcurrency.coerceIn(1, 16).toFloat()) }
    var customHeadersText by remember { mutableStateOf(prefilledHeaders) }
    var expectedSha256 by remember { mutableStateOf("") }

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
                inspectionError = e.message ?: "Failed to inspect remote resource"
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
        topBar = {
            TopAppBar(
                title = { Text("Add Download", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
            // URL Input Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Source Link",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://example.com/file.zip") },
                        trailingIcon = {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    url = clip
                                    inspectUrl()
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                            }
                        },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { inspectUrl() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = url.isNotBlank() && !isInspecting,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isInspecting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Probing Server Capabilities...")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Inspect Server Capabilities")
                        }
                    }
                }
            }

            // Capability preview
            AnimatedVisibility(visible = capabilityResult != null || inspectionError != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (inspectionError != null) AzureError.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (inspectionError != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AzureError)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = inspectionError ?: "",
                                    color = AzureError,
                                    fontSize = 13.sp
                                )
                            }
                        } else capabilityResult?.let { cap ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (cap.supportsRange) Icons.Default.Speed else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (cap.supportsRange) AzureSuccess else MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (cap.supportsRange) "Byte-Range Supported (Multi-connection enabled)" else "Single Connection (Ranges unsupported)",
                                    fontWeight = FontWeight.Bold,
                                    color = if (cap.supportsRange) AzureSuccess else MaterialTheme.colorScheme.secondary,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Detected Size: " + (if (cap.contentLength > 0) formatBytes(cap.contentLength) else "Unknown (Dynamic stream)"),
                                fontSize = 12.sp
                            )
                            if (cap.eTag != null) {
                                Text(text = "ETag: ${cap.eTag}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (cap.contentType != null) {
                                Text(text = "MIME: ${cap.contentType}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Filename Input
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("Target File Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Concurrency Slider
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Concurrency Connections", fontWeight = FontWeight.SemiBold)
                        Text("${concurrency.toInt()} Threads", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = concurrency,
                        onValueChange = { concurrency = it },
                        valueRange = 1f..16f,
                        steps = 14
                    )
                    Text(
                        text = "Divides known-size files into non-overlapping byte ranges. Conservative default is 4 threads.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Advanced: Custom Headers
            OutlinedTextField(
                value = customHeadersText,
                onValueChange = { customHeadersText = it },
                label = { Text("Custom Headers (Optional, Key:Value per line)") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Authorization: Bearer xyz\nCookie: session=123") },
                minLines = 2,
                maxLines = 4
            )

            // Advanced: Expected SHA-256
            OutlinedTextField(
                value = expectedSha256,
                onValueChange = { expectedSha256 = it },
                label = { Text("Expected SHA-256 Checksum (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Submit Button
            Button(
                onClick = {
                    if (url.isBlank()) return@Button
                    val finalName = if (fileName.isNotBlank()) {
                        FileNameSanitizer.sanitize(fileName)
                    } else {
                        FileNameSanitizer.sanitize(CapabilityDetector.extractFileName(null, url))
                    }
                    val headers = parseHeaders(customHeadersText)
                    val sha = expectedSha256.trim().ifBlank { null }
                    onSubmitDownload(url, finalName, concurrency.toInt(), headers, sha)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                enabled = url.isNotBlank()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Download", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
