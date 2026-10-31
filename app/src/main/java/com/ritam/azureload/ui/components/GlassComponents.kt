package com.ritam.azureload.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
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
 * Ambient Luminous Backdrop for iOS Liquid Glass aesthetics.
 * Renders an ultra-deep obsidian background populated with soft, ethereal radiant color orbs
 * (Electric Cyan, Cyber Violet, Neon Magenta, Mint Teal) that frosted liquid glass surfaces
 * physically refract and specularly reflect.
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicVoid)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Top-left luminous Cyan / Azure orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricCyan.copy(alpha = 0.16f),
                        VibrantAzure.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.15f, h * 0.12f),
                    radius = (w * 0.75f).coerceAtLeast(300f)
                )
            )

            // Top-right / Center-right luminous Violet / Magenta orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonMagenta.copy(alpha = 0.12f),
                        CyberViolet.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.88f, h * 0.32f),
                    radius = (w * 0.70f).coerceAtLeast(280f)
                )
            )

            // Bottom-center subtle Mint / Teal glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MintTeal.copy(alpha = 0.09f),
                        AzurePrimary.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.45f, h * 0.82f),
                    radius = (w * 0.65f).coerceAtLeast(260f)
                )
            )
        }

        content()
    }
}

/**
 * Standard Liquid Glass Card with continuous-curvature G2 squircle shape,
 * translucent frosted glass ambient gradient, specular lighting highlight border,
 * and tactile iOS spring press physics.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    isHighlighted: Boolean = false,
    highlightColor: Color = ElectricCyan,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = remember(cornerRadius) { SquircleShape(cornerRadius) }
    val interactionSource = remember { MutableInteractionSource() }

    val borderTopColor by animateColorAsState(
        targetValue = if (isHighlighted) highlightColor.copy(alpha = 0.75f) else Color(0x55FFFFFF),
        animationSpec = tween(280),
        label = "glassBorderTop"
    )
    val borderBottomColor by animateColorAsState(
        targetValue = if (isHighlighted) highlightColor.copy(alpha = 0.22f) else Color(0x12FFFFFF),
        animationSpec = tween(280),
        label = "glassBorderBottom"
    )

    val surfaceColors = if (isHighlighted) {
        listOf(
            highlightColor.copy(alpha = 0.14f),
            Color(0x18152538),
            Color(0x0C0D1826)
        )
    } else {
        listOf(
            Color(0x22FFFFFF),
            Color(0x0E142236),
            Color(0x0809111D)
        )
    }

    val interactiveModifier = if (onClick != null) {
        Modifier
            .expressivePressScale(interactionSource)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple()
            ) { onClick() }
    } else {
        Modifier.clip(shape)
    }

    Box(
        modifier = modifier
            .then(interactiveModifier)
            .background(brush = Brush.verticalGradient(colors = surfaceColors))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(borderTopColor, borderBottomColor)),
                shape = shape
            ),
        content = content
    )
}

/**
 * Frosted Glass Card alias conforming to LastWave/FloWave design system.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        onClick = onClick,
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
    isSolid: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val shape = remember(cornerRadius) { SquircleShape(cornerRadius) }
    val interactionSource = remember { MutableInteractionSource() }

    val interactiveModifier = if (onClick != null) {
        modifier
            .expressivePressScale(interactionSource, targetScale = 0.95f)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple()
            ) { onClick() }
    } else {
        modifier.clip(shape)
    }

    val bgBrush = when {
        isSolid -> {
            val primaryColor = tint ?: ElectricCyan
            val secondaryColor = when (primaryColor) {
                ElectricCyan -> VibrantAzure
                NeonMagenta -> CyberViolet
                AuroraEmerald -> MintTeal
                TangerineGlow -> GoldenAmber
                else -> primaryColor.copy(alpha = 0.85f)
            }
            Brush.horizontalGradient(listOf(primaryColor, secondaryColor))
        }
        tint != null -> {
            Brush.verticalGradient(
                listOf(
                    tint.copy(alpha = 0.22f),
                    tint.copy(alpha = 0.08f)
                )
            )
        }
        else -> {
            Brush.verticalGradient(
                listOf(
                    Color(0x22FFFFFF),
                    Color(0x0CFFFFFF)
                )
            )
        }
    }

    val borderBrush = when {
        isSolid -> Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x25FFFFFF)))
        tint != null -> Brush.verticalGradient(
            listOf(
                tint.copy(alpha = (borderAlpha + 0.25f).coerceAtMost(1f)),
                tint.copy(alpha = (borderAlpha * 0.4f).coerceAtMost(1f))
            )
        )
        else -> Brush.verticalGradient(
            listOf(
                Color(0x45FFFFFF),
                Color(0x10FFFFFF)
            )
        )
    }

    Row(
        modifier = interactiveModifier
            .background(bgBrush)
            .border(1.dp, borderBrush, shape)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

/**
 * Liquid Glass Button with squircle shape, specular border, radiant neons, and fluid spring physics.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSolid: Boolean = false,
    accentColor: Color = ElectricCyan,
    cornerRadius: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
    content: @Composable RowScope.() -> Unit
) {
    val shape = remember(cornerRadius) { SquircleShape(cornerRadius) }
    val interactionSource = remember { MutableInteractionSource() }

    val bgBrush = when {
        !enabled -> Brush.verticalGradient(listOf(Color(0x10FFFFFF), Color(0x05FFFFFF)))
        isSolid -> {
            val startColor = if (accentColor == AzurePrimary) ElectricCyan else accentColor
            val endColor = if (accentColor == AzurePrimary) VibrantAzure else accentColor.copy(alpha = 0.85f)
            Brush.horizontalGradient(listOf(startColor, endColor))
        }
        else -> Brush.verticalGradient(
            listOf(
                accentColor.copy(alpha = 0.25f),
                accentColor.copy(alpha = 0.09f)
            )
        )
    }

    val borderBrush = when {
        !enabled -> Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x08FFFFFF)))
        isSolid -> Brush.verticalGradient(listOf(Color(0x90FFFFFF), Color(0x28FFFFFF)))
        else -> Brush.verticalGradient(
            listOf(
                accentColor.copy(alpha = 0.70f),
                accentColor.copy(alpha = 0.20f)
            )
        )
    }

    Box(
        modifier = modifier
            .expressivePressScale(interactionSource, targetScale = 0.96f)
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderBrush, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
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
 * Liquid Glass Icon Button: sleek frosted glass circular or squircle icon button.
 */
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(40.dp),
    tint: Color = TextPrimary,
    accentBg: Color? = null,
    cornerRadius: Dp = 100.dp
) {
    val shape = remember(cornerRadius) { SquircleShape(cornerRadius) }
    val interactionSource = remember { MutableInteractionSource() }

    val bgBrush = if (accentBg != null) {
        Brush.verticalGradient(listOf(accentBg.copy(alpha = 0.25f), accentBg.copy(alpha = 0.08f)))
    } else {
        Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x0CFFFFFF)))
    }

    val borderBrush = if (accentBg != null) {
        Brush.verticalGradient(listOf(accentBg.copy(alpha = 0.65f), accentBg.copy(alpha = 0.18f)))
    } else {
        Brush.verticalGradient(listOf(Color(0x50FFFFFF), Color(0x12FFFFFF)))
    }

    Box(
        modifier = modifier
            .expressivePressScale(interactionSource, targetScale = 0.92f)
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderBrush, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple()
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
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
    accentColor: Color = ElectricCyan
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
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = value,
                    color = TextPrimary,
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
 * Liquid Glass Action Tile widget for dashboard quick shortcuts and operations.
 */
@Composable
fun LiquidGlassActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = ElectricCyan
) {
    LiquidGlassCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp
            )
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
        ErrorCategory.DNS, ErrorCategory.NETWORK_OFFLINE -> SolarWarning
        ErrorCategory.SSL -> NeonMagenta
        ErrorCategory.TIMEOUT -> SolarWarning
        ErrorCategory.STORAGE_FULL -> TangerineGlow
        ErrorCategory.DISK_IO -> TangerineGlow
        ErrorCategory.PERMISSION_DENIED -> CoralDanger
        ErrorCategory.INTEGRITY_MISMATCH -> NeonMagenta
        ErrorCategory.HTTP_CLIENT -> CoralDanger
        ErrorCategory.HTTP_SERVER -> SunsetCoral
        ErrorCategory.UNKNOWN -> CoralDanger
    }

    val categoryIcon = when (error.category) {
        ErrorCategory.DNS, ErrorCategory.NETWORK_OFFLINE -> Icons.Default.WifiOff
        ErrorCategory.SSL -> Icons.Default.Lock
        ErrorCategory.TIMEOUT -> Icons.Default.HourglassEmpty
        ErrorCategory.STORAGE_FULL -> Icons.Default.DiscFull
        ErrorCategory.DISK_IO -> Icons.Default.Save
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
                            color = TextPrimary,
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
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = error.userMessage,
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Actionable Recovery Guidance Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SquircleShape(10.dp))
                    .background(Color(0x16FFFFFF))
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
                            color = TextPrimary
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
                    color = TextMuted,
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
                        accentColor = TextSecondary,
                        cornerRadius = 10.dp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Info", fontSize = 11.sp, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (onRetry != null) {
                    LiquidGlassButton(
                        onClick = onRetry,
                        isSolid = true,
                        accentColor = if (error.isRetryable) ElectricCyan else SolarWarning,
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
