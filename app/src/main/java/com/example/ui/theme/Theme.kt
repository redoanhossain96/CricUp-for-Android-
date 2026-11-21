package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

val LocalThemeIsDark = compositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(false) // Default to Modern Golden-Green Light Theme
}

@Composable
fun adaptiveGoldColor(): Color =
    if (LocalThemeIsDark.current.value) GoldYellow else LightGoldAccent

@Composable
fun adaptiveEmeraldColor(): Color =
    if (LocalThemeIsDark.current.value) EmeraldNeon else LightEmeraldText

private val ModernLightColorScheme = lightColorScheme(
    primary = LightEmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F4E0),
    onPrimaryContainer = Color(0xFF00471F),
    secondary = LightGoldAccent,
    onSecondary = Color.White,
    secondaryContainer = LightGoldSoftBg,
    onSecondaryContainer = Color(0xFF4F3800),
    tertiary = GoldDark,
    onTertiary = Color.Black,
    background = LightBg,
    onBackground = LightTextNavy,
    surface = LightSurface,
    onSurface = LightTextNavy,
    surfaceVariant = Color(0xFFE6F3EB),
    onSurfaceVariant = LightTextSubtle,
    outline = LightCardBorder,
    outlineVariant = Color(0xFFC2DCCB),
    error = WicketRed,
    onError = Color.White
)

private val BroadcastDarkColorScheme = darkColorScheme(
    primary = EmeraldNeon,
    onPrimary = Color.Black,
    primaryContainer = EmeraldDark,
    onPrimaryContainer = Color.White,
    secondary = GoldYellow,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF382C05),
    onSecondaryContainer = GoldLight,
    tertiary = GoldLight,
    onTertiary = Color.Black,
    background = DeepNavyBg,
    onBackground = Color.White,
    surface = SurfaceDarkNavy,
    onSurface = Color.White,
    surfaceVariant = SlateCardBg,
    onSurfaceVariant = DarkTextLightGrey,
    outline = CardBorder,
    outlineVariant = Color(0xFF2C6B4E),
    error = WicketRed,
    onError = Color.White
)

@Composable
fun CricUpTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BroadcastDarkColorScheme else ModernLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun CSMBimphaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    CricUpTheme(darkTheme = darkTheme, content = content)
}
