package com.lukeneedham.patchwork

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.lukeneedham.common.ui.DebugTheme

/** The app's colours and typeface, from the palette in `MainActivity.kt`. */
@Immutable
data class AppThemeValues(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val primary: Color,
    val onPrimary: Color,
    val outline: Color,
    val error: Color,
    val warning: Color,
    val fontFamily: FontFamily,
)

private val LocalAppTheme = staticCompositionLocalOf<AppThemeValues> { error("No AppTheme provided") }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppTheme provides AppThemeValues(
            background = Dark900,
            onBackground = White,
            surface = Surface,
            onSurface = White,
            primary = Pink500,
            onPrimary = White,
            outline = Subtle,
            error = Color(0xFFFF6B6B),
            warning = Color(0xFFFECE00),
            fontFamily = FontFamily.Default,
        ),
        content = content,
    )
}

object AppTheme {
    val current: AppThemeValues @Composable get() = LocalAppTheme.current
}

/** How the shared library's pages (debug, logs, crashes) look, so they match the app. */
fun AppThemeValues.toDebugTheme() = DebugTheme(
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    primary = primary,
    onPrimary = onPrimary,
    outline = outline,
    error = error,
    warning = warning,
    fontFamily = fontFamily,
)
