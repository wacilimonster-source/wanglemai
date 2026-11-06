package com.wanglemai.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.wanglemai.app.AppContainer
import com.wanglemai.app.domain.PREF_THEME
import com.wanglemai.app.domain.ThemeMode
import com.wanglemai.app.domain.themeModeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Light = lightColorScheme(
    primary = Color(0xFF215B45), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EEE7), onPrimaryContainer = Color(0xFF123523),
    secondary = Color(0xFF215B45), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EEE7), onSecondaryContainer = Color(0xFF123523),
    background = Color(0xFFF5F4EE), surface = Color.White, onSurface = Color(0xFF202D28),
    surfaceVariant = Color(0xFFEDEFE8), onSurfaceVariant = Color(0xFF4A5A51),
    outline = Color(0xFFDCE2D9), error = Color(0xFFA32929)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA5D7B9), onPrimary = Color(0xFF123523),
    primaryContainer = Color(0xFF2C3D30), onPrimaryContainer = Color(0xFFE7EEE8),
    secondary = Color(0xFFA5D7B9), onSecondary = Color(0xFF123523),
    secondaryContainer = Color(0xFF2C3D30), onSecondaryContainer = Color(0xFFE7EEE8),
    background = Color(0xFF141B17), surface = Color(0xFF202B24), onSurface = Color(0xFFE7EEE8),
    surfaceVariant = Color(0xFF2C3D30), onSurfaceVariant = Color(0xFFB2C0B7),
    outline = Color(0xFF3B493F), error = Color(0xFFFFB4AB)
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
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
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
