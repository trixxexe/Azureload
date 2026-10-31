package com.ritam.azureload.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
            // Elegant Minimalist iOS Glass Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Logo & Brand Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(SquircleShape(11.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(ElectricCyan.copy(alpha = 0.28f), VibrantAzure.copy(alpha = 0.12f))
                                )
                            )
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), SquircleShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(11.dp))
                    Column {
                        Text(
                            text = "Azureload",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.3).sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Liquid Multi-Stream",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = ElectricCyan
                        )
                    }
                }

                // Floating Action Capsule
                LiquidGlassPill(
                    cornerRadius = 100.dp
                ) {
                    IconButton(
                        onClick = onNavigateToBrowser,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = "Browser", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onNavigateToDiagnostics,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = "Diagnostics", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        floatingActionButton = {
            LiquidGlassButton(
                onClick = onNavigateToAdd,
                isSolid = true,
                accentColor = ElectricCyan,
                cornerRadius = 100.dp,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)
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
            // Live Velocity Dock (when downloads are active)
            AnimatedVisibility(
                visible = activeCount > 0,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(250))
            ) {
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    cornerRadius = 20.dp,
                    isHighlighted = true,
                    highlightColor = ElectricCyan
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(SquircleShape(10.dp))
                                .background(ElectricCyan.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatSpeed(totalActiveSpeed),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "$activeCount active stream${if (activeCount > 1) "s" else ""}",
                                fontSize = 11.sp,
                                color = ElectricCyan
                            )
                        }

                        // Sparkline in velocity dock
                        if (totalSpeedHistory.isNotEmpty()) {
                            BandwidthSparkline(
                                history = totalSpeedHistory,
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(30.dp),
                                lineColor = ElectricCyan,
                                fillColor = VibrantAzure
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        LiquidGlassButton(
                            onClick = onPauseAll,
                            accentColor = GoldenAmber,
                            cornerRadius = 10.dp,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp), tint = GoldenAmber)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldenAmber)
                        }
                    }
                }
            }

            // Minimalist Search & Sort Capsule
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search downloads, URLs, types...", fontSize = 13.sp, color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = SquircleShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0x14FFFFFF),
                        focusedContainerColor = Color(0x22FFFFFF),
                        unfocusedBorderColor = Color(0x20FFFFFF),
                        focusedBorderColor = ElectricCyan,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Sort Button Capsule
                Box {
                    LiquidGlassIconButton(
                        onClick = { showSortMenu = true },
                        icon = Icons.Default.Sort,
                        contentDescription = "Sort",
                        cornerRadius = 14.dp,
                        modifier = Modifier.size(46.dp)
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

            // Liquid Glass Segmented Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
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
                        tint = if (isSelected) ElectricCyan else null,
                        isSolid = isSelected,
                        cornerRadius = 12.dp,
                        onClick = { selectedTab = tab }
                    ) {
                        Text(
                            text = "${tab.name.lowercase().replaceFirstChar { it.uppercase() }} $count",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) PureBlack else TextPrimary,
                            maxLines = 1
                        )
                    }
                }
            }

            // Category Filter Pills Rail
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 3.dp),
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
                        cornerRadius = 10.dp,
                        onClick = { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) PureBlack else TextPrimary
                        )
                    }
                }
            }

            // Batch Actions Subtle Bar
            if (downloads.isNotEmpty() && (failedOrPausedCount > 0 || completedCount > 0)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredList.size} item${if (filteredList.size != 1) "s" else ""}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (failedOrPausedCount > 0) {
                            LiquidGlassButton(
                                onClick = onResumeAll,
                                isSolid = true,
                                accentColor = ElectricCyan,
                                cornerRadius = 8.dp,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(13.dp), tint = PureBlack)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureBlack)
                            }
                        }

                        if (completedCount > 0) {
                            LiquidGlassButton(
                                onClick = { showClearCompletedDialog = true },
                                accentColor = TextSecondary,
                                cornerRadius = 8.dp,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(13.dp), tint = TextPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Finished", fontSize = 11.sp, color = TextPrimary)
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
                                    .clip(SquircleShape(16.dp))
                                    .background(ElectricCyan.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (searchQuery.isNotBlank() || selectedCategory != FileCategory.ALL) Icons.Default.SearchOff else Icons.Default.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = ElectricCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (searchQuery.isNotBlank() || selectedCategory != FileCategory.ALL) "No matching downloads" else "No transfers here yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (searchQuery.isNotBlank() || selectedCategory != FileCategory.ALL) "Try adjusting your search terms or filters" else "Tap + New Download to accelerate a file",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            if (searchQuery.isNotBlank() || selectedCategory != FileCategory.ALL) {
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
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 96.dp),
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
            title = { Text("Clear Completed Downloads?") },
            text = {
                Column {
                    Text("This will remove $completedCount completed download${if (completedCount > 1) "s" else ""} from your list.")
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
        cornerRadius = 22.dp,
        isHighlighted = isDownloading,
        highlightColor = visuals.accentColor,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: File icon, name, status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = visuals.categoryLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = visuals.accentColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${formatBytes(item.downloadedBytes)}" + (if (item.totalBytes > 0) " / ${formatBytes(item.totalBytes)}" else ""),
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Pill
                val statusColor = when (item.status) {
                    DownloadStatus.COMPLETED -> AuroraEmerald
                    DownloadStatus.FAILED -> CoralDanger
                    DownloadStatus.DOWNLOADING -> ElectricCyan
                    else -> SolarWarning
                }
                LiquidGlassPill(
                    tint = statusColor,
                    cornerRadius = 8.dp
                ) {
                    Text(
                        text = item.status.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Animated Gradient Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(SquircleShape(3.dp))
                    .background(Color(0x18FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(SquircleShape(3.dp))
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

            // Sparkline for actively downloading files
            if (isDownloading && speedHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                BandwidthSparkline(
                    history = speedHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp),
                    lineColor = ElectricCyan,
                    fillColor = VibrantAzure
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
                        .background(CoralDanger.copy(alpha = 0.12f))
                        .border(0.5.dp, CoralDanger.copy(alpha = 0.35f), SquircleShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = CoralDanger,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${classified.title}: ${classified.userMessage}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CoralDanger,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = classified.recoveryGuidance,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Row: Parallel Streams & Velocity info + Micro-Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isParallelTransfer) {
                        LiquidGlassPill(
                            tint = ElectricCyan,
                            cornerRadius = 6.dp
                        ) {
                            Text(
                                text = "${item.effectiveConnectionCount()}x Streams",
                                fontSize = 10.sp,
                                color = ElectricCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        val singleStreamLabel = if (!item.supportsRanges && item.concurrency > 1 &&
                            item.status != DownloadStatus.QUEUED && item.status != DownloadStatus.DETECTING) {
                            "Single Stream (No Ranges)"
                        } else {
                            "1 Stream"
                        }
                        LiquidGlassPill(cornerRadius = 6.dp) {
                            Text(
                                text = singleStreamLabel,
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }

                    if (isDownloading && speed > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatSpeed(speed),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                    }

                    if (item.totalBytes > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }
                }

                // Minimalist Glass Icon Action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (item.status) {
                        DownloadStatus.DOWNLOADING -> {
                            LiquidGlassIconButton(
                                onClick = onPause,
                                icon = Icons.Default.Pause,
                                contentDescription = "Pause",
                                tint = GoldenAmber,
                                modifier = Modifier.size(32.dp),
                                cornerRadius = 8.dp
                            )
                        }
                        DownloadStatus.PAUSED -> {
                            LiquidGlassIconButton(
                                onClick = onResume,
                                icon = Icons.Default.PlayArrow,
                                contentDescription = "Resume",
                                tint = ElectricCyan,
                                modifier = Modifier.size(32.dp),
                                cornerRadius = 8.dp
                            )
                        }
                        DownloadStatus.FAILED -> {
                            LiquidGlassIconButton(
                                onClick = onRetry,
                                icon = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = ElectricCyan,
                                modifier = Modifier.size(32.dp),
                                cornerRadius = 8.dp
                            )
                        }
                        DownloadStatus.COMPLETED -> {
                            LiquidGlassIconButton(
                                onClick = onShareFile,
                                icon = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = VibrantAzure,
                                modifier = Modifier.size(32.dp),
                                cornerRadius = 8.dp
                            )
                            LiquidGlassIconButton(
                                onClick = onOpenFile,
                                icon = Icons.Default.OpenInNew,
                                contentDescription = "Open",
                                tint = AuroraEmerald,
                                modifier = Modifier.size(32.dp),
                                cornerRadius = 8.dp
                            )
                        }
                        else -> {}
                    }
                    LiquidGlassIconButton(
                        onClick = { if (item.status == DownloadStatus.COMPLETED) onDelete() else onCancel() },
                        icon = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = CoralDanger,
                        modifier = Modifier.size(32.dp),
                        cornerRadius = 8.dp
                    )
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
