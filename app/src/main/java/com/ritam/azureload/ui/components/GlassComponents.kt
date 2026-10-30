package com.ritam.azureload.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritam.azureload.core.network.ClassifiedDownloadError
import com.ritam.azureload.core.network.ErrorCategory
import com.ritam.azureload.ui.theme.*

/**
 * Standard Liquid Glass Card with continuous-curvature squircle shape,
 * translucent frosted glass ambient gradient, and specular lighting highlight border.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    isHighlighted: Boolean = false,
    highlightColor: Color = NeonCyan,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = remember(cornerRadius) { SquircleShape(cornerRadius) }

    val borderTopColor by animateColorAsState(
        targetValue = if (isHighlighted) highlightColor.copy(alpha = 0.65f) else Color(0x4AFFFFFF),
        animationSpec = tween(300),
        label = "borderTop"
    )
    val borderBottomColor by animateColorAsState(
        targetValue = if (isHighlighted) highlightColor.copy(alpha = 0.20f) else Color(0x10FFFFFF),
        animationSpec = tween(300),
        label = "borderBottom"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isHighlighted) {
                        listOf(
                            highlightColor.copy(alpha = 0.14f),
                            Color(0x10152538),
                            Color(0x0A0D1826)
                        )
                    } else {
                        listOf(
                            Color(0x22FFFFFF),
                            Color(0x0E1A293E),
                            Color(0x08101C2B)
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(borderTopColor, borderBottomColor)
                ),
                shape = shape
            ),
        content = content
    )
}

/**
 * Liquid Glass Pill / Capsule for chips, status tags, badges, and quick filters.
 */
