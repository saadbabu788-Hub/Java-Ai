package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AuraDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = CyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = LuminousIndigo,
    onSecondary = ObsidianVoid,
    secondaryContainer = IndigoContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = MintSuccess,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianCard,
    surfaceContainerHigh = ObsidianElevated,
    outline = ObsidianBorder,
    outlineVariant = ColorTokens.SubtleBorder,
    error = CoralError,
    onError = TextPrimary
)

private object ColorTokens {
    val SubtleBorder = androidx.compose.ui.graphics.Color(0xFF1F293D)
}

val AuraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AuraPilotTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AuraDarkColorScheme,
        typography = AuraTypography,
        shapes = AuraShapes,
        content = content
    )
}
