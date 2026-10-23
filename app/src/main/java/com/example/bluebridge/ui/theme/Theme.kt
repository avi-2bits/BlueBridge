package com.example.bluebridge.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode(val displayName: String, val subtitle: String) {
    DYNAMIC_MATERIAL("Dynamic Material You", "WALLPAPER ADAPTIVE"),
    MOSS("Moss", "MUTED GREEN"),
    PAPER("Paper", "WARM PARCHMENT"),
    WATER("Water", "COOL AQUATIC"),
    GRUVBOX_DARK("Gruvbox Dark", "WARM DARK")
}

// 1. Gruvbox Dark
private val GruvboxDarkColorScheme = darkColorScheme(
    primary = GruvboxDarkGreen,
    onPrimary = GruvboxDarkBg,
    primaryContainer = GruvboxDarkContainer,
    onPrimaryContainer = GruvboxDarkFg0,
    secondary = GruvboxDarkBlue,
    onSecondary = GruvboxDarkBg,
    tertiary = GruvboxDarkYellow,
    onTertiary = GruvboxDarkBg,
    background = GruvboxDarkBg,
    onBackground = GruvboxDarkFg1,
    surface = GruvboxDarkSurface,
    onSurface = GruvboxDarkFg0,
    surfaceVariant = GruvboxDarkContainer,
    onSurfaceVariant = GruvboxDarkMuted,
    surfaceContainer = GruvboxDarkContainer,
    surfaceContainerHigh = GruvboxDarkSurface,
    outline = GruvboxDarkOutline,
    outlineVariant = GruvboxDarkOutline,
    error = GruvboxDarkRed,
    onError = GruvboxDarkBg,
)

// 2. Moss Dark
private val MossDarkColorScheme = darkColorScheme(
    primary = MossDarkGreen,
    onPrimary = MossDarkBg,
    primaryContainer = MossDarkContainer,
    onPrimaryContainer = MossDarkFg0,
    secondary = Color(0xFFA3B899),
    onSecondary = MossDarkBg,
    tertiary = Color(0xFFB8C5A5),
    onTertiary = MossDarkBg,
    background = MossDarkBg,
    onBackground = MossDarkFg0,
    surface = MossDarkSurface,
    onSurface = MossDarkFg0,
    surfaceVariant = MossDarkContainer,
    onSurfaceVariant = MossDarkMuted,
    surfaceContainer = MossDarkContainer,
    surfaceContainerHigh = MossDarkSurface,
    outline = MossDarkOutline,
    outlineVariant = MossDarkOutline,
    error = Color(0xFFE06C6C),
    onError = MossDarkBg,
)

// 3. Moss Light
private val MossLightColorScheme = lightColorScheme(
    primary = MossLightGreen,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = MossLightContainer,
    onPrimaryContainer = MossLightFg0,
    secondary = Color(0xFF556B52),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF6B705C),
    onTertiary = Color(0xFFFFFFFF),
    background = MossLightBg,
    onBackground = MossLightFg0,
    surface = MossLightSurface,
    onSurface = MossLightFg0,
    surfaceVariant = MossLightContainer,
    onSurfaceVariant = MossLightMuted,
    surfaceContainer = MossLightContainer,
    surfaceContainerHigh = MossLightSurface,
    outline = MossLightOutline,
    outlineVariant = MossLightOutline,
    error = Color(0xFFA83232),
    onError = Color(0xFFFFFFFF),
)

// 4. Paper Dark
private val PaperDarkColorScheme = darkColorScheme(
    primary = PaperDarkGold,
    onPrimary = PaperDarkBg,
    primaryContainer = PaperDarkContainer,
    onPrimaryContainer = PaperDarkFg0,
    secondary = Color(0xFFC4B495),
    onSecondary = PaperDarkBg,
    tertiary = Color(0xFFD69A73),
    onTertiary = PaperDarkBg,
    background = PaperDarkBg,
    onBackground = PaperDarkFg0,
    surface = PaperDarkSurface,
    onSurface = PaperDarkFg0,
    surfaceVariant = PaperDarkContainer,
    onSurfaceVariant = PaperDarkMuted,
    surfaceContainer = PaperDarkContainer,
    surfaceContainerHigh = PaperDarkSurface,
    outline = PaperDarkOutline,
    outlineVariant = PaperDarkOutline,
    error = Color(0xFFE06C6C),
    onError = PaperDarkBg,
)

// 5. Paper Light
private val PaperLightColorScheme = lightColorScheme(
    primary = PaperLightGold,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = PaperLightContainer,
    onPrimaryContainer = PaperLightFg0,
    secondary = Color(0xFF78684A),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF8F5D38),
    onTertiary = Color(0xFFFFFFFF),
    background = PaperLightBg,
    onBackground = PaperLightFg0,
    surface = PaperLightSurface,
    onSurface = PaperLightFg0,
    surfaceVariant = PaperLightContainer,
    onSurfaceVariant = PaperLightMuted,
    surfaceContainer = PaperLightContainer,
    surfaceContainerHigh = PaperLightSurface,
    outline = PaperLightOutline,
    outlineVariant = PaperLightOutline,
    error = Color(0xFFA83232),
    onError = Color(0xFFFFFFFF),
)

// 6. Water Dark
private val WaterDarkColorScheme = darkColorScheme(
    primary = WaterDarkBlue,
    onPrimary = WaterDarkBg,
    primaryContainer = WaterDarkContainer,
    onPrimaryContainer = WaterDarkFg0,
    secondary = Color(0xFF92AEC2),
    onSecondary = WaterDarkBg,
    tertiary = Color(0xFF80CBC4),
    onTertiary = WaterDarkBg,
    background = WaterDarkBg,
    onBackground = WaterDarkFg0,
    surface = WaterDarkSurface,
    onSurface = WaterDarkFg0,
    surfaceVariant = WaterDarkContainer,
    onSurfaceVariant = WaterDarkMuted,
    surfaceContainer = WaterDarkContainer,
    surfaceContainerHigh = WaterDarkSurface,
    outline = WaterDarkOutline,
    outlineVariant = WaterDarkOutline,
    error = Color(0xFFE06C6C),
    onError = WaterDarkBg,
)

