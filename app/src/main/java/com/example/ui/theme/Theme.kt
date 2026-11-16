package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PirateDarkColorScheme = darkColorScheme(
    primary = TreasureGold,
    onPrimary = InkDark,
    primaryContainer = WarmTeak,
    onPrimaryContainer = AntiqueParchment,
    secondary = LagoonTeal,
    onSecondary = AbyssalNavy,
    secondaryContainer = DeepOcean,
    onSecondaryContainer = BioluminescentCyan,
    tertiary = CrimsonCorsair,
    onTertiary = CreamText,
    background = AbyssalNavy,
    onBackground = CreamText,
    surface = MahoganyWood,
    onSurface = CreamText,
    surfaceVariant = DeepOcean,
    onSurfaceVariant = MutedSand,
    outline = BrassBorder,
    error = ErrorCoral,
    onError = InkDark
)

val PirateShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun PirateRealmsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PirateDarkColorScheme,
        typography = AppTypography,
        shapes = PirateShapes,
        content = content
    )
}
