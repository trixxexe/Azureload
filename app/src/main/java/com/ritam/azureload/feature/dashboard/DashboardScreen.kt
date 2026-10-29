package com.ritam.azureload.feature.dashboard

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritam.azureload.core.model.DownloadItem
import com.ritam.azureload.core.model.DownloadStatus
import com.ritam.azureload.ui.theme.AzureError
import com.ritam.azureload.ui.theme.AzureSuccess
import com.ritam.azureload.ui.theme.AzureWarning

enum class FilterTab { ALL, ACTIVE, COMPLETED, FAILED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    downloads: List<DownloadItem>,
    speeds: Map<Long, Long>,
    onNavigateToAdd: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    var selectedTab by remember { mutableStateOf(FilterTab.ALL) }

    val filteredList = remember(downloads, selectedTab) {
        when (selectedTab) {
            FilterTab.ALL -> downloads
            FilterTab.ACTIVE -> downloads.filter {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.DETECTING || it.status == DownloadStatus.QUEUED
            }
            FilterTab.COMPLETED -> downloads.filter { it.status == DownloadStatus.COMPLETED }
            FilterTab.FAILED -> downloads.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.PAUSED }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Azureload",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToBrowser) {
                        Icon(Icons.Default.Language, contentDescription = "Built-in Browser")
                    }
                    IconButton(onClick = onNavigateToDiagnostics) {
                        Icon(Icons.Default.BugReport, contentDescription = "Diagnostics")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Download") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Tabs
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                FilterTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            val count = when (tab) {
                                FilterTab.ALL -> downloads.size
                                FilterTab.ACTIVE -> downloads.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.DETECTING }
                                FilterTab.COMPLETED -> downloads.count { it.status == DownloadStatus.COMPLETED }
                                FilterTab.FAILED -> downloads.count { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.PAUSED }
                            }
                            Text("${tab.name.lowercase().replaceFirstChar { it.uppercase() }} ($count)")
                        }
                    )
                }
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No transfers in this category",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        DownloadCard(
                            item = item,
                            speed = speeds[item.id] ?: 0L,
                            onClick = { onNavigateToDetail(item.id) },
                            onPause = { onPause(item.id) },
                            onResume = { onResume(item.id) },
                            onCancel = { onCancel(item.id) },
                            onDelete = { onDelete(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadCard(
    item: DownloadItem,
    speed: Long,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = if (item.totalBytes > 0) {
        (item.downloadedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (item.status) {
                        DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                        DownloadStatus.FAILED -> Icons.Default.Error
                        DownloadStatus.DOWNLOADING -> Icons.Default.Download
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = when (item.status) {
                        DownloadStatus.COMPLETED -> AzureSuccess
                        DownloadStatus.FAILED -> AzureError
                        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                        else -> AzureWarning
                    },
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatBytes(item.downloadedBytes) + (if (item.totalBytes > 0) " / ${formatBytes(item.totalBytes)}" else ""),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (item.status) {
                        DownloadStatus.COMPLETED -> AzureSuccess.copy(alpha = 0.2f)
                        DownloadStatus.FAILED -> AzureError.copy(alpha = 0.2f)
                        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else -> AzureWarning.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        text = item.status.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (item.status) {
                            DownloadStatus.COMPLETED -> AzureSuccess
                            DownloadStatus.FAILED -> AzureError
                            DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                            else -> AzureWarning
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.supportsRanges && item.concurrency > 1) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${item.concurrency}x Parallel",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Text(
                            text = "Single Stream",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (item.status == DownloadStatus.DOWNLOADING && speed > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${formatSpeed(speed)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // Card Action buttons
                Row {
                    when (item.status) {
                        DownloadStatus.DOWNLOADING -> {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = AzureWarning)
                            }
                        }
                        DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        else -> {}
                    }
                    IconButton(onClick = { if (item.status == DownloadStatus.COMPLETED) onDelete() else onCancel() }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = AzureError)
                    }
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

fun formatSpeed(bytesPerSec: Long): String {
    val mb = bytesPerSec / (1024.0 * 1024.0)
    return if (mb >= 0.1) String.format("%.2f MB/s", mb) else "${bytesPerSec / 1024} KB/s"
}
