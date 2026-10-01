package com.bblvn.composebaseproject.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Dark-only Material color scheme. It exists so that library composables
 * (ripple, text selection, Coil crossfade surfaces, the ads module's Material
 * widgets) inherit AI Photo colors. App code should read [AppColors] directly.
 */
private val AiPhotoColorScheme = darkColorScheme(
    primary = AppColors.accentViolet,
    onPrimary = AppColors.textPrimary,
    primaryContainer = AppColors.accentViolet,
    onPrimaryContainer = AppColors.textPrimary,
    secondary = AppColors.accentMagenta,
    onSecondary = AppColors.textPrimary,
    tertiary = AppColors.accentCyan,
    onTertiary = AppColors.textOnAccent,
    background = AppColors.bgRoot,
    onBackground = AppColors.textPrimary,
    surface = AppColors.bgSurface,
    onSurface = AppColors.textPrimary,
    surfaceVariant = AppColors.bgElevated,
    onSurfaceVariant = AppColors.textSecondary,
    outline = AppColors.strokeStrong,
    outlineVariant = AppColors.strokeSoft,
    error = AppColors.stateDanger,
    onError = AppColors.textPrimary,
    scrim = AppColors.bgScrim,
)

/**
 * R3 item 8 — the app is dark-theme only, so status/nav bar icons are forced
 * LIGHT regardless of the system theme, and navigation-bar contrast
 * enforcement is turned off (otherwise Android paints its own translucent
 * scrim over our bottom nav bar).
 *
 * This is safe to call from inside the theme; it runs on every recomposition
 * via SideEffect and is a no-op in @Preview.
 */
@Composable
fun ApplyDarkSystemBars() {
    val view = LocalView.current
    if (LocalInspectionMode.current) return
    val activity = view.context as? Activity ?: return
    SideEffect {
        val window = activity.window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
}

/**
 * The AI Photo theme. Dark only — there is no `darkTheme` parameter and no
 * dynamic color, by design.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    ApplyDarkSystemBars()
    MaterialTheme(
        colorScheme = AiPhotoColorScheme,
        typography = AppMaterialTypography,
        content = content,
    )
}

/**
 * Kept because `BaseActivity` (BaseProject file, outside this phase's scope)
 * already calls it. Parameters are accepted and ignored: AI Photo is dark-only.
 */
@Composable
fun ComposeBaseProjectTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    AppTheme(content = content)
}
