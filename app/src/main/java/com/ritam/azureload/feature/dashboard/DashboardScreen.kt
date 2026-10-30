package com.ritam.azureload.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.ritam.azureload.core.model.FileCategory
import com.ritam.azureload.core.model.getFileCategory
import com.ritam.azureload.ui.components.BandwidthSparkline
import com.ritam.azureload.ui.components.getFileVisuals
import com.ritam.azureload.ui.theme.AzureCardBorder
import com.ritam.azureload.ui.theme.AzureError
import com.ritam.azureload.ui.theme.AzureSuccess
import com.ritam.azureload.ui.theme.AzureWarning

enum class FilterTab { ALL, ACTIVE, COMPLETED, FAILED }

enum class SortOption(val title: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    SIZE_DESC("Largest Size"),
    SIZE_ASC("Smallest Size"),
    NAME_ASC("Name (A-Z)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    downloads: List<DownloadItem>,
    speeds: Map<Long, Long>,
    speedHistories: Map<Long, List<Long>> = emptyMap(),
    totalSpeedHistory: List<Long> = emptyList(),
    onNavigateToAdd: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onPauseAll: () -> Unit = {},
    onResumeAll: () -> Unit = {},
    onClearCompleted: (Boolean) -> Unit = {},
    onOpenFile: (DownloadItem) -> Unit,
    onShareFile: (DownloadItem) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(FilterTab.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(FileCategory.ALL) }
    var selectedSort by remember { mutableStateOf(SortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showClearCompletedDialog by remember { mutableStateOf(false) }

    // Counts for tab badges
    val activeCount = remember(downloads) {
        downloads.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.DETECTING || it.status == DownloadStatus.QUEUED }
    }
    val completedCount = remember(downloads) {
        downloads.count { it.status == DownloadStatus.COMPLETED }
    }
    val failedOrPausedCount = remember(downloads) {
        downloads.count { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.PAUSED }
    }

    // Filtered and sorted downloads
    val filteredList = remember(downloads, selectedTab, searchQuery, selectedCategory, selectedSort) {
        downloads
            .asSequence()
            .filter { item ->
                when (selectedTab) {
                    FilterTab.ALL -> true
                    FilterTab.ACTIVE -> item.status == DownloadStatus.DOWNLOADING ||
                            item.status == DownloadStatus.DETECTING ||
                            item.status == DownloadStatus.QUEUED
                    FilterTab.COMPLETED -> item.status == DownloadStatus.COMPLETED
                    FilterTab.FAILED -> item.status == DownloadStatus.FAILED ||
                            item.status == DownloadStatus.PAUSED
                }
            }
            .filter { item ->
                if (selectedCategory == FileCategory.ALL) true
                else item.getFileCategory() == selectedCategory
            }
            .filter { item ->
                if (searchQuery.isBlank()) true
                else {
                    val q = searchQuery.trim()
                    item.fileName.contains(q, ignoreCase = true) ||
                        item.url.contains(q, ignoreCase = true) ||
                        (item.mimeType?.contains(q, ignoreCase = true) == true) ||
                        item.status.name.contains(q, ignoreCase = true)
                }
            }
            .toList()
            .let { list ->
                when (selectedSort) {
                    SortOption.DATE_DESC -> list.sortedByDescending { it.createdAt }
                    SortOption.DATE_ASC -> list.sortedBy { it.createdAt }
                    SortOption.SIZE_DESC -> list.sortedByDescending { it.totalBytes }
                    SortOption.SIZE_ASC -> list.sortedBy { it.totalBytes }
                    SortOption.NAME_ASC -> list.sortedBy { it.fileName.lowercase() }
                }
            }
    }

    // Total active speed
    val totalActiveSpeed = remember(speeds) {
        speeds.values.sum()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Azureload",
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Multi-Connection Accelerator",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
                text = { Text("New Download", fontWeight = FontWeight.SemiBold) },
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
            // Live Bandwidth Banner (when downloads are active)
            AnimatedVisibility(
                visible = activeCount > 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Total Speed: ${formatSpeed(totalActiveSpeed)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$activeCount active transfers running",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Sparkline in banner
                        if (totalSpeedHistory.isNotEmpty()) {
                            BandwidthSparkline(
                                history = totalSpeedHistory,
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(30.dp),
                                lineColor = MaterialTheme.colorScheme.primary,
                                fillColor = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        FilledTonalButton(
                            onClick = onPauseAll,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = AzureWarning.copy(alpha = 0.2f),
                                contentColor = AzureWarning
                            )
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search by name, status, MIME, or URL...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                )
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FileCategory.values().forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.displayName, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Sort Chip / Dropdown
                Box {
                    AssistChip(
                        onClick = { showSortMenu = true },
                        label = { Text(selectedSort.title, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOption.values().forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.title) },
                                onClick = {
                                    selectedSort = opt
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Filter Tabs
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                FilterTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            val count = when (tab) {
                                FilterTab.ALL -> downloads.size
                                FilterTab.ACTIVE -> activeCount
                                FilterTab.COMPLETED -> completedCount
                                FilterTab.FAILED -> failedOrPausedCount
                            }
                            Text(
                                text = "${tab.name.lowercase().replaceFirstChar { it.uppercase() }} ($count)",
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Batch Actions Bar (Pause All, Resume All, Clear Finished)
            if (downloads.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredList.size} of ${downloads.size} items",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (failedOrPausedCount > 0) {
                            TextButton(
                                onClick = onResumeAll,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume All", fontSize = 12.sp)
                            }
                        }

                        if (completedCount > 0) {
                            TextButton(
                                onClick = { showClearCompletedDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Finished", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // List Content
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (searchQuery.isNotBlank() || selectedCategory != FileCategory.ALL) Icons.Default.SearchOff else Icons.Default.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedCategory != FileCategory.ALL) "No downloads match your filters" else "No transfers in this category",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedCategory != FileCategory.ALL) "Try adjusting your query or category selection" else "Tap + to start a multi-connection download",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchQuery.isNotBlank() || selectedCategory != FileCategory.ALL) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedCategory = FileCategory.ALL
                                    selectedTab = FilterTab.ALL
                                }
                            ) {
                                Text("Reset Filters")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        DownloadCard(
                            item = item,
                            speed = speeds[item.id] ?: 0L,
                            speedHistory = speedHistories[item.id] ?: emptyList(),
                            onClick = { onNavigateToDetail(item.id) },
                            onPause = { onPause(item.id) },
                            onResume = { onResume(item.id) },
                            onRetry = { onRetry(item.id) },
                            onCancel = { onCancel(item.id) },
                            onDelete = { onDelete(item.id) },
                            onOpenFile = { onOpenFile(item) },
                            onShareFile = { onShareFile(item) }
                        )
                    }
                }
            }
        }
    }

    // Confirmation dialog for clearing completed downloads
    if (showClearCompletedDialog) {
        var deleteFilesOnDisk by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showClearCompletedDialog = false },
            icon = {
                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = { Text("Clear Completed Downloads?") },
            text = {
                Column {
                    Text("This will remove $completedCount completed downloads from your history list.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteFilesOnDisk = !deleteFilesOnDisk }
                    ) {
                        Checkbox(
                            checked = deleteFilesOnDisk,
                            onCheckedChange = { deleteFilesOnDisk = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Also delete files from device storage", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCompletedDialog = false
                        onClearCompleted(deleteFilesOnDisk)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (deleteFilesOnDisk) AzureError else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCompletedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DownloadCard(
    item: DownloadItem,
    speed: Long,
    speedHistory: List<Long> = emptyList(),
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onOpenFile: () -> Unit,
    onShareFile: () -> Unit
) {
    val progress = if (item.totalBytes > 0) {
        (item.downloadedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "progressAnimation"
    )

    val visuals = remember(item.fileName, item.mimeType) {
        getFileVisuals(item.fileName, item.mimeType)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            1.dp,
            if (item.status == DownloadStatus.DOWNLOADING) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else AzureCardBorder.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: File icon, name, status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = visuals.accentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = visuals.icon,
                            contentDescription = null,
                            tint = visuals.accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = visuals.categoryLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = visuals.accentColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${formatBytes(item.downloadedBytes)}" + (if (item.totalBytes > 0) " / ${formatBytes(item.totalBytes)}" else ""),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (item.status) {
                        DownloadStatus.COMPLETED -> AzureSuccess.copy(alpha = 0.15f)
                        DownloadStatus.FAILED -> AzureError.copy(alpha = 0.15f)
                        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else -> AzureWarning.copy(alpha = 0.15f)
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

            Spacer(modifier = Modifier.height(10.dp))

            // Animated Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when (item.status) {
                    DownloadStatus.COMPLETED -> AzureSuccess
                    DownloadStatus.FAILED -> AzureError
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surface
            )

            // Sparkline for actively downloading files
            if (item.status == DownloadStatus.DOWNLOADING && speedHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                BandwidthSparkline(
                    history = speedHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp),
                    lineColor = MaterialTheme.colorScheme.primary,
                    fillColor = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Row: Metadata badges & actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isParallelTransfer) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${item.effectiveConnectionCount()}x Parallel",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            val singleStreamLabel = if (!item.supportsRanges && item.concurrency > 1 &&
                                item.status != DownloadStatus.QUEUED && item.status != DownloadStatus.DETECTING) {
                                "Single Stream (No Ranges)"
                            } else {
                                "Single Stream"
                            }
                            Text(
                                text = singleStreamLabel,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (item.status == DownloadStatus.DOWNLOADING && speed > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatSpeed(speed),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    if (item.totalBytes > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Card Action buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (item.status) {
                        DownloadStatus.DOWNLOADING -> {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = AzureWarning)
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DownloadStatus.FAILED -> {
                            IconButton(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            IconButton(onClick = onShareFile) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = onOpenFile) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open file", tint = MaterialTheme.colorScheme.primary)
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
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(java.util.Locale.US, "%.2f GB", gb)
}

fun formatSpeed(bytesPerSec: Long): String {
    val mb = bytesPerSec / (1024.0 * 1024.0)
    return if (mb >= 0.1) String.format(java.util.Locale.US, "%.2f MB/s", mb) else "${bytesPerSec / 1024} KB/s"
}
