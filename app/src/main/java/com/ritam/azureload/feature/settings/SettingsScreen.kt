package com.ritam.azureload.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritam.azureload.core.settings.SettingsManager
import com.ritam.azureload.feature.dashboard.formatSpeed
import com.ritam.azureload.ui.components.LiquidGlassCard
import com.ritam.azureload.ui.components.LiquidGlassIconButton
import com.ritam.azureload.ui.components.LiquidGlassPill
import com.ritam.azureload.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val wifiOnly by settingsManager.wifiOnlyFlow.collectAsStateWithLifecycle()
    val defaultConcurrency by settingsManager.defaultConcurrencyFlow.collectAsStateWithLifecycle()
    val maxActiveTransfers by settingsManager.maxActiveTransfersFlow.collectAsStateWithLifecycle()
    val globalSpeedLimit by settingsManager.globalSpeedLimitFlow.collectAsStateWithLifecycle()

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
                    text = "Settings",
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
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Network & Concurrency
            Text(
                text = "Network & Concurrency",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ElectricCyan,
                letterSpacing = 0.5.sp
            )

            LiquidGlassCard(cornerRadius = 22.dp) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Only download over Wi-Fi", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("Prevent transfers on cellular data", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { settingsManager.setWifiOnly(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureBlack,
                                checkedTrackColor = ElectricCyan
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = Color(0x18FFFFFF))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Default Concurrency", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("$defaultConcurrency Streams", fontWeight = FontWeight.Bold, color = ElectricCyan)
                    }
                    Slider(
                        value = defaultConcurrency.toFloat(),
                        onValueChange = { settingsManager.setDefaultConcurrency(it.toInt()) },
                        valueRange = 1f..16f,
                        steps = 14,
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricCyan,
                            activeTrackColor = VibrantAzure
                        )
                    )
                    Text("Parallel range streams allocated per new download.", fontSize = 11.sp, color = TextMuted)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = Color(0x18FFFFFF))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Max Concurrent Active Transfers", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("$maxActiveTransfers Active", fontWeight = FontWeight.Bold, color = ElectricCyan)
                    }
                    Slider(
                        value = maxActiveTransfers.toFloat(),
                        onValueChange = { settingsManager.setMaxActiveTransfers(it.toInt()) },
                        valueRange = 1f..6f,
                        steps = 4,
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricCyan,
                            activeTrackColor = VibrantAzure
                        )
                    )
                    Text("Transfers exceeding this limit wait in QUEUED state.", fontSize = 11.sp, color = TextMuted)
                }
            }

            // Global Bandwidth Throttle
            Text(
                text = "Bandwidth & Speed Limits",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ElectricCyan,
                letterSpacing = 0.5.sp
            )

            LiquidGlassCard(cornerRadius = 22.dp) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Global Speed Limit", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        Text(
                            text = if (globalSpeedLimit > 0) formatSpeed(globalSpeedLimit) else "Unlimited",
                            fontWeight = FontWeight.Bold,
                            color = if (globalSpeedLimit > 0) SolarWarning else ElectricCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

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
                            val isSelected = globalSpeedLimit == bytes
                            LiquidGlassPill(
                                tint = if (isSelected) ElectricCyan else null,
                                isSolid = isSelected,
                                cornerRadius = 12.dp,
                                onClick = { settingsManager.setGlobalSpeedLimit(bytes) }
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Applies across all active transfers simultaneously to preserve bandwidth.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            // About Azureload
            Text(
                text = "About Azureload",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ElectricCyan,
                letterSpacing = 0.5.sp
            )

            LiquidGlassCard(cornerRadius = 22.dp) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Azureload Liquid Glass", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Version 1.0.0 • iOS Liquid Glass Edition", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Multi-connection download accelerator featuring continuous G2 superellipse curves, radiant liquid glass materials, and mobile lifecycle safeguards.",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Package: com.ritam.azureload", fontSize = 11.sp, color = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
