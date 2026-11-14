package com.wanglemai.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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

@Immutable
data class EditorialPalette(
    val isDark: Boolean,
    val bg: Color,
    val bgDeep: Color,
    val surface: Color,
    val surfaceWarm: Color,
    val soft: Color,
    val softHover: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val primary: Color,
    val primaryDeep: Color,
    val primarySoft: Color,
    val onPrimary: Color,
    val inkIsland: Color,
    val inkIslandText: Color,
    val line: Color,
    val lineStrong: Color,
    val marginLine: Color,
    val danger: Color,
    val dangerSoft: Color
)

val LightEditorialPalette = EditorialPalette(
    isDark = false,
    bg = Color(0xFFF3EDE2),
    bgDeep = Color(0xFFE7DDD0),
    surface = Color(0xFFFFFDF9),
    surfaceWarm = Color(0xFFFAF5EC),
    soft = Color(0xFFEBE2D4),
    softHover = Color(0xFFE2D6C5),
    text = Color(0xFF2A231D),
    muted = Color(0xFF756757),
    faint = Color(0xFFA69683),
    primary = Color(0xFF9A4B26),
    primaryDeep = Color(0xFF743416),
    primarySoft = Color(0x1F9A4B26),
    onPrimary = Color(0xFFFFFAF4),
    inkIsland = Color(0xFF28211B),
    inkIslandText = Color(0xFFFBF6EE),
    line = Color(0xFFE5DBCC),
    lineStrong = Color(0xFFD2C4B0),
    marginLine = Color(0x389A4B26),
    danger = Color(0xFFA6382E),
    dangerSoft = Color(0xFFF9E5E1)
)

val DarkEditorialPalette = EditorialPalette(
    isDark = true,
    bg = Color(0xFF1E1B18),
    bgDeep = Color(0xFF171412),
    surface = Color(0xFF2A2520),
    surfaceWarm = Color(0xFF312B25),
    soft = Color(0xFF38312A),
    softHover = Color(0xFF443B33),
    text = Color(0xFFF3E9DC),
    muted = Color(0xFFB7A693),
    faint = Color(0xFF7D6F5F),
    primary = Color(0xFFE5B285),
    primaryDeep = Color(0xFFF2C69E),
    primarySoft = Color(0x2EE5B285),
    onPrimary = Color(0xFF2B1F16),
    inkIsland = Color(0xFFE5B285),
    inkIslandText = Color(0xFF261C14),
    line = Color(0xFF423930),
    lineStrong = Color(0xFF564A3E),
    marginLine = Color(0x3DE5B285),
    danger = Color(0xFFFFB4AB),
    dangerSoft = Color(0xFF5C231C)
)

val LocalEditorialPalette = staticCompositionLocalOf { LightEditorialPalette }

val EditorialSerif = FontFamily.Serif
val EditorialMono = FontFamily.Monospace

// Editorial Paper Craft palette synced with prototype/warm/styles.css
private val Light = lightColorScheme(
    primary = Color(0xFF9A4B26), onPrimary = Color(0xFFFFFAF4),
    secondary = Color(0xFF9A4B26), onSecondary = Color(0xFFFFFAF4),
    tertiary = Color(0xFF9A4B26), onTertiary = Color(0xFFFFFAF4),
    background = Color(0xFFF3EDE2), onBackground = Color(0xFF2A231D),
    surface = Color(0xFFFFFDF9), onSurface = Color(0xFF2A231D),
    surfaceVariant = Color(0xFFEBE2D4), onSurfaceVariant = Color(0xFF756757),
    primaryContainer = Color(0xFFEBE2D4), onPrimaryContainer = Color(0xFF2A231D),
    secondaryContainer = Color(0xFFEBE2D4), onSecondaryContainer = Color(0xFF2A231D),
    tertiaryContainer = Color(0xFFEBE2D4), onTertiaryContainer = Color(0xFF2A231D),
    outline = Color(0xFFA69683), outlineVariant = Color(0xFFE5DBCC),
    error = Color(0xFFA6382E), onError = Color(0xFFFFFAF4),
    errorContainer = Color(0xFFF9E5E1), onErrorContainer = Color(0xFF6E2418),
    inverseSurface = Color(0xFF28211B), inverseOnSurface = Color(0xFFFBF6EE),
    inversePrimary = Color(0xFFE5B285), surfaceTint = Color(0xFF9A4B26),
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = Color(0xFFFAF5EC),
    surfaceContainer = Color(0xFFF3EDE2),
    surfaceContainerHigh = Color(0xFFEBE2D4),
    surfaceContainerHighest = Color(0xFFE7DDD0),
    surfaceDim = Color(0xFFE5DBCC), surfaceBright = Color(0xFFFFFDF9),
    scrim = Color(0xFF241B14)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE5B285), onPrimary = Color(0xFF2B1F16),
    secondary = Color(0xFFE5B285), onSecondary = Color(0xFF2B1F16),
    tertiary = Color(0xFFE5B285), onTertiary = Color(0xFF2B1F16),
    background = Color(0xFF1E1B18), onBackground = Color(0xFFF3E9DC),
    surface = Color(0xFF2A2520), onSurface = Color(0xFFF3E9DC),
    surfaceVariant = Color(0xFF38312A), onSurfaceVariant = Color(0xFFB7A693),
    primaryContainer = Color(0xFF38312A), onPrimaryContainer = Color(0xFFF3E9DC),
    secondaryContainer = Color(0xFF38312A), onSecondaryContainer = Color(0xFFF3E9DC),
    tertiaryContainer = Color(0xFF38312A), onTertiaryContainer = Color(0xFFF3E9DC),
    outline = Color(0xFF7D6F5F), outlineVariant = Color(0xFF423930),
    error = Color(0xFFFFB4AB), onError = Color(0xFF5C1A10),
    errorContainer = Color(0xFF5C231C), onErrorContainer = Color(0xFFFFDAD3),
    inverseSurface = Color(0xFFF3E9DC), inverseOnSurface = Color(0xFF2A2520),
    inversePrimary = Color(0xFF9A4B26), surfaceTint = Color(0xFFE5B285),
    surfaceContainerLowest = Color(0xFF171412),
    surfaceContainerLow = Color(0xFF2A2520),
    surfaceContainer = Color(0xFF312B25),
    surfaceContainerHigh = Color(0xFF38312A),
    surfaceContainerHighest = Color(0xFF443B33),
    surfaceDim = Color(0xFF171412), surfaceBright = Color(0xFF38312A),
    scrim = Color(0xFF000000)
)

private val WarmTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = EditorialSerif,
        fontSize = 23.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold
    ),
    titleLarge = TextStyle(
        fontFamily = EditorialSerif,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold
    ),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

private val WarmShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
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
    val palette = if (dark) DarkEditorialPalette else LightEditorialPalette
    CompositionLocalProvider(LocalEditorialPalette provides palette) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = WarmTypography,
            shapes = WarmShapes,
            content = content
        )
    }
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
