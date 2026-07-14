package com.example.dreamescape_ai.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

/**
 * SCRIPULYA AI uses a fixed dark, starry-night aesthetic. Dynamic coloring is
 * intentionally disabled so the deep-blue/purple palette stays consistent.
 */
private val ScripulyaColorScheme = darkColorScheme(
    primary = ManaBlue,
    onPrimary = NightVoid,
    primaryContainer = ManaBlueDeep,
    onPrimaryContainer = ScripulyaText,
    secondary = ArcanePurple,
    onSecondary = ScripulyaText,
    secondaryContainer = ArcanePurpleDeep,
    onSecondaryContainer = ScripulyaText,
    tertiary = ScripulyaPink,
    onTertiary = ScripulyaText,
    background = BlueVoid,
    onBackground = ScripulyaText,
    surface = NightDeep,
    onSurface = ScripulyaText,
    surfaceVariant = NightPanel,
    onSurfaceVariant = ScripulyaTextDim,
    surfaceContainer = NightPanel,
    surfaceContainerHigh = NightPanelHi,
    outline = NightOutline,
    outlineVariant = NightOutline
)

@Composable
fun Dreamescape_aiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScripulyaColorScheme,
        typography = Typography,
        content = content
    )
}

/** A full-bleed vertical gradient evoking a night sky. Apply as a root background. */
fun Modifier.nightSkyGradient(): Modifier =
    this.background(
        Brush.verticalGradient(
            colors = listOf(
                BlueVoid,
                BlueDeep,
                BlueVoid
            )
        )
    )

/** Convenience wrapper that paints the night-sky gradient behind [content]. */
@Composable
fun StarryBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nightSkyGradient()
    ) {
        content()
    }
}