// 7. Water Light
private val WaterLightColorScheme = lightColorScheme(
    primary = WaterLightBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = WaterLightContainer,
    onPrimaryContainer = WaterLightFg0,
    secondary = Color(0xFF4A6978),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF3B7A77),
    onTertiary = Color(0xFFFFFFFF),
    background = WaterLightBg,
    onBackground = WaterLightFg0,
    surface = WaterLightSurface,
    onSurface = WaterLightFg0,
    surfaceVariant = WaterLightContainer,
    onSurfaceVariant = WaterLightMuted,
    surfaceContainer = WaterLightContainer,
    surfaceContainerHigh = WaterLightSurface,
    outline = WaterLightOutline,
    outlineVariant = WaterLightOutline,
    error = Color(0xFFA83232),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun createAdaptedDynamicColorScheme(context: Context, darkTheme: Boolean): ColorScheme {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val base = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        val primaryTint = base.primary
        return if (darkTheme) {
            val bgTinted = compositeOver(primaryTint.copy(alpha = 0.08f), Color(0xFF141615))
            val surfaceTinted = compositeOver(primaryTint.copy(alpha = 0.12f), Color(0xFF1B1E1D))
            val containerTinted = compositeOver(primaryTint.copy(alpha = 0.16f), Color(0xFF222624))
            val outlineTinted = compositeOver(primaryTint.copy(alpha = 0.40f), Color(0xFF38403C))

            base.copy(
                background = bgTinted,
                surface = surfaceTinted,
                surfaceVariant = containerTinted,
                surfaceContainer = containerTinted,
                surfaceContainerHigh = compositeOver(primaryTint.copy(alpha = 0.20f), Color(0xFF282D2A)),
                outline = outlineTinted,
                outlineVariant = outlineTinted,
            )
        } else {
            val bgTinted = compositeOver(primaryTint.copy(alpha = 0.06f), Color(0xFFF4F6F4))
            val surfaceTinted = compositeOver(primaryTint.copy(alpha = 0.10f), Color(0xFFE9ECE9))
            val containerTinted = compositeOver(primaryTint.copy(alpha = 0.14f), Color(0xFFDFE4DF))
            val outlineTinted = compositeOver(primaryTint.copy(alpha = 0.35f), Color(0xFF8A968C))

            base.copy(
                background = bgTinted,
                surface = surfaceTinted,
                surfaceVariant = containerTinted,
                surfaceContainer = containerTinted,
                surfaceContainerHigh = compositeOver(primaryTint.copy(alpha = 0.18f), Color(0xFFD5DDD6)),
                outline = outlineTinted,
                outlineVariant = outlineTinted,
            )
        }
    } else {
        return if (darkTheme) MossDarkColorScheme else MossLightColorScheme
    }
}

private fun compositeOver(foreground: Color, background: Color): Color {
    val alpha = foreground.alpha
    if (alpha <= 0f) return background
    val invAlpha = 1f - alpha
    val r = (foreground.red * alpha) + (background.red * invAlpha)
    val g = (foreground.green * alpha) + (background.green * invAlpha)
    val b = (foreground.blue * alpha) + (background.blue * invAlpha)
    return Color(r, g, b, 1f)
}

@Composable
fun getThemeSwatches(themeMode: AppThemeMode, darkTheme: Boolean, context: Context): List<Color> {
    return when (themeMode) {
        AppThemeMode.DYNAMIC_MATERIAL -> {
            val scheme = createAdaptedDynamicColorScheme(context, darkTheme)
            listOf(scheme.background, scheme.surface, scheme.primary, scheme.outline)
        }
        AppThemeMode.MOSS -> {
            if (darkTheme) listOf(MossDarkBg, MossDarkSurface, MossDarkGreen, MossDarkOutline)
            else listOf(MossLightBg, MossLightSurface, MossLightGreen, MossLightOutline)
        }
        AppThemeMode.PAPER -> {
            if (darkTheme) listOf(PaperDarkBg, PaperDarkSurface, PaperDarkGold, PaperDarkOutline)
            else listOf(PaperLightBg, PaperLightSurface, PaperLightGold, PaperLightOutline)
        }
        AppThemeMode.WATER -> {
            if (darkTheme) listOf(WaterDarkBg, WaterDarkSurface, WaterDarkBlue, WaterDarkOutline)
            else listOf(WaterLightBg, WaterLightSurface, WaterLightBlue, WaterLightOutline)
        }
        AppThemeMode.GRUVBOX_DARK -> {
            listOf(GruvboxDarkBg, GruvboxDarkSurface, GruvboxDarkGreen, GruvboxDarkOutline)
        }
    }
}

@Composable
fun BlueBridgeTheme(
    themeMode: AppThemeMode = AppThemeMode.GRUVBOX_DARK,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when (themeMode) {
        AppThemeMode.DYNAMIC_MATERIAL -> createAdaptedDynamicColorScheme(context, darkTheme)
        AppThemeMode.GRUVBOX_DARK -> GruvboxDarkColorScheme
        AppThemeMode.MOSS -> if (darkTheme) MossDarkColorScheme else MossLightColorScheme
        AppThemeMode.PAPER -> if (darkTheme) PaperDarkColorScheme else PaperLightColorScheme
        AppThemeMode.WATER -> if (darkTheme) WaterDarkColorScheme else WaterLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
