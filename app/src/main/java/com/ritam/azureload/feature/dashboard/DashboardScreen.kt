package com.ritam.azureload.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.ritam.azureload.core.network.ErrorClassifier
import com.ritam.azureload.ui.components.*
import com.ritam.azureload.ui.theme.*

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
                val q = searchQuery.trim()
                if (q.isBlank()) true
                else {
                    item.fileName.contains(q, ignoreCase = true) ||
                            item.url.contains(q, ignoreCase = true) ||
                            (item.mimeType?.contains(q, ignoreCase = true) == true) ||
                            item.status.name.contains(q, ignoreCase = true)
                }
            }
            .sortedWith { a, b ->
                when (selectedSort) {
                    SortOption.DATE_DESC -> b.createdAt.compareTo(a.createdAt)
                    SortOption.DATE_ASC -> a.createdAt.compareTo(b.createdAt)
                    SortOption.SIZE_DESC -> b.totalBytes.compareTo(a.totalBytes)
                    SortOption.SIZE_ASC -> a.totalBytes.compareTo(b.totalBytes)
                    SortOption.NAME_ASC -> a.fileName.compareTo(b.fileName, ignoreCase = true)
                }
            }
            .toList()
    }

    // Total active speed
    val totalActiveSpeed = remember(speeds) {
        speeds.values.sum()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(SquircleShape(10.dp))
                                .background(AzurePrimary.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Azureload",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = AzureOnSurface
                            )
                            Text(
                                text = "Liquid Multi-Stream Accelerator",
                                fontSize = 11.sp,
                                color = NeonCyan
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToBrowser) {
                        Icon(Icons.Default.Language, contentDescription = "Built-in Browser", tint = AzureOnSurface)
                    }
                    IconButton(onClick = onNavigateToDiagnostics) {
                        Icon(Icons.Default.BugReport, contentDescription = "Diagnostics", tint = AzureOnSurface)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = AzureOnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            LiquidGlassButton(
                onClick = onNavigateToAdd,
                isSolid = true,
                accentColor = AzurePrimary,
                cornerRadius = 20.dp,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = PureBlack, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Download", fontWeight = FontWeight.ExtraBold, color = PureBlack, fontSize = 14.sp)
            }
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
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    cornerRadius = 18.dp,
                    isHighlighted = true,
                    highlightColor = NeonCyan
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(SquircleShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Total Speed: ${formatSpeed(totalActiveSpeed)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AzureOnSurface
                            )
                            Text(
                                text = "$activeCount active transfers running",
                                fontSize = 11.sp,
                                color = AzureOnSurfaceVariant
                            )
                        }

                        // Sparkline in banner
                        if (totalSpeedHistory.isNotEmpty()) {
                            BandwidthSparkline(
                                history = totalSpeedHistory,
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(30.dp),
                                lineColor = NeonCyan,
                                fillColor = AzurePrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        LiquidGlassButton(
                            onClick = onPauseAll,
                            accentColor = AzureWarning,
                            cornerRadius = 10.dp,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp), tint = AzureWarning)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AzureWarning)
                        }
                    }
                }
            }

            // Search Bar in Squircle shape
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search by name, status, MIME, or URL...", fontSize = 13.sp, color = AzureOnSurfaceVariant) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = AzureOnSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = AzureOnSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape = SquircleShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0x10FFFFFF),
                    focusedContainerColor = Color(0x1EFFFFFF),
                    unfocusedBorderColor = Color(0x22FFFFFF),
                    focusedBorderColor = AzurePrimary
                )
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FileCategory.values().forEach { cat ->
                    val isSelected = selectedCategory == cat
                    LiquidGlassPill(
                        tint = if (isSelected) AzurePrimary else null,
                        isSolid = isSelected,
                        cornerRadius = 10.dp,
                        onClick = { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) PureBlack else AzureOnSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Sort Dropdown
                Box {
                    LiquidGlassPill(
                        cornerRadius = 10.dp,
                        onClick = { showSortMenu = true }
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(14.dp), tint = AzureOnSurface)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(selectedSort.title, fontSize = 11.sp, color = AzureOnSurface)
                    }
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

            // Filter Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val count = when (tab) {
                        FilterTab.ALL -> downloads.size
                        FilterTab.ACTIVE -> activeCount
                        FilterTab.COMPLETED -> completedCount
                        FilterTab.FAILED -> failedOrPausedCount
                    }
                    LiquidGlassPill(
                        modifier = Modifier.weight(1f),
                        tint = if (isSelected) AzurePrimary else null,
                        isSolid = isSelected,
                        cornerRadius = 12.dp,
                        onClick = { selectedTab = tab }
                    ) {
                        Text(
                            text = "${tab.name.lowercase().replaceFirstChar { it.uppercase() }} ($count)",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) PureBlack else AzureOnSurface,
                            maxLines = 1
                        )
                    }
                }
            }

            // Batch Actions Bar (Pause All, Resume All, Clear Finished)
            if (downloads.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredList.size} of ${downloads.size} items",
                        fontSize = 11.sp,
                        color = AzureOnSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (failedOrPausedCount > 0) {
                            LiquidGlassButton(
                                onClick = onResumeAll,
                                isSolid = true,
                                accentColor = NeonCyan,
                                cornerRadius = 8.dp,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureBlack)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureBlack)
                            }
                        }

                        if (completedCount > 0) {
                            LiquidGlassButton(
                                onClick = { showClearCompletedDialog = true },
                                accentColor = AzureOnSurfaceVariant,
                                cornerRadius = 8.dp,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(14.dp), tint = AzureOnSurface)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Finished", fontSize = 11.sp, color = AzureOnSurface)
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
                    LiquidGlassCard(
                        modifier = Modifier.padding(24.dp),
                        cornerRadius = 24.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(SquircleShape(16.dp))
                                    .background(AzurePrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (searchQuery.isNotBlank() || selectedCategory != FileCategory.ALL) Icons.Default.SearchOff else Icons.Default.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = AzurePrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (searchQuery.isNotBlank() || selectedCategory != FileCategory.ALL) "No downloads match your filters" else "No transfers in this category",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = AzureOnSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (searchQuery.isNotBlank() || selectedCategory != FileCategory.ALL) "Try adjusting your query or category selection" else "Tap + to start a multi-connection download",
                                fontSize = 12.sp,
                                color = AzureOnSurfaceVariant
                            )
                            if (searchQuery.isNotBlank() || selectedCategory != FileCategory.ALL) {
                                Spacer(modifier = Modifier.height(12.dp))
                                LiquidGlassButton(
                                    onClick = {
                                        searchQuery = ""
                                        selectedCategory = FileCategory.ALL
                                        selectedTab = FilterTab.ALL
                                    },
                                    isSolid = true,
                                    accentColor = AzurePrimary,
                                    cornerRadius = 10.dp
                                ) {
                                    Text("Reset Filters", color = PureBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
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
                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = AzurePrimary)
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
                        containerColor = if (deleteFilesOnDisk) AzureError else AzurePrimary
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

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        isHighlighted = item.status == DownloadStatus.DOWNLOADING,
        highlightColor = NeonCyan,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: File icon, name, status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(SquircleShape(12.dp))
                        .background(visuals.accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = visuals.icon,
                        contentDescription = null,
                        tint = visuals.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = AzureOnSurface
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
                            color = AzureOnSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Status Badge in Liquid Glass Pill
                LiquidGlassPill(
                    tint = when (item.status) {
                        DownloadStatus.COMPLETED -> AzureSuccess
                        DownloadStatus.FAILED -> AzureError
                        DownloadStatus.DOWNLOADING -> NeonCyan
                        else -> AzureWarning
                    },
                    cornerRadius = 6.dp
                ) {
                    Text(
                        text = item.status.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (item.status) {
                            DownloadStatus.COMPLETED -> AzureSuccess
                            DownloadStatus.FAILED -> AzureError
                            DownloadStatus.DOWNLOADING -> NeonCyan
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
                    .clip(SquircleShape(3.dp)),
                color = when (item.status) {
                    DownloadStatus.COMPLETED -> AzureSuccess
                    DownloadStatus.FAILED -> AzureError
                    else -> NeonCyan
                },
                trackColor = Color(0x20FFFFFF)
            )

            // Sparkline for actively downloading files
            if (item.status == DownloadStatus.DOWNLOADING && speedHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                BandwidthSparkline(
                    history = speedHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp),
                    lineColor = NeonCyan,
                    fillColor = AzurePrimary
                )
            }

            // Inline Error Diagnostics for Failed Downloads
            if (item.status == DownloadStatus.FAILED && !item.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                val classified = remember(item.errorMessage) {
                    ErrorClassifier.parseOrClassify(item.errorMessage)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SquircleShape(10.dp))
                        .background(AzureError.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AzureError,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${classified.title}: ${classified.userMessage}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AzureError,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = classified.recoveryGuidance,
                                fontSize = 10.sp,
                                color = AzureOnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
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
                        LiquidGlassPill(
                            tint = NeonCyan,
                            cornerRadius = 6.dp
                        ) {
                            Text(
                                text = "${item.effectiveConnectionCount()}x Parallel",
                                fontSize = 10.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        val singleStreamLabel = if (!item.supportsRanges && item.concurrency > 1 &&
                            item.status != DownloadStatus.QUEUED && item.status != DownloadStatus.DETECTING) {
                            "Single Stream (No Ranges)"
                        } else {
                            "Single Stream"
                        }
                        LiquidGlassPill(cornerRadius = 6.dp) {
                            Text(
                                text = singleStreamLabel,
                                fontSize = 10.sp,
                                color = AzureOnSurfaceVariant
                            )
                        }
                    }

                    if (item.status == DownloadStatus.DOWNLOADING && speed > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatSpeed(speed),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }

                    if (item.totalBytes > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AzureOnSurfaceVariant
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
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = AzurePrimary)
                            }
                        }
                        DownloadStatus.FAILED -> {
                            IconButton(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = AzurePrimary)
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            IconButton(onClick = onShareFile) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = AzurePrimary)
                            }
                            IconButton(onClick = onOpenFile) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open file", tint = AzurePrimary)
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
