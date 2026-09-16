package com.arjun.gander.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = FluentBlue,
    onPrimary = FluentLightSurfaceRaised,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFD7E9FF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001D35),
    secondary = androidx.compose.ui.graphics.Color(0xFF4F5F70),
    onSecondary = FluentLightSurfaceRaised,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFD3E4F7),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF0B1D2A),
    background = FluentLightBackground,
    onBackground = FluentLightText,
    surface = FluentLightSurface,
    onSurface = FluentLightText,
    surfaceVariant = FluentLightSurfaceMuted,
    onSurfaceVariant = FluentLightTextMuted,
    outline = FluentLightBorder,
    outlineVariant = FluentLightBorderSubtle,
)

private val DarkColors = darkColorScheme(
    primary = FluentBlueDark,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00344F),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF004C70),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFC8E6FF),
    secondary = androidx.compose.ui.graphics.Color(0xFFB7C8DB),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF22323F),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF394956),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFD3E4F7),
    background = FluentDarkBackground,
    onBackground = FluentDarkText,
    surface = FluentDarkSurface,
    onSurface = FluentDarkText,
    surfaceVariant = FluentDarkSurfaceMuted,
    onSurfaceVariant = FluentDarkTextMuted,
    outline = FluentDarkBorder,
    outlineVariant = FluentDarkBorderSubtle,
)

internal val VaultShelfShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun VaultShelfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = VaultShelfTypography,
        shapes = VaultShelfShapes,
        content = content,
    )
}
