package com.ritam.azureload.feature.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritam.azureload.core.model.ChunkItem
import com.ritam.azureload.core.model.ChunkStatus
import com.ritam.azureload.core.model.DownloadItem
import com.ritam.azureload.core.model.DownloadStatus
import com.ritam.azureload.core.storage.ChecksumResult
import com.ritam.azureload.core.storage.IntegrityVerifier
import com.ritam.azureload.feature.dashboard.formatBytes
import com.ritam.azureload.feature.dashboard.formatSpeed
import com.ritam.azureload.ui.components.BandwidthSparkline
import com.ritam.azureload.ui.components.SegmentedChunkBar
import com.ritam.azureload.ui.components.getFileVisuals
import com.ritam.azureload.ui.theme.AzureCardBorder
import com.ritam.azureload.ui.theme.AzureError
import com.ritam.azureload.ui.theme.AzureSuccess
import com.ritam.azureload.ui.theme.AzureWarning
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    download: DownloadItem?,
    chunks: List<ChunkItem>,
    speed: Long,
    speedHistory: List<Long> = emptyList(),
    onBack: () -> Unit,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onOpenFile: (DownloadItem) -> Unit,
    onShareFile: (DownloadItem) -> Unit = {},
    onUpdateSpeedLimit: (Long, Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var checksumResult by remember { mutableStateOf<ChecksumResult?>(null) }
    var isCalculatingChecksum by remember { mutableStateOf(false) }
    var hashInputToCompare by remember { mutableStateOf("") }

    var currentLimitBytes by remember(download?.speedLimitBytesPerSec) {
        mutableStateOf(download?.speedLimitBytesPerSec ?: 0L)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfer Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (download != null && download.status == DownloadStatus.COMPLETED) {
                        IconButton(onClick = { onShareFile(download) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share File")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (download == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Download not found or was removed.")
            }
            return@Scaffold
        }

        val totalBytes = download.totalBytes
        val downloadedBytes = download.downloadedBytes
        val progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(durationMillis = 300),
            label = "detailProgress"
        )
        val remainingBytes = (totalBytes - downloadedBytes).coerceAtLeast(0)

        val etaText = if (speed > 0 && remainingBytes > 0) {
            val seconds = remainingBytes / speed
            val mins = seconds / 60
            val secs = seconds % 60
            if (mins > 60) "${mins / 60}h ${mins % 60}m" else "${mins}m ${secs}s"
        } else if (download.status == DownloadStatus.COMPLETED) {
            "Finished"
        } else {
            "Calculating..."
        }

        val isParallel = download.isParallelTransfer
        val connectionValue = if (!isParallel) {
            if (download.status == DownloadStatus.DOWNLOADING) "1 Active Stream" else "1 Stream"
        } else if (chunks.isNotEmpty()) {
            if (download.status == DownloadStatus.DOWNLOADING) {
                val activeCount = chunks.count { it.status == ChunkStatus.DOWNLOADING }
                if (activeCount > 0) "$activeCount / ${chunks.size} Active" else "${chunks.size} Connections"
            } else {
                "${chunks.size} Connections"
            }
        } else {
            "${download.effectiveConnectionCount()} Connections"
        }

        val visuals = remember(download.fileName, download.mimeType) {
            getFileVisuals(download.fileName, download.mimeType)
        }

        fun copyToClipboard(label: String, text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Card: File info, progress, actions
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, AzureCardBorder.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = visuals.accentColor.copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = visuals.icon,
                                        contentDescription = null,
                                        tint = visuals.accentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = download.fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = visuals.categoryLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = visuals.accentColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when (download.status) {
                                            DownloadStatus.COMPLETED -> AzureSuccess.copy(alpha = 0.15f)
                                            DownloadStatus.FAILED -> AzureError.copy(alpha = 0.15f)
                                            DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else -> AzureWarning.copy(alpha = 0.15f)
                                        }
                                    ) {
                                        Text(
                                            text = download.status.name,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (download.status) {
                                                DownloadStatus.COMPLETED -> AzureSuccess
                                                DownloadStatus.FAILED -> AzureError
                                                DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                                                else -> AzureWarning
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // URL with Copy action
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { copyToClipboard("URL", download.url) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = download.url,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy URL",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Transfer mode chip
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isParallel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = if (isParallel) "${download.effectiveConnectionCount(chunks)}x Multi-Connection Parallel" else "Single Stream",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isParallel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!download.supportsRanges && download.concurrency > 1 &&
                                download.status != DownloadStatus.QUEUED && download.status != DownloadStatus.DETECTING) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "(Byte ranges unsupported by server)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Animated Progress bar
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = when (download.status) {
                                DownloadStatus.COMPLETED -> AzureSuccess
                                DownloadStatus.FAILED -> AzureError
                                else -> MaterialTheme.colorScheme.primary
                            },
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatBytes(downloadedBytes)} / ${if (totalBytes > 0) formatBytes(totalBytes) else "Unknown"}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (download.status) {
                                DownloadStatus.DOWNLOADING -> {
                                    Button(
                                        onClick = { onPause(download.id) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = AzureWarning)
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pause")
                                    }
                                }
                                DownloadStatus.PAUSED -> {
                                    Button(
                                        onClick = { onResume(download.id) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Resume")
                                    }
                                }
                                DownloadStatus.FAILED -> {
                                    Button(
                                        onClick = { onRetry(download.id) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry")
                                    }
                                }
                                DownloadStatus.COMPLETED -> {
                                    Button(
                                        onClick = { onOpenFile(download) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open File")
                                    }
                                    OutlinedButton(
                                        onClick = { onShareFile(download) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share")
                                    }
                                }
                                else -> {}
                            }

                            OutlinedButton(
                                onClick = { onCancel(download.id); onBack() },
                                modifier = Modifier.weight(if (download.status == DownloadStatus.COMPLETED) 0.8f else 1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AzureError)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (download.status == DownloadStatus.COMPLETED) "Delete" else "Cancel")
                            }
                        }
                    }
                }
            }

            // Stats Metrics & Live Speed Analytics Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Speed",
                        value = if (download.status == DownloadStatus.DOWNLOADING) formatSpeed(speed) else "0 KB/s",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "ETA",
                        value = etaText,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Streams",
                        value = connectionValue,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Real-Time Speed Graph Card (Sparkline / Area Chart)
            if (speedHistory.isNotEmpty() || download.status == DownloadStatus.DOWNLOADING) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, AzureCardBorder.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ShowChart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Speed Analytics",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    text = if (speedHistory.isNotEmpty()) "Avg: ${formatSpeed(speedHistory.average().toLong())}" else "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            BandwidthSparkline(
                                history = speedHistory,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp),
                                lineColor = MaterialTheme.colorScheme.primary,
                                fillColor = MaterialTheme.colorScheme.primary,
                                showGridAndLabels = true,
                                currentSpeed = speed
                            )
                        }
                    }
                }
            }

            // Bandwidth Throttling / Speed Limit Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, AzureCardBorder.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Bandwidth Throttle", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = if (currentLimitBytes > 0) formatSpeed(currentLimitBytes) else "Unlimited",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentLimitBytes > 0) AzureWarning else MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Preset chips
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
                                "5 MB/s" to 5L * 1024L * 1024L,
                                "10 MB/s" to 10L * 1024L * 1024L
                            )
                            presets.forEach { (label, bytes) ->
                                FilterChip(
                                    selected = currentLimitBytes == bytes,
                                    onClick = {
                                        currentLimitBytes = bytes
                                        onUpdateSpeedLimit(download.id, bytes)
                                    },
                                    label = { Text(label, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Segmented Parallel Range Breakdown
            if (isParallel && chunks.isNotEmpty()) {
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Parallel Range Breakdown (${chunks.size} Connections)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${chunks.count { it.status == ChunkStatus.COMPLETED }}/${chunks.size} Done",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SegmentedChunkBar(chunks = chunks)
                    }
                }

                items(chunks, key = { it.id }) { chunk ->
                    ChunkCard(chunk)
                }
            }

            // Checksum Calculation & Integrity Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, AzureCardBorder.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = AzureSuccess, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("File Checksum & Verification", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Target file status on disk
                        val targetFile = if (download.status == DownloadStatus.COMPLETED && download.destinationPath.isNotBlank()) {
                            File(download.destinationPath)
                        } else if (download.stagingPath.isNotBlank()) {
                            File(download.stagingPath)
                        } else null

                        val fileExists = targetFile?.exists() == true

                        if (fileExists && targetFile != null) {
                            Button(
                                onClick = {
                                    isCalculatingChecksum = true
                                    coroutineScope.launch {
                                        try {
                                            checksumResult = IntegrityVerifier.computeChecksums(targetFile)
                                        } finally {
                                            isCalculatingChecksum = false
                                        }
                                    }
                                },
                                enabled = !isCalculatingChecksum,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isCalculatingChecksum) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Computing SHA-256 & MD5...")
                                } else {
                                    Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (checksumResult == null) "Calculate File Checksums" else "Re-calculate Checksums")
                                }
                            }

                            checksumResult?.let { res ->
                                Spacer(modifier = Modifier.height(12.dp))

                                // SHA-256 result row
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("SHA-256 Hash", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        IconButton(
                                            onClick = { copyToClipboard("SHA-256", res.sha256) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy SHA-256", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = res.sha256,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // MD5 result row
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("MD5 Hash", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        IconButton(
                                            onClick = { copyToClipboard("MD5", res.md5) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy MD5", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = res.md5,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                // Expected hash comparison
                                if (!download.expectedSha256.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val isMatch = res.sha256.equals(download.expectedSha256.trim(), ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isMatch) AzureSuccess.copy(alpha = 0.15f) else AzureError.copy(alpha = 0.15f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isMatch) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                                contentDescription = null,
                                                tint = if (isMatch) AzureSuccess else AzureError,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isMatch) "Expected SHA-256 Verified Match" else "SHA-256 Checksum Mismatch!",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isMatch) AzureSuccess else AzureError
                                            )
                                        }
                                    }
                                }

                                // Live comparator text field
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = hashInputToCompare,
                                    onValueChange = { hashInputToCompare = it },
                                    label = { Text("Compare Against Expected Checksum", fontSize = 11.sp) },
                                    placeholder = { Text("Paste MD5 or SHA-256...", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        if (hashInputToCompare.isNotBlank()) {
                                            val cleanInput = hashInputToCompare.trim()
                                            val matchesSha = res.sha256.equals(cleanInput, ignoreCase = true)
                                            val matchesMd5 = res.md5.equals(cleanInput, ignoreCase = true)
                                            val matched = matchesSha || matchesMd5
                                            Icon(
                                                imageVector = if (matched) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                                contentDescription = null,
                                                tint = if (matched) AzureSuccess else AzureError
                                            )
                                        }
                                    }
                                )
                            }
                        } else {
                            Text(
                                text = "File is not yet available on local storage to compute checksums.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Technical Metadata Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, AzureCardBorder.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Technical Metadata", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        MetadataRow("Status", download.status.name)
                        MetadataRow("Transfer Mode", if (isParallel) "Multi-Connection Parallel" else "Single Stream")
                        MetadataRow(
                            "Active Connections",
                            if (isParallel) {
                                val totalConnections = download.effectiveConnectionCount(chunks)
                                if (download.status == DownloadStatus.DOWNLOADING) {
                                    val active = chunks.count { it.status == ChunkStatus.DOWNLOADING }
                                    "$active active / $totalConnections total"
                                } else {
                                    "0 active / $totalConnections total"
                                }
                            } else {
                                if (download.status == DownloadStatus.DOWNLOADING) "1 active stream" else "0 active streams"
                            }
                        )
                        MetadataRow("Configured Concurrency", "${download.concurrency} streams requested")
                        MetadataRow("Byte Ranges Supported", if (download.supportsRanges) "Yes" else "No")
                        if (!download.supportsRanges && download.concurrency > 1 &&
                            download.status != DownloadStatus.QUEUED && download.status != DownloadStatus.DETECTING) {
                            MetadataRow(
                                "Acceleration Fallback",
                                "Server does not support HTTP byte ranges (HTTP 206). Running safely in single-stream mode."
                            )
                        }
                        if (!download.mimeType.isNullOrBlank()) MetadataRow("MIME Type", download.mimeType)
                        if (!download.eTag.isNullOrBlank()) MetadataRow("ETag", download.eTag)
                        if (!download.lastModified.isNullOrBlank()) MetadataRow("Last-Modified", download.lastModified)
                        if (!download.stagingPath.isNullOrBlank()) MetadataRow("Staging Path", download.stagingPath)
                        if (!download.destinationPath.isNullOrBlank()) MetadataRow("Destination", download.destinationPath)
                        if (!download.expectedSha256.isNullOrBlank()) MetadataRow("Expected SHA-256", download.expectedSha256)
                        if (download.speedLimitBytesPerSec > 0) MetadataRow("Speed Throttle", formatSpeed(download.speedLimitBytesPerSec))
                        if (!download.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Error: ${download.errorMessage}", color = AzureError, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, AzureCardBorder.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun ChunkCard(chunk: ChunkItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, AzureCardBorder.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connection #${chunk.chunkIndex + 1}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (chunk.status) {
                        ChunkStatus.COMPLETED -> AzureSuccess.copy(alpha = 0.15f)
                        ChunkStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ChunkStatus.FAILED -> AzureError.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = chunk.status.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (chunk.status) {
                            ChunkStatus.COMPLETED -> AzureSuccess
                            ChunkStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                            ChunkStatus.FAILED -> AzureError
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { chunk.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = when (chunk.status) {
                    ChunkStatus.COMPLETED -> AzureSuccess
                    ChunkStatus.FAILED -> AzureError
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Range: ${chunk.startOffset} .. ${chunk.endOffset}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${formatBytes(chunk.downloadedBytes)} / ${formatBytes(chunk.totalSize)} (${(chunk.progress * 100).toInt()}%)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
