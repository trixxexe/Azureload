package com.ritam.azureload.feature.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritam.azureload.feature.dashboard.formatBytes
import com.ritam.azureload.ui.components.LiquidGlassCard
import com.ritam.azureload.ui.components.LiquidGlassIconButton
import com.ritam.azureload.ui.components.LiquidGlassPill
import com.ritam.azureload.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    stagingDir: File,
    activeJobsCount: Int,
    onBack: () -> Unit
) {
    val usableSpace = stagingDir.usableSpace
    val totalSpace = stagingDir.totalSpace
    val partFiles = stagingDir.listFiles { _, name -> name.endsWith(".part") } ?: emptyArray()

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
                    text = "Diagnostics & Health",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.size(40.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Storage Health Card
            Text(
                text = "Storage & Buffer Safety",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ElectricCyan,
                letterSpacing = 0.5.sp
            )

            LiquidGlassCard(cornerRadius = 22.dp) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(SquircleShape(10.dp))
                                .background(ElectricCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Internal Storage & Staging", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Free Space: ${formatBytes(usableSpace)}", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("Total Capacity: ${formatBytes(totalSpace)}", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Staging Location: ${stagingDir.absolutePath}", fontSize = 11.sp, color = TextMuted)
                    Text("Active Staging Part Files: ${partFiles.size}", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(10.dp))
                    LiquidGlassPill(tint = AuroraEmerald, cornerRadius = 8.dp) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuroraEmerald, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pre-allocation Space Guard Active", fontSize = 11.sp, color = AuroraEmerald, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Memory Leak & Concurrency State
            Text(
                text = "Memory Leak Protection & Concurrency",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ElectricCyan,
                letterSpacing = 0.5.sp
            )

            LiquidGlassCard(cornerRadius = 22.dp) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(SquircleShape(10.dp))
                                .background(CyberViolet.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = CyberViolet, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Mobile Best Practices & Lifecycle", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Active Coordinator Jobs: $activeJobsCount", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Lifecycle-Aware Flow Collection: Halts collection when backgrounded via collectAsStateWithLifecycle", fontSize = 11.sp, color = TextSecondary)
                    Text("• WebView Memory Leak Prevention: Explicitly torn down on composition disposal", fontSize = 11.sp, color = TextSecondary)
                    Text("• Bounded I/O Buffers: 32KB/64KB chunk buffers prevent OutOfMemory on multi-gigabyte transfers", fontSize = 11.sp, color = TextSecondary)
                    Text("• OkHttp Connection Pool: 32 idle connections bounded to 5-minute keepalive", fontSize = 11.sp, color = TextSecondary)
                    Text("• Foreground Service Scoping: Automatically stops when all downloads conclude or pause", fontSize = 11.sp, color = TextSecondary)
                }
            }

            // Structured Error Classification Matrix
            Text(
                text = "Structured Error Classification Matrix",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ElectricCyan,
                letterSpacing = 0.5.sp
            )

            LiquidGlassCard(cornerRadius = 22.dp) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "Azureload classifies all failures into structured error models with tailored recovery guidance:",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val errorGuides = listOf(
                        Triple("DNS Resolution", "UnknownHostException, unresolvable domains", "Prompts user to verify internet connection and domain spelling."),
                        Triple("SSL / TLS Handshake", "Certificate errors, SSL handshake failure", "Guides user to check device time/date, proxy, or VPN settings."),
                        Triple("Socket Timeout", "SocketTimeoutException, ConnectException", "Applies exponential backoff with jitter and enables seamless resumption."),
                        Triple("HTTP 4xx Client Errors", "401/403 (Auth/Forbidden), 404 (Missing), 429 (Rate-limited)", "Provides clear authentication notice, checks cookies, handles Retry-After delays."),
                        Triple("HTTP 5xx Server Errors", "500, 502, 503, 504 server crashes and outages", "Classifies as temporary host outage and schedules automatic recovery."),
                        Triple("Disk Storage Full", "ENOSPC, insufficient disk capacity", "Guards against pre-allocation failures and prompts user to free device memory."),
                        Triple("Disk I/O Failure", "EIO, filesystem write error, storage corruption", "Guides user to verify storage medium mount state and filesystem health."),
                        Triple("Permission Denied", "EACCES, Scoped Storage access restrictions", "Informs user to grant file permissions or use internal staging storage."),
                        Triple("SHA-256 Mismatch", "Integrity checksum verification failure", "Safely flags corrupted downloads and provides one-tap restart.")
                    )

                    errorGuides.forEachIndexed { index, (name, trigger, recovery) ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LiquidGlassPill(tint = ElectricCyan, cornerRadius = 6.dp) {
                                    Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElectricCyan)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Triggers: $trigger", fontSize = 11.sp, color = TextMuted)
                            Text("Recovery: $recovery", fontSize = 11.sp, color = TextPrimary)
                        }
                        if (index < errorGuides.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0x18FFFFFF))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
