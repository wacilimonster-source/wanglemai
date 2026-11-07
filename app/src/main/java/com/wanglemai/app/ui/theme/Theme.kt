package com.wanglemai.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.wanglemai.app.AppContainer
import com.wanglemai.app.domain.PREF_THEME
import com.wanglemai.app.domain.ThemeMode
import com.wanglemai.app.domain.themeModeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Warm-beige palette per docs/visual-refresh/development.md §1:
// cream background, warm-paper surfaces, caramel accents. Secondary/tertiary roles map to
// primary so default components never pick up a cool tone; container roles map to surfaceVariant
// with onSurface text, and surfaceContainer* is explicit so tonalElevation stays warm.
private val Light = lightColorScheme(
    primary = Color(0xFF8A5335), onPrimary = Color(0xFFFFFAF2),
    secondary = Color(0xFF8A5335), onSecondary = Color(0xFFFFFAF2),
    tertiary = Color(0xFF8A5335), onTertiary = Color(0xFFFFFAF2),
    background = Color(0xFFF6F1E8), onBackground = Color(0xFF383129),
    surface = Color(0xFFFFFBF4), onSurface = Color(0xFF383129),
    surfaceVariant = Color(0xFFEDE4D7), onSurfaceVariant = Color(0xFF786B5D),
    primaryContainer = Color(0xFFEDE4D7), onPrimaryContainer = Color(0xFF383129),
    secondaryContainer = Color(0xFFEDE4D7), onSecondaryContainer = Color(0xFF383129),
    tertiaryContainer = Color(0xFFEDE4D7), onTertiaryContainer = Color(0xFF383129),
    outline = Color(0xFFA08F7C), outlineVariant = Color(0xFFE3D8C8),
    error = Color(0xFFA33E32), onError = Color(0xFFFFFAF2),
    errorContainer = Color(0xFFF7DFD8), onErrorContainer = Color(0xFF6E2418),
    inverseSurface = Color(0xFF383129), inverseOnSurface = Color(0xFFFFFBF4),
    inversePrimary = Color(0xFFE5B68E), surfaceTint = Color(0xFF8A5335),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFFCF6EB),
    surfaceContainer = Color(0xFFF8F0E3),
    surfaceContainerHigh = Color(0xFFF2E9DB),
    surfaceContainerHighest = Color(0xFFEDE4D7),
    surfaceDim = Color(0xFFE5DDCE), surfaceBright = Color(0xFFFFFBF4),
    scrim = Color(0xFF383129)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE5B68E), onPrimary = Color(0xFF34251B),
    secondary = Color(0xFFE5B68E), onSecondary = Color(0xFF34251B),
    tertiary = Color(0xFFE5B68E), onTertiary = Color(0xFF34251B),
    background = Color(0xFF26221E), onBackground = Color(0xFFF4EADC),
    surface = Color(0xFF332D26), onSurface = Color(0xFFF4EADC),
    surfaceVariant = Color(0xFF43392F), onSurfaceVariant = Color(0xFFC0AF9B),
    primaryContainer = Color(0xFF43392F), onPrimaryContainer = Color(0xFFF4EADC),
    secondaryContainer = Color(0xFF43392F), onSecondaryContainer = Color(0xFFF4EADC),
    tertiaryContainer = Color(0xFF43392F), onTertiaryContainer = Color(0xFFF4EADC),
    outline = Color(0xFF8A7B69), outlineVariant = Color(0xFF514438),
    error = Color(0xFFFFB4A8), onError = Color(0xFF5C1A10),
    errorContainer = Color(0xFF6E2B20), onErrorContainer = Color(0xFFFFDAD3),
    inverseSurface = Color(0xFFF4EADC), inverseOnSurface = Color(0xFF332D26),
    inversePrimary = Color(0xFF8A5335), surfaceTint = Color(0xFFE5B68E),
    surfaceContainerLowest = Color(0xFF211D19),
    surfaceContainerLow = Color(0xFF2D2823),
    surfaceContainer = Color(0xFF332D26),
    surfaceContainerHigh = Color(0xFF3B342C),
    surfaceContainerHighest = Color(0xFF43392F),
    surfaceDim = Color(0xFF1D1A16), surfaceBright = Color(0xFF3B342C),
    scrim = Color(0xFF000000)
)

// Type scale from development.md §字体与尺寸; system font only (weights/sizes per table).
private val WarmTypography = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp)
)

// Corner radii from development.md §字体与尺寸: segmented 11, search 14, buttons 15,
// list group 20, editor sheet top 28. Widget 24dp lives in the background drawables.
private val WarmShapes = Shapes(
    extraSmall = RoundedCornerShape(11.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun WanglemaiTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    syncSystemBars: Boolean = true,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    if (syncSystemBars) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = WarmTypography,
        shapes = WarmShapes,
        content = content
    )
}

@Composable
fun rememberAppThemeMode(container: AppContainer): ThemeMode {
    val mode by produceState(initialValue = ThemeMode.SYSTEM, container) {
        value = withContext(Dispatchers.IO) {
            runCatching { themeModeOf(container.repository.getPreference(PREF_THEME)) }
                .getOrDefault(ThemeMode.SYSTEM)
        }
    }
    return mode
}
