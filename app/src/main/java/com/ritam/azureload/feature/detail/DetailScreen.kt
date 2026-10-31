package com.ritam.azureload.feature.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
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
import com.ritam.azureload.core.network.ErrorClassifier
import com.ritam.azureload.core.storage.ChecksumResult
import com.ritam.azureload.core.storage.IntegrityVerifier
import com.ritam.azureload.feature.dashboard.formatBytes
import com.ritam.azureload.feature.dashboard.formatSpeed
import com.ritam.azureload.ui.components.*
import com.ritam.azureload.ui.theme.*
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

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteFileOnDisk by remember { mutableStateOf(false) }

    var currentLimitBytes by remember(download?.speedLimitBytesPerSec) {
        mutableStateOf(download?.speedLimitBytesPerSec ?: 0L)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassIconButton(
                    onClick = onBack,
                    icon = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(40.dp)
                )

                Text(
                    text = "Transfer Details",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )

                if (download != null && download.status == DownloadStatus.COMPLETED) {
                    LiquidGlassIconButton(
                        onClick = { onShareFile(download) },
                        icon = Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(40.dp),
                        tint = VibrantAzure
                    )
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }
        }
    ) { padding ->
        if (download == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Download not found or was removed.", color = TextMuted)
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

        val etaText = when {
            download.status == DownloadStatus.COMPLETED -> "Finished"
            download.status == DownloadStatus.PAUSED -> "Paused"
            download.status == DownloadStatus.FAILED -> "Failed"
            download.status == DownloadStatus.QUEUED -> "Queued"
            speed > 0 && remainingBytes > 0 -> {
                val seconds = remainingBytes / speed
                val mins = seconds / 60
                val secs = seconds % 60
                if (mins > 60) "${mins / 60}h ${mins % 60}m" else "${mins}m ${secs}s"
            }
            totalBytes <= 0 && download.status == DownloadStatus.DOWNLOADING -> "Unknown"
            else -> "Calculating..."
        }

        val isParallel = download.isParallelTransfer
        val connectionValue = if (!isParallel) {
            if (download.status == DownloadStatus.DOWNLOADING) "1 Active Stream" else "1 Stream"
        } else if (chunks.isNotEmpty()) {
            if (download.status == DownloadStatus.DOWNLOADING) {
                val activeCount = chunks.count { it.status == ChunkStatus.DOWNLOADING }
                if (activeCount > 0) "$activeCount / ${chunks.size} Active" else "${chunks.size} Streams"
            } else {
                "${chunks.size} Streams"
            }
        } else {
            "${download.effectiveConnectionCount()} Streams"
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
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error Card (if failed or error present)
            if (download.status == DownloadStatus.FAILED || !download.errorMessage.isNullOrBlank()) {
                item {
                    val classified = remember(download.errorMessage) {
                        ErrorClassifier.parseOrClassify(download.errorMessage)
                    }
                    LiquidGlassErrorCard(
                        error = classified,
                        onRetry = { onRetry(download.id) },
                        onCopyDetails = {
                            val detail = "${classified.title}\n${classified.userMessage}\nGuidance: ${classified.recoveryGuidance}\nDetails: ${download.errorMessage}"
                            copyToClipboard("Error Diagnostics", detail)
                        }
                    )
                }
            }

            // Hero Card: File info, progress, primary actions
            item {
                LiquidGlassCard(
                    cornerRadius = 24.dp,
                    isHighlighted = download.status == DownloadStatus.DOWNLOADING,
                    highlightColor = visuals.accentColor
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(SquircleShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                visuals.accentColor.copy(alpha = 0.32f),
                                                visuals.accentColor.copy(alpha = 0.12f)
                                            )
                                        )
                                    )
                                    .border(1.dp, visuals.accentColor.copy(alpha = 0.5f), SquircleShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = visuals.icon,
                                    contentDescription = null,
                                    tint = visuals.accentColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = download.fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = visuals.categoryLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = visuals.accentColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val statusColor = when (download.status) {
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
                                            text = download.status.name,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = statusColor
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // URL with Copy action in Liquid Glass Pill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SquircleShape(12.dp))
                                .background(Color(0x14FFFFFF))
                                .border(0.5.dp, Color(0x20FFFFFF), SquircleShape(12.dp))
                                .clickable { copyToClipboard("URL", download.url) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = download.url,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy URL",
                                    modifier = Modifier.size(14.dp),
                                    tint = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Slender Animated Gradient Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
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
                                            when (download.status) {
                                                DownloadStatus.COMPLETED -> listOf(AuroraEmerald, MintTeal)
                                                DownloadStatus.FAILED -> listOf(CoralDanger, SunsetPink)
                                                else -> listOf(ElectricCyan, VibrantAzure)
                                            }
                                        )
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatBytes(downloadedBytes)} / ${if (totalBytes > 0) formatBytes(totalBytes) else "Unknown"}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            if (totalBytes > 0) {
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ElectricCyan
                                )
                            } else if (download.status == DownloadStatus.DOWNLOADING) {
                                Text(
                                    text = "Streaming",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (download.status) {
                                DownloadStatus.DOWNLOADING -> {
                                    LiquidGlassButton(
                                        onClick = { onPause(download.id) },
                                        modifier = Modifier.weight(1f),
                                        accentColor = GoldenAmber
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp), tint = GoldenAmber)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pause", color = GoldenAmber, fontWeight = FontWeight.Bold)
                                    }
                                }
                                DownloadStatus.PAUSED -> {
                                    LiquidGlassButton(
                                        onClick = { onResume(download.id) },
                                        modifier = Modifier.weight(1f),
                                        isSolid = true,
                                        accentColor = ElectricCyan
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp), tint = PureBlack)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Resume", color = PureBlack, fontWeight = FontWeight.Bold)
                                    }
                                }
                                DownloadStatus.FAILED -> {
                                    LiquidGlassButton(
                                        onClick = { onRetry(download.id) },
                                        modifier = Modifier.weight(1f),
                                        isSolid = true,
                                        accentColor = ElectricCyan
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = PureBlack)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry", color = PureBlack, fontWeight = FontWeight.Bold)
                                    }
                                }
                                DownloadStatus.COMPLETED -> {
                                    LiquidGlassButton(
                                        onClick = { onOpenFile(download) },
                                        modifier = Modifier.weight(1f),
                                        isSolid = true,
                                        accentColor = AuroraEmerald
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp), tint = PureBlack)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open File", color = PureBlack, fontWeight = FontWeight.Bold)
                                    }
                                    LiquidGlassButton(
                                        onClick = { onShareFile(download) },
                                        modifier = Modifier.weight(1f),
                                        accentColor = VibrantAzure
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp), tint = VibrantAzure)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share", color = VibrantAzure, fontWeight = FontWeight.Bold)
                                    }
                                }
                                else -> {}
                            }

                            LiquidGlassButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier.weight(if (download.status == DownloadStatus.COMPLETED) 0.8f else 1f),
                                accentColor = CoralDanger
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = CoralDanger)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (download.status == DownloadStatus.COMPLETED) "Delete" else "Cancel", color = CoralDanger)
                            }
                        }
                    }
                }
            }

            // Stats Metrics Grid (Speed, ETA, Streams)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidGlassMetricCard(
                        title = "Speed",
                        value = if (download.status == DownloadStatus.DOWNLOADING) formatSpeed(speed) else "0 KB/s",
                        accentColor = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidGlassMetricCard(
                        title = "ETA",
                        value = etaText,
                        accentColor = SolarWarning,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidGlassMetricCard(
                        title = "Streams",
                        value = connectionValue,
                        accentColor = CyberViolet,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Real-Time Speed Graph Card (Sparkline)
            if (speedHistory.isNotEmpty() || download.status == DownloadStatus.DOWNLOADING) {
                item {
                    LiquidGlassCard(cornerRadius = 20.dp) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ShowChart,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Speed Analytics",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                }
                                Text(
                                    text = if (speedHistory.isNotEmpty()) "Avg: ${formatSpeed(speedHistory.average().toLong())}" else "",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            BandwidthSparkline(
                                history = speedHistory,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp),
                                lineColor = ElectricCyan,
                                fillColor = VibrantAzure,
                                showGridAndLabels = true,
                                currentSpeed = speed
                            )
                        }
                    }
                }
            }

            // Bandwidth Throttling / Speed Limit Card
            item {
                LiquidGlassCard(cornerRadius = 20.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Bandwidth Throttle", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                            }
                            Text(
                                text = if (currentLimitBytes > 0) formatSpeed(currentLimitBytes) else "Unlimited",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentLimitBytes > 0) SolarWarning else ElectricCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

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
                                val isSelected = currentLimitBytes == bytes
                                LiquidGlassPill(
                                    tint = if (isSelected) ElectricCyan else null,
                                    isSolid = isSelected,
                                    cornerRadius = 10.dp,
                                    onClick = {
                                        currentLimitBytes = bytes
                                        onUpdateSpeedLimit(download.id, bytes)
                                    }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) PureBlack else TextPrimary
                                    )
                                }
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
                                text = "Parallel Streams Breakdown (${chunks.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                            Text(
                                text = "${chunks.count { it.status == ChunkStatus.COMPLETED }}/${chunks.size} Completed",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SegmentedChunkBar(chunks = chunks)
                    }
                }

                items(chunks, key = { it.id }) { chunk ->
                    LiquidGlassChunkCard(chunk)
                }
            }

            // Checksum Calculation & Integrity Card
            item {
                LiquidGlassCard(cornerRadius = 20.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = AuroraEmerald, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("File Checksum & Verification", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val targetFile = if (download.status == DownloadStatus.COMPLETED && download.destinationPath.isNotBlank()) {
                            File(download.destinationPath)
                        } else null

                        val fileExists = targetFile?.exists() == true

                        if (fileExists && targetFile != null) {
                            LiquidGlassButton(
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
                                isSolid = true,
                                accentColor = AuroraEmerald,
                                cornerRadius = 12.dp
                            ) {
                                if (isCalculatingChecksum) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PureBlack)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Computing SHA-256 & MD5...", color = PureBlack, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp), tint = PureBlack)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (checksumResult == null) "Calculate File Checksums" else "Re-calculate Checksums", color = PureBlack, fontWeight = FontWeight.Bold)
                                }
                            }

                            checksumResult?.let { res ->
                                Spacer(modifier = Modifier.height(14.dp))

                                // SHA-256 result row
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("SHA-256 Hash", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        IconButton(
                                            onClick = { copyToClipboard("SHA-256", res.sha256) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy SHA-256", modifier = Modifier.size(16.dp), tint = TextSecondary)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(SquircleShape(8.dp))
                                            .background(Color(0x18FFFFFF))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = res.sha256,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // MD5 result row
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("MD5 Hash", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        IconButton(
                                            onClick = { copyToClipboard("MD5", res.md5) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy MD5", modifier = Modifier.size(16.dp), tint = TextSecondary)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(SquircleShape(8.dp))
                                            .background(Color(0x18FFFFFF))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = res.md5,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }

                                // Interactive Compare Checksum input
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = hashInputToCompare,
                                    onValueChange = { hashInputToCompare = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Compare with Expected Hash") },
                                    placeholder = { Text("Paste SHA-256 or MD5 to compare...", fontSize = 12.sp) },
                                    singleLine = true,
                                    shape = SquircleShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = Color(0x10FFFFFF),
                                        focusedContainerColor = Color(0x1EFFFFFF),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )

                                if (hashInputToCompare.isNotBlank()) {
                                    val cleaned = hashInputToCompare.trim()
                                    val matchesSha = cleaned.equals(res.sha256, ignoreCase = true)
                                    val matchesMd5 = cleaned.equals(res.md5, ignoreCase = true)
                                    val isMatch = matchesSha || matchesMd5

                                    Spacer(modifier = Modifier.height(8.dp))
                                    LiquidGlassPill(
                                        tint = if (isMatch) AuroraEmerald else CoralDanger,
                                        cornerRadius = 8.dp
                                    ) {
                                        Icon(
                                            imageVector = if (isMatch) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = if (isMatch) AuroraEmerald else CoralDanger,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isMatch) "Checksum Verified (Matches ${if (matchesSha) "SHA-256" else "MD5"})" else "Checksum Mismatch! File may be corrupt or altered.",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isMatch) AuroraEmerald else CoralDanger
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Checksum computation available once the file has completed downloading and is stored on disk.",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // Technical Metadata Overview Card
            item {
                LiquidGlassCard(cornerRadius = 20.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Technical Metadata",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        MetadataRow("Download ID", "#${download.id}")
                        MetadataRow("Byte Ranges Supported", if (download.supportsRanges) "Yes" else "No")
                        if (!download.supportsRanges && download.concurrency > 1 &&
                            download.status != DownloadStatus.QUEUED && download.status != DownloadStatus.DETECTING) {
                            MetadataRow(
                                "Acceleration Fallback",
                                "Server does not support HTTP byte ranges. Running in single-stream mode."
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
                            Text("Diagnostic Log: ${download.errorMessage}", color = CoralDanger, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                icon = {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = CoralDanger)
                },
                title = {
                    Text(if (download.status == DownloadStatus.COMPLETED) "Delete Download?" else "Cancel Download?")
                },
                text = {
                    Column {
                        Text(
                            if (download.status == DownloadStatus.COMPLETED) {
                                "Remove \"${download.fileName}\" from your download list?"
                            } else {
                                "Cancel active transfer \"${download.fileName}\"?"
                            }
                        )
                        if (download.status == DownloadStatus.COMPLETED && download.destinationPath.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { deleteFileOnDisk = !deleteFileOnDisk }
                            ) {
                                Checkbox(
                                    checked = deleteFileOnDisk,
                                    onCheckedChange = { deleteFileOnDisk = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Also delete file from device storage", fontSize = 13.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            if (deleteFileOnDisk && download.destinationPath.isNotBlank()) {
                                try {
                                    val f = File(download.destinationPath)
                                    if (f.exists()) f.delete()
                                } catch (_: Exception) {}
                            }
                            onCancel(download.id)
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralDanger)
                    ) {
                        Text(if (download.status == DownloadStatus.COMPLETED) "Delete" else "Cancel Download", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Dismiss", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
fun LiquidGlassMetricCard(
    title: String,
    value: String,
    accentColor: Color = ElectricCyan,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(
        modifier = modifier,
        cornerRadius = 16.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title.uppercase(), fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = TextPrimary)
        }
    }
}

@Composable
fun LiquidGlassChunkCard(chunk: ChunkItem) {
    val isChunkDownloading = chunk.status == ChunkStatus.DOWNLOADING
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        isHighlighted = isChunkDownloading,
        highlightColor = ElectricCyan
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
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                LiquidGlassPill(
                    tint = when (chunk.status) {
                        ChunkStatus.COMPLETED -> AuroraEmerald
                        ChunkStatus.DOWNLOADING -> ElectricCyan
                        ChunkStatus.FAILED -> CoralDanger
                        else -> null
                    },
                    cornerRadius = 6.dp
                ) {
                    Text(
                        text = chunk.status.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (chunk.status) {
                            ChunkStatus.COMPLETED -> AuroraEmerald
                            ChunkStatus.DOWNLOADING -> ElectricCyan
                            ChunkStatus.FAILED -> CoralDanger
                            else -> TextSecondary
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Slender progress track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(SquircleShape(2.dp))
                    .background(Color(0x18FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(chunk.progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(SquircleShape(2.dp))
                        .background(
                            when (chunk.status) {
                                ChunkStatus.COMPLETED -> AuroraEmerald
                                ChunkStatus.FAILED -> CoralDanger
                                else -> ElectricCyan
                            }
                        )
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Range: ${chunk.startOffset} .. ${chunk.endOffset}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextMuted
                )
                Text(
                    text = "${formatBytes(chunk.downloadedBytes)} / ${formatBytes(chunk.totalSize)} (${(chunk.progress * 100).toInt()}%)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
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
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = TextMuted)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, color = TextPrimary)
    }
}
