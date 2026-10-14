package com.example.bluebridge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val GruvboxDarkColorScheme = darkColorScheme(
    primary = GruvboxGreen,
    onPrimary = GruvboxBg0,
    primaryContainer = GruvboxBg2,
    onPrimaryContainer = GruvboxFg0,
    secondary = GruvboxYellow,
    onSecondary = GruvboxBg0,
    tertiary = GruvboxAqua,
    onTertiary = GruvboxBg0,
    background = GruvboxBg0,
    onBackground = GruvboxFg1,
    surface = GruvboxBg1,
    onSurface = GruvboxFg0,
    surfaceVariant = GruvboxBg2,
    onSurfaceVariant = GruvboxFg1,
    surfaceContainer = GruvboxBg1,
    surfaceContainerHigh = GruvboxBg2,
    outline = GruvboxGray,
    outlineVariant = GruvboxBg2,
    error = GruvboxRed,
    onError = GruvboxBg0,
)

// Custom Dark Theme (Deep Slate Background + Lighter Slate Navigation Bar)
private val CustomDarkColorScheme = darkColorScheme(
    primary = AppIndigoPrimary,
    onPrimary = AppIndigoOnPrimary,
    primaryContainer = AppIndigoPrimaryContainer,
    onPrimaryContainer = AppIndigoOnPrimaryContainer,
    secondary = Color(0xFFB388FF),
    onSecondary = Color(0xFF1A0054),
    tertiary = Color(0xFF80CBC4),
    onTertiary = Color(0xFF003732),
    background = CustomDarkBg,
    onBackground = Color(0xFFE2E2E6),
    surface = CustomDarkBg,
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = CustomDarkSurfaceVariant, // Navigation Bar
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainer = CustomDarkSurfaceVariant,
    surfaceContainerHigh = Color(0xFF2D2D2D),
    outline = Color(0xFF49454F),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

// Custom Light Theme (Off-white Background + Muted Darker Gray Navigation Bar)
private val CustomLightColorScheme = lightColorScheme(
    primary = AppIndigoPrimary,
    onPrimary = AppIndigoOnPrimary,
    primaryContainer = AppIndigoPrimaryContainer,
    onPrimaryContainer = AppIndigoOnPrimaryContainer,
    secondary = Color(0xFF5E35B1),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF00695C),
    onTertiary = Color(0xFFFFFFFF),
    background = CustomLightBg,
    onBackground = Color(0xFF1C1B1F),
    surface = CustomLightBg,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = CustomLightSurfaceVariant, // Navigation Bar
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainer = CustomLightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFD0BCFF),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFF79747E),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun BlueBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    useGruvbox: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        useGruvbox -> GruvboxDarkColorScheme
        (dynamicColor && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)) -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> CustomDarkColorScheme
        else -> CustomLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
