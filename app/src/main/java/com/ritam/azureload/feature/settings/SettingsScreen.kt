package com.ritam.azureload.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritam.azureload.core.settings.SettingsManager
import com.ritam.azureload.feature.dashboard.formatSpeed
import com.ritam.azureload.ui.theme.AzureCardBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val wifiOnly by settingsManager.wifiOnlyFlow.collectAsState()
    val defaultConcurrency by settingsManager.defaultConcurrencyFlow.collectAsState()
    val maxActiveTransfers by settingsManager.maxActiveTransfersFlow.collectAsState()
    val globalSpeedLimit by settingsManager.globalSpeedLimitFlow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Network & Concurrency
            Text(
                text = "Network & Concurrency",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, AzureCardBorder.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Only download over Wi-Fi", fontWeight = FontWeight.SemiBold)
                            Text("Prevent transfers on cellular data", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = wifiOnly, onCheckedChange = { settingsManager.setWifiOnly(it) })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Default Concurrency", fontWeight = FontWeight.SemiBold)
                        Text("$defaultConcurrency Threads", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = defaultConcurrency.toFloat(),
                        onValueChange = { settingsManager.setDefaultConcurrency(it.toInt()) },
                        valueRange = 1f..16f,
                        steps = 14
                    )
                    Text("Multi-connection range threads allocated per new download.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Max Concurrent Active Transfers", fontWeight = FontWeight.SemiBold)
                        Text("$maxActiveTransfers Active", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = maxActiveTransfers.toFloat(),
                        onValueChange = { settingsManager.setMaxActiveTransfers(it.toInt()) },
                        valueRange = 1f..6f,
                        steps = 4
                    )
                    Text("Maximum transfers running concurrently. Additional downloads wait in QUEUED state.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Global Bandwidth Throttle
            Text(
                text = "Bandwidth & Speed Limits",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, AzureCardBorder.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Global Download Speed Limit", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = if (globalSpeedLimit > 0) formatSpeed(globalSpeedLimit) else "Unlimited",
                            fontWeight = FontWeight.Bold,
                            color = if (globalSpeedLimit > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val limits = listOf(
                            "Unlimited" to 0L,
                            "1 MB/s" to 1L * 1024L * 1024L,
                            "2 MB/s" to 2L * 1024L * 1024L,
                            "5 MB/s" to 5L * 1024L * 1024L,
                            "10 MB/s" to 10L * 1024L * 1024L,
                            "25 MB/s" to 25L * 1024L * 1024L
                        )
                        limits.forEach { (label, bytes) ->
                            FilterChip(
                                selected = globalSpeedLimit == bytes,
                                onClick = { settingsManager.setGlobalSpeedLimit(bytes) },
                                label = { Text(label, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Applies across all active transfers simultaneously to preserve Wi-Fi/cellular bandwidth.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // About Azureload
            Text(
                text = "About Azureload",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, AzureCardBorder.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Azureload (BOLT Mobile)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Version 1.0.0 (Build 1) • Production Release", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "A desktop-grade multi-connection download accelerator and native manager for Android.",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Package: com.ritam.azureload", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
