package dev.jcode.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Roles the theme adds beyond Material's [androidx.compose.material3.ColorScheme].
 *
 * [brand] is the unmodified accent, used only for marks, carets and live pulses:
 * it fails contrast as body text. Everything else is derived from the scheme's
 * own roles so the extras stay in step when the palette changes.
 */
@Immutable
data class ExtendedColors(
    val brand: Color,
    val codeBackground: Color,
    val codeBorder: Color,
    val userBubble: Color,
    val userBubbleText: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val success: Color,
)

internal val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        brand = Color.Unspecified,
        codeBackground = Color.Unspecified,
        codeBorder = Color.Unspecified,
        userBubble = Color.Unspecified,
        userBubbleText = Color.Unspecified,
        cardSurface = Color.Unspecified,
        cardBorder = Color.Unspecified,
        success = Color.Unspecified,
    )
}

val MaterialTheme.extendedColors: ExtendedColors
    @Composable @ReadOnlyComposable get() = LocalExtendedColors.current

/**
 * The extras for one palette in one mode, taken from the scheme rather than
 * hard-coded per theme. A new theme gets correct code blocks, bubbles and cards
 * without listing them: a code block is the theme's own "highest container"
 * surface, which is the M3 role for exactly this.
 */
private fun extendedFor(family: ThemeFamily, dark: Boolean): ExtendedColors {
    val scheme = family.scheme(dark)
    return ExtendedColors(
        brand = family.brand(dark),
        codeBackground = scheme.surfaceContainerHighest,
        codeBorder = scheme.outlineVariant,
        userBubble = scheme.primaryContainer,
        userBubbleText = scheme.onPrimaryContainer,
        cardSurface = scheme.surfaceContainerLow,
        cardBorder = scheme.outlineVariant,
        success = family.success(dark),
    )
}

/**
 * [family] is the palette; [darkTheme] is the mode. They are independent on
 * purpose: a theme supplies both, so pinning a theme does not pin it to one mode.
 */
@Composable
fun JCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    family: ThemeFamily = ThemeFamily.Default,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalExtendedColors provides extendedFor(family, darkTheme)) {
        MaterialTheme(
            colorScheme = family.scheme(darkTheme),
            shapes = AppShapes,
            typography = JCodeTypography,
            content = content,
        )
    }
}