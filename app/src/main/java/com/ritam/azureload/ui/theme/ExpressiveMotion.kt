package com.ritam.azureload.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Shared iOS Expressive motion physics for Azureload.
 * Provides fluid spring physics for interactive squishes, scale feedback, and layout transitions.
 */
object ExpressiveMotion {
    const val Quick = 160
    const val Standard = 300
    const val Emphasized = 440

    fun <T> spatialFast() = spring<T>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMedium, // 1500f: immediate, tactile button feedback (~180ms)
    )

    fun <T> spatialDefault() = spring<T>(
        dampingRatio = 0.8f,
        stiffness = 650f, // Snappy & fluid iOS expressive motion (~280ms)
    )

    fun <T> spatialSlow() = spring<T>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow, // 400f
    )

    fun <T> effectsFast() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium, // 1500f
    )

    fun <T> effectsDefault() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 700f, // Clean fade without sluggish trailing (~250ms)
    )
}

/**
 * Tactile spring press feedback modifier.
 * Subtly squishes the component when touched and springs back dynamically on release.
 */
@Composable
fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.965f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = if (isPressed) ExpressiveMotion.spatialFast() else ExpressiveMotion.spatialDefault(),
        label = "expressive_press_scale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