@Composable
fun LiquidGlassPill(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 100.dp,
    tint: Color? = null,
    borderAlpha: Float = 0.35f,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val shape = remember(cornerRadius) { SquircleShape(cornerRadius) }
    val baseModifier = if (onClick != null) {
        modifier
            .clip(shape)
            .clickable { onClick() }
    } else {
        modifier.clip(shape)
    }

    val bgBrush = if (tint != null) {
        Brush.verticalGradient(
            colors = listOf(
                tint.copy(alpha = 0.22f),
                tint.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x24FFFFFF),
                Color(0x0CFFFFFF)
            )
        )
    }

    val borderBrush = if (tint != null) {
        Brush.verticalGradient(
            colors = listOf(
                tint.copy(alpha = (borderAlpha + 0.2f).coerceAtMost(1f)),
                tint.copy(alpha = (borderAlpha * 0.5f).coerceAtMost(1f))
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x45FFFFFF),
                Color(0x10FFFFFF)
            )
        )
    }

    Row(
        modifier = baseModifier
            .background(bgBrush)
            .border(1.dp, borderBrush, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

/**
 * Liquid Glass Button with squircle shape, specular border, and neon accents.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = AzurePrimary,
    cornerRadius: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val shape = remember(cornerRadius) { SquircleShape(cornerRadius) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = if (enabled) {
                        listOf(
                            accentColor.copy(alpha = 0.28f),
                            accentColor.copy(alpha = 0.12f)
                        )
                    } else {
                        listOf(
                            Color(0x10FFFFFF),
                            Color(0x05FFFFFF)
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (enabled) {
                        listOf(
                            accentColor.copy(alpha = 0.65f),
                            accentColor.copy(alpha = 0.20f)
                        )
                    } else {
                        listOf(
                            Color(0x1AFFFFFF),
                            Color(0x08FFFFFF)
                        )
                    }
                ),
                shape = shape
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple()
            ) { onClick() }
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * Liquid Glass Stat Card Widget with specular rim and squircle icon container.
 */
@Composable
fun LiquidGlassStatWidget(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color = AzurePrimary
) {
    LiquidGlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(SquircleShape(12.dp))
                    .background(accentColor.copy(alpha = 0.18f))
                    .border(1.dp, accentColor.copy(alpha = 0.45f), SquircleShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    color = AzureOnSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = value,
                    color = AzureOnSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = subtitle,
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Liquid Glass Error Card displaying classified download errors with
 * structured classification badge, user-friendly diagnosis, actionable recovery guidance,
 * and retry/copy buttons.
 */
@Composable
fun LiquidGlassErrorCard(
    error: ClassifiedDownloadError,
    onRetry: (() -> Unit)? = null,
    onCopyDetails: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val categoryColor = when (error.category) {
        ErrorCategory.DNS, ErrorCategory.NETWORK_OFFLINE -> AzureWarning
        ErrorCategory.SSL -> NeonPink
        ErrorCategory.TIMEOUT -> AzureWarning
        ErrorCategory.STORAGE_FULL -> Color(0xFFFF9100)
        ErrorCategory.PERMISSION_DENIED -> AzureError
        ErrorCategory.INTEGRITY_MISMATCH -> Color(0xFFFF1744)
        ErrorCategory.HTTP_CLIENT -> AzureError
        ErrorCategory.HTTP_SERVER -> Color(0xFFFF5252)
        ErrorCategory.UNKNOWN -> AzureError
    }

    val categoryIcon = when (error.category) {
        ErrorCategory.DNS, ErrorCategory.NETWORK_OFFLINE -> Icons.Default.WifiOff
        ErrorCategory.SSL -> Icons.Default.Lock
        ErrorCategory.TIMEOUT -> Icons.Default.HourglassEmpty
        ErrorCategory.STORAGE_FULL -> Icons.Default.DiscFull
        ErrorCategory.PERMISSION_DENIED -> Icons.Default.Security
        ErrorCategory.INTEGRITY_MISMATCH -> Icons.Default.ChecklistRtl
        ErrorCategory.HTTP_CLIENT -> Icons.Default.Warning
        ErrorCategory.HTTP_SERVER -> Icons.Default.CloudOff
        ErrorCategory.UNKNOWN -> Icons.Default.ErrorOutline
    }

    LiquidGlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        isHighlighted = true,
        highlightColor = categoryColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Category Badge & Status Code
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassPill(
                    tint = categoryColor,
                    cornerRadius = 8.dp
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = error.category.name.replace('_', ' '),
                        color = categoryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (error.httpStatusCode != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    LiquidGlassPill(
                        tint = categoryColor.copy(alpha = 0.8f),
                        cornerRadius = 8.dp
                    ) {
                        Text(
                            text = "HTTP ${error.httpStatusCode}",
                            color = AzureOnSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Error Title & User Message
            Text(
                text = error.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = AzureOnSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = error.userMessage,
                fontSize = 12.sp,
                color = AzureOnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Actionable Recovery Guidance Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SquircleShape(10.dp))
                    .background(Color(0x18FFFFFF))
                    .border(0.5.dp, Color(0x28FFFFFF), SquircleShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Recommended Action",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = categoryColor
                        )
                        Text(
                            text = error.recoveryGuidance,
                            fontSize = 11.sp,
                            color = AzureOnSurface
                        )
                    }
                }
            }

            // Raw technical details snippet if present
            if (!error.rawMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Technical Details: ${error.rawMessage}",
                    fontSize = 10.sp,
                    color = AzureOnSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Buttons: Retry / Copy Details
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onCopyDetails != null) {
                    LiquidGlassButton(
                        onClick = onCopyDetails,
                        accentColor = AzureOnSurfaceVariant,
                        cornerRadius = 10.dp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = AzureOnSurface)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Info", fontSize = 11.sp, color = AzureOnSurface)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (onRetry != null) {
                    LiquidGlassButton(
                        onClick = onRetry,
                        accentColor = if (error.isRetryable) AzurePrimary else AzureWarning,
                        cornerRadius = 10.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureBlack)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (error.isRetryable) "Retry Now" else "Force Retry",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureBlack
                        )
                    }
                }
            }
        }
    }
}
