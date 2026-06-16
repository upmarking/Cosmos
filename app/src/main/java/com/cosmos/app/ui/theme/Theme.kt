package com.cosmos.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CosmosColorScheme = darkColorScheme(
    primary = CosmosPrimary,
    onPrimary = CosmosOnPrimary,
    primaryContainer = CosmosPrimaryContainer,
    onPrimaryContainer = CosmosOnPrimaryContainer,
    inversePrimary = CosmosInversePrimary,
    secondary = CosmosSecondary,
    onSecondary = CosmosOnSecondary,
    secondaryContainer = CosmosSecondaryContainer,
    onSecondaryContainer = CosmosOnSecondaryContainer,
    tertiary = CosmosTertiary,
    onTertiary = CosmosOnTertiary,
    tertiaryContainer = CosmosTertiaryContainer,
    onTertiaryContainer = CosmosOnTertiaryContainer,
    error = CosmosError,
    onError = CosmosOnError,
    errorContainer = CosmosErrorContainer,
    onErrorContainer = CosmosOnErrorContainer,
    background = CosmosBackground,
    onBackground = CosmosOnBackground,
    surface = CosmosSurface,
    onSurface = CosmosOnSurface,
    surfaceVariant = CosmosSurfaceVariant,
    onSurfaceVariant = CosmosOnSurfaceVariant,
    surfaceTint = CosmosPrimary,
    inverseSurface = CosmosInverseSurface,
    inverseOnSurface = CosmosInverseOnSurface,
    outline = CosmosOutline,
    outlineVariant = CosmosOutlineVariant,
    scrim = CosmosBackground,
)

@Composable
fun CosmosTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = CosmosColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CosmosTypography,
        content = content
    )
}
