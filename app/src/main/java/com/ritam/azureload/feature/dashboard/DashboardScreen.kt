package com.ritam.azureload.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritam.azureload.core.model.DownloadItem
import com.ritam.azureload.core.model.DownloadStatus
import com.ritam.azureload.core.model.FileCategory
import com.ritam.azureload.core.model.getFileCategory
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
    var showActionMenu by remember { mutableStateOf(false) }
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
                val aSize = if (a.totalBytes > 0) a.totalBytes else a.downloadedBytes
                val bSize = if (b.totalBytes > 0) b.totalBytes else b.downloadedBytes
                when (selectedSort) {
                    SortOption.DATE_DESC -> b.createdAt.compareTo(a.createdAt)
                    SortOption.DATE_ASC -> a.createdAt.compareTo(b.createdAt)
                    SortOption.SIZE_DESC -> bSize.compareTo(aSize)
                    SortOption.SIZE_ASC -> aSize.compareTo(bSize)
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
            // Elegant Minimalist iOS Glass Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large iOS Title + Total Count Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Transfers",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        letterSpacing = (-0.6).sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    LiquidGlassPill(
                        cornerRadius = 100.dp,
                        tint = ElectricCyan
                    ) {
                        Text(
                            text = "${downloads.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                    }
                }

                // Header Actions: Live Velocity Chip + Action Menu (•••)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Live Velocity Pill when transfers are actively running
                    if (activeCount > 0 && totalActiveSpeed > 0) {
                        LiquidGlassPill(
                            cornerRadius = 100.dp,
                            tint = ElectricCyan
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatSpeed(totalActiveSpeed),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ElectricCyan
                            )
                            if (totalSpeedHistory.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                BandwidthSparkline(
                                    history = totalSpeedHistory,
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(14.dp),
                                    lineColor = ElectricCyan,
                                    fillColor = VibrantAzure
                                )
                            }
                        }
                    }

                    // Minimalist Overflow Action Menu (•••)
                    Box {
                        LiquidGlassIconButton(
                            onClick = { showActionMenu = true },
                            icon = Icons.Default.MoreHoriz,
                            contentDescription = "Actions",
                            modifier = Modifier.size(36.dp),
                            cornerRadius = 12.dp
                        )

                        DropdownMenu(
                            expanded = showActionMenu,
                            onDismissRequest = { showActionMenu = false }
                        ) {
                            if (activeCount > 0) {
                                DropdownMenuItem(
                                    text = { Text("Pause All Transfers") },
                                    leadingIcon = { Icon(Icons.Default.Pause, null, tint = GoldenAmber) },
                                    onClick = {
                                        showActionMenu = false
                                        onPauseAll()
                                    }
                                )
                            }
                            if (failedOrPausedCount > 0) {
                                DropdownMenuItem(
                                    text = { Text("Resume All Transfers") },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = ElectricCyan) },
                                    onClick = {
                                        showActionMenu = false
                                        onResumeAll()
                                    }
                                )
                            }
                            if (completedCount > 0) {
                                DropdownMenuItem(
                                    text = { Text("Clear Completed Transfers") },
                                    leadingIcon = { Icon(Icons.Default.CleaningServices, null, tint = AuroraEmerald) },
                                    onClick = {
                                        showActionMenu = false
                                        showClearCompletedDialog = true
                                    }
                                )
                            }
                            HorizontalDivider()
                            SortOption.values().forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            opt.title,
                                            fontWeight = if (selectedSort == opt) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        if (selectedSort == opt) {
                                            Icon(Icons.Default.Check, null, tint = ElectricCyan)
                                        }
                                    },
                                    onClick = {
                                        selectedSort = opt
                                        showActionMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Minimalist iOS Liquid Glass Search Capsule
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                placeholder = { Text("Search transfers, URLs, types...", fontSize = 13.sp, color = TextMuted) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = SquircleShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0x12FFFFFF),
                    focusedContainerColor = Color(0x1EFFFFFF),
                    unfocusedBorderColor = Color(0x20FFFFFF),
                    focusedBorderColor = ElectricCyan,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            // Segmented iOS Liquid Glass Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(SquircleShape(14.dp))
                    .background(Color(0x12FFFFFF))
                    .border(1.dp, Color(0x1EFFFFFF), SquircleShape(14.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val tabTitle = when (tab) {
                        FilterTab.ALL -> "All"
                        FilterTab.ACTIVE -> "Active"
                        FilterTab.COMPLETED -> "Done"
                        FilterTab.FAILED -> "Failed"
                    }
                    val tabCount = when (tab) {
                        FilterTab.ALL -> downloads.size
                        FilterTab.ACTIVE -> activeCount
                        FilterTab.COMPLETED -> completedCount
                        FilterTab.FAILED -> failedOrPausedCount
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(SquircleShape(11.dp))
                            .background(
                                if (isSelected) Brush.horizontalGradient(listOf(ElectricCyan, VibrantAzure))
                                else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                            .clickable { selectedTab = tab }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$tabTitle ($tabCount)",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) PureBlack else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Category Filter Pills Rail
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FileCategory.values().forEach { cat ->
                    val isSelected = selectedCategory == cat
                    val catColor = when (cat) {
                        FileCategory.ALL -> ElectricCyan
                        FileCategory.MEDIA -> NeonMagenta
                        FileCategory.DOCUMENTS -> VibrantAzure
                        FileCategory.ARCHIVES -> TangerineGlow
                        FileCategory.OTHER -> CyberViolet
                    }

                    LiquidGlassPill(
                        tint = if (isSelected) catColor else null,
                        isSolid = isSelected,
                        cornerRadius = 100.dp,
                        onClick = { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) PureBlack else TextPrimary
                        )
                    }
                }
            }

            // Main Download Transfer List
            if (filteredList.isEmpty()) {
                val isFiltered = searchQuery.isNotBlank() || selectedCategory != FileCategory.ALL || selectedTab != FilterTab.ALL
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 108.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LiquidGlassCard(
                        modifier = Modifier.padding(28.dp),
                        cornerRadius = 24.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(SquircleShape(18.dp))
                                    .background(ElectricCyan.copy(alpha = 0.16f))
                                    .border(1.dp, ElectricCyan.copy(alpha = 0.40f), SquircleShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFiltered) Icons.Default.SearchOff else Icons.Default.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = ElectricCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (isFiltered) "No matching transfers" else "No transfers yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isFiltered) "No transfers match your current filter or search criteria." else "Tap + in the dock below to accelerate a file",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            if (isFiltered) {
                                Spacer(modifier = Modifier.height(14.dp))
                                LiquidGlassButton(
                                    onClick = {
                                        searchQuery = ""
                                        selectedCategory = FileCategory.ALL
                                        selectedTab = FilterTab.ALL
                                    },
                                    isSolid = true,
                                    accentColor = ElectricCyan,
                                    cornerRadius = 10.dp
                                ) {
                                    Text("Reset Filters", color = PureBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else if (downloads.isEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                LiquidGlassButton(
                                    onClick = onNavigateToAdd,
                                    isSolid = true,
                                    accentColor = ElectricCyan,
                                    cornerRadius = 12.dp
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = PureBlack, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Transfer", color = PureBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 108.dp),
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
                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = ElectricCyan)
            },
            title = { Text("Clear Completed Transfers?") },
            text = {
                Column {
                    Text("This will remove $completedCount completed transfer${if (completedCount > 1) "s" else ""} from your list.")
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
                        Text("Also delete downloaded files from storage", fontSize = 13.sp)
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
                        containerColor = if (deleteFilesOnDisk) CoralDanger else ElectricCyan
                    )
                ) {
                    Text(if (deleteFilesOnDisk) "Delete & Clear" else "Clear List", color = PureBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCompletedDialog = false }) {
                    Text("Cancel", color = TextSecondary)
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

    val isDownloading = item.status == DownloadStatus.DOWNLOADING

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        isHighlighted = isDownloading,
        highlightColor = visuals.accentColor,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Primary Line: File Icon + Title & Stats + Quick Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Squircle Icon Plate
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(SquircleShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    visuals.accentColor.copy(alpha = 0.28f),
                                    visuals.accentColor.copy(alpha = 0.10f)
                                )
                            )
                        )
                        .border(1.dp, visuals.accentColor.copy(alpha = 0.45f), SquircleShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = visuals.icon,
                        contentDescription = null,
                        tint = visuals.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Sleek Inline Status
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (item.status) {
                            DownloadStatus.DOWNLOADING -> {
                                val speedText = if (speed > 0) " • ${formatSpeed(speed)}" else ""
                                val totalStr = if (item.totalBytes > 0) formatBytes(item.totalBytes) else "Unknown"
                                "${visuals.categoryLabel} • ${formatBytes(item.downloadedBytes)} / $totalStr$speedText"
                            }
                            DownloadStatus.COMPLETED -> {
                                val totalStr = if (item.totalBytes > 0) formatBytes(item.totalBytes) else formatBytes(item.downloadedBytes)
                                "${visuals.categoryLabel} • $totalStr • Completed"
                            }
                            DownloadStatus.PAUSED -> {
                                val totalStr = if (item.totalBytes > 0) formatBytes(item.totalBytes) else "Unknown"
                                "${visuals.categoryLabel} • ${formatBytes(item.downloadedBytes)} / $totalStr • Paused"
                            }
                            DownloadStatus.FAILED -> "${visuals.categoryLabel} • Interrupted • Tap for diagnosis"
                            else -> "${visuals.categoryLabel} • ${item.status.name.lowercase().replaceFirstChar { it.uppercase() }}"
                        },
                        fontSize = 11.sp,
                        color = if (item.status == DownloadStatus.FAILED) CoralDanger else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Fluid Quick Action Squircle Button
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        LiquidGlassIconButton(
                            onClick = onPause,
                            icon = Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = GoldenAmber,
                            modifier = Modifier.size(34.dp),
                            cornerRadius = 10.dp
                        )
                    }
                    DownloadStatus.PAUSED -> {
                        LiquidGlassIconButton(
                            onClick = onResume,
                            icon = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint = ElectricCyan,
                            modifier = Modifier.size(34.dp),
                            cornerRadius = 10.dp
                        )
                    }
                    DownloadStatus.FAILED -> {
                        LiquidGlassIconButton(
                            onClick = onRetry,
                            icon = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = ElectricCyan,
                            modifier = Modifier.size(34.dp),
                            cornerRadius = 10.dp
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        LiquidGlassIconButton(
                            onClick = onOpenFile,
                            icon = Icons.Default.OpenInNew,
                            contentDescription = "Open",
                            tint = AuroraEmerald,
                            modifier = Modifier.size(34.dp),
                            cornerRadius = 10.dp
                        )
                    }
                    else -> {}
                }

                // Delete / Cancel Button
                Spacer(modifier = Modifier.width(4.dp))
                LiquidGlassIconButton(
                    onClick = { if (item.status == DownloadStatus.COMPLETED) onDelete() else onCancel() },
                    icon = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = CoralDanger.copy(alpha = 0.8f),
                    modifier = Modifier.size(34.dp),
                    cornerRadius = 10.dp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Slender 4dp Glowing Squircle Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(SquircleShape(2.dp))
                    .background(Color(0x16FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(SquircleShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                when (item.status) {
                                    DownloadStatus.COMPLETED -> listOf(AuroraEmerald, MintTeal)
                                    DownloadStatus.FAILED -> listOf(CoralDanger, SunsetPink)
                                    else -> listOf(ElectricCyan, VibrantAzure)
                                }
                            )
                        )
                )
            }

            // Embedded live sparkline for downloading files
            if (isDownloading && speedHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                BandwidthSparkline(
                    history = speedHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp),
                    lineColor = ElectricCyan,
                    fillColor = VibrantAzure
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Footer Sub-row: Stream Concurrency & Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val streamLabel = if (item.isParallelTransfer) {
                    "⚡ ${item.effectiveConnectionCount()}x Streams"
                } else if (!item.supportsRanges && item.concurrency > 1 &&
                    item.status != DownloadStatus.QUEUED && item.status != DownloadStatus.DETECTING) {
                    "Single Stream"
                } else {
                    "1 Stream"
                }

                Text(
                    text = streamLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isParallelTransfer) ElectricCyan else TextMuted
                )

                if (item.totalBytes > 0) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
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
