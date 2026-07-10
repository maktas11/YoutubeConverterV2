package com.maktas.ytconverter.ui.theme

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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.rememberDynamicColorScheme
import com.maktas.ytconverter.data.ColorPresets
import com.maktas.ytconverter.data.ColorThemeMode
import com.maktas.ytconverter.data.CustomColors

// Fallback scheme for DYNAMIC mode on pre-Android-12 devices, which can't derive
// colors from the wallpaper. Kept as the app's original hand-picked brand colors.
private val DarkColorScheme = darkColorScheme(
    primary = Red80,
    secondary = RedGrey80,
    tertiary = Orange80
)

private val LightColorScheme = lightColorScheme(
    primary = Red40,
    secondary = RedGrey40,
    tertiary = Orange40
)

@Composable
fun YoutubeConverterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    rawColors: Boolean = false,
    colorThemeMode: ColorThemeMode = ColorThemeMode.DYNAMIC,
    colorPresetId: String = ColorPresets.default.id,
    customColors: CustomColors = CustomColors(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (colorThemeMode) {
        ColorThemeMode.DYNAMIC -> dynamicOrFallbackColorScheme(darkTheme)

        ColorThemeMode.PRESET -> {
            val seed = Color(ColorPresets.byId(colorPresetId).seed)
            val base = rememberDynamicColorScheme(seedColor = seed, isDark = darkTheme)
            withVividSurfaces(
                base, seed, darkTheme, rawColors,
                backgroundOverride = null, surfaceVariantOverride = null,
            )
        }

        // Primary/secondary/tertiary/error still go through Material3's standard
        // tone-mapping (that's what keeps their "on" text/icon colors readable).
        // Background/surface are handled separately below — Material3 normally keeps
        // those nearly neutral (a faint hue at most), which read as "still just beige
        // or dark" — so here they use the picked/seed color directly instead, for an
        // actually vivid background rather than a barely-tinted one.
        ColorThemeMode.CUSTOM -> {
            val seed = Color(customColors.seed)
            val base = rememberDynamicColorScheme(
                seedColor = seed,
                isDark = darkTheme,
                primary = customColors.primary?.let { Color(it) },
                secondary = customColors.secondary?.let { Color(it) },
                tertiary = customColors.tertiary?.let { Color(it) },
                error = customColors.error?.let { Color(it) },
            )
            withVividSurfaces(
                base,
                seed,
                darkTheme,
                rawColors,
                backgroundOverride = customColors.neutral?.let { Color(it) },
                surfaceVariantOverride = customColors.neutralVariant?.let { Color(it) },
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
private fun dynamicOrFallbackColorScheme(darkTheme: Boolean): ColorScheme {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    return if (darkTheme) DarkColorScheme else LightColorScheme
}

/**
 * Replaces the whole background/surface family with visibly-tinted colors derived
 * from [seed] (or the literal [backgroundOverride]/[surfaceVariantOverride] when set),
 * instead of Material3's default near-neutral tones. Covers every surface token
 * Material3 components actually draw from (cards, nav bars, dialogs use the
 * surfaceContainer* tokens, not plain surface/background), so the vivid look is
 * consistent app-wide rather than just on the root background.
 */
private fun withVividSurfaces(
    base: ColorScheme,
    seed: Color,
    isDark: Boolean,
    rawColors: Boolean,
    backgroundOverride: Color?,
    surfaceVariantOverride: Color?,
): ColorScheme {
    val blendTarget = if (isDark) Color.Black else Color.White
    // rawColors (the "Disabled" theme option) means exactly the picked/seed color,
    // no blend toward white/black at all — not even a little.
    fun tint(fraction: Float) = if (rawColors) seed else lerp(seed, blendTarget, fraction)
    fun onColorFor(background: Color) = if (background.luminance() > 0.5f) Color.Black else Color.White

    val background = backgroundOverride ?: tint(0.72f)
    val surfaceVariant = surfaceVariantOverride ?: tint(0.60f)

    return base.copy(
        background = background,
        onBackground = onColorFor(background),
        surface = background,
        onSurface = onColorFor(background),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onColorFor(surfaceVariant),
        surfaceBright = tint(0.85f),
        surfaceDim = tint(0.55f),
        surfaceContainerLowest = tint(0.88f),
        surfaceContainerLow = tint(0.80f),
        surfaceContainer = tint(0.72f),
        surfaceContainerHigh = tint(0.64f),
        surfaceContainerHighest = tint(0.56f),
    )
}
