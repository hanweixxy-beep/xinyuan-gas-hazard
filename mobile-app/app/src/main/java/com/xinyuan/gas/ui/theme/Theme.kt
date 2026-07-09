package com.xinyuan.gas.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// 品牌色
val BluePrimary = Color(0xFF007AFF)
val BlueLight = Color(0xFFE3F2FD)
val OrangeAccent = Color(0xFFFF9500)
val GreenSuccess = Color(0xFF34C759)
val RedDanger = Color(0xFFFF3B30)
val GrayBackground = Color(0xFFF2F2F7)
val GrayCard = Color(0xFFFFFFFF)
val GraySeparator = Color(0xFFC6C6C8)
val GraySecondaryText = Color(0xFF8E8E93)
val GrayTertiaryText = Color(0xFFAEAEB2)

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = BlueLight,
    secondary = OrangeAccent,
    background = GrayBackground,
    surface = Color.White,
    surfaceVariant = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = GraySecondaryText,
    error = RedDanger,
    outline = GraySeparator
)

val IOSTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.37.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = GraySecondaryText),
)

@Composable
fun GasHazardTheme(content: @Composable () -> Unit) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = GrayBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = IOSTypography,
        content = content
    )
}
