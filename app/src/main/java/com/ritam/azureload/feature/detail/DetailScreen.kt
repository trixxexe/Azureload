package com.ritam.azureload.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritam.azureload.core.model.ChunkItem
import com.ritam.azureload.core.model.ChunkStatus
import com.ritam.azureload.core.model.DownloadItem
import com.ritam.azureload.core.model.DownloadStatus
import com.ritam.azureload.feature.dashboard.formatBytes
import com.ritam.azureload.feature.dashboard.formatSpeed
import com.ritam.azureload.ui.theme.AzureError
import com.ritam.azureload.ui.theme.AzureSuccess
import com.ritam.azureload.ui.theme.AzureWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    download: DownloadItem?,
    chunks: List<ChunkItem>,
    speed: Long,
    onBack: () -> Unit,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onOpenFile: (DownloadItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfer Details", fontWeight = FontWeight.Bold) },
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
            "1 Stream"
        } else if (chunks.isNotEmpty()) {
            if (download.status == DownloadStatus.DOWNLOADING) {
                val activeCount = chunks.count { it.status == ChunkStatus.DOWNLOADING }
                if (activeCount > 0) "$activeCount / ${chunks.size} Active" else "${chunks.size} Connections"
            } else {
                "${chunks.size} Chunks"
            }
        } else {
            "${download.concurrency} Chunks"
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = download.fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = download.url,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isParallel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = if (isParallel) "${chunks.size.takeIf { it > 0 } ?: download.concurrency}x Parallel" else "Single Stream",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isParallel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!download.supportsRanges && download.concurrency > 1) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "(Byte ranges unsupported by server)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Progress bar
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
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
                            if (download.status == DownloadStatus.DOWNLOADING) {
                                Button(
                                    onClick = { onPause(download.id) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = AzureWarning)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pause")
                                }
                            } else if (download.status == DownloadStatus.PAUSED) {
                                Button(
                                    onClick = { onResume(download.id) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Resume")
                                }
                            } else if (download.status == DownloadStatus.FAILED) {
                                Button(
                                    onClick = { onRetry(download.id) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry")
                                }
                            } else if (download.status == DownloadStatus.COMPLETED) {
                                Button(
                                    onClick = { onOpenFile(download) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open File")
                                }
                            }

                            OutlinedButton(
                                onClick = { onCancel(download.id); onBack() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AzureError)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (download.status == DownloadStatus.COMPLETED) "Delete" else "Cancel")
                            }
                        }
                    }
                }
            }

            // Stats Metrics Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        title = "Connections",
                        value = connectionValue,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Chunks Section
            if (isParallel && chunks.isNotEmpty()) {
                item {
                    Text(
                        text = "Parallel Range Breakdown (${chunks.size} Connections)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(chunks, key = { it.id }) { chunk ->
                    ChunkCard(chunk)
                }
            }

            // Technical details
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Technical Metadata", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        MetadataRow("Status", download.status.name)
                        MetadataRow("Transfer Mode", if (isParallel) "Multi-Connection Parallel" else "Single Stream")
                        MetadataRow(
                            "Active Connections",
                            if (isParallel) {
                                if (chunks.isNotEmpty()) {
                                    val active = chunks.count { it.status == ChunkStatus.DOWNLOADING }
                                    "$active active / ${chunks.size} total"
                                } else "${download.concurrency} parallel connections"
                            } else {
                                if (download.status == DownloadStatus.DOWNLOADING) "1 active stream" else "1 stream"
                            }
                        )
                        MetadataRow("Configured Concurrency", "${download.concurrency} streams requested")
                        MetadataRow("Byte Ranges Supported", if (download.supportsRanges) "Yes" else "No")
                        if (!download.supportsRanges && download.concurrency > 1) {
                            MetadataRow(
                                "Acceleration Fallback",
                                "Server does not support HTTP byte ranges (HTTP 206). Engine runs safely in single-stream mode."
                            )
                        }
                        if (!download.eTag.isNullOrBlank()) MetadataRow("ETag", download.eTag)
                        if (!download.lastModified.isNullOrBlank()) MetadataRow("Last-Modified", download.lastModified)
                        if (!download.stagingPath.isNullOrBlank()) MetadataRow("Staging Path", download.stagingPath)
                        if (!download.destinationPath.isNullOrBlank()) MetadataRow("Destination", download.destinationPath)
                        if (!download.expectedSha256.isNullOrBlank()) MetadataRow("SHA-256 Checksum", download.expectedSha256)
                        if (!download.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ChunkCard(chunk: ChunkItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        ChunkStatus.COMPLETED -> AzureSuccess.copy(alpha = 0.2f)
                        ChunkStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ChunkStatus.FAILED -> AzureError.copy(alpha = 0.2f)
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
                    text = "Offset: ${chunk.startOffset} .. ${chunk.endOffset}",
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
