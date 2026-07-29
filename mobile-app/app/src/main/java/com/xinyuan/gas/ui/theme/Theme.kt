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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// 2026 企业视觉规范。保留旧变量名作为兼容别名，避免业务页面重复定义颜色。
val InkPrimary = Color(0xFF111418)
val InkSecondary = Color(0xFF5F6670)
val InkTertiary = Color(0xFF8B929C)
val BrandGreen = Color(0xFF17805D)
val BrandGreenStrong = Color(0xFF116B4E)
val BrandGreenSoft = Color(0xFFEAF6F1)
val BluePrimary = Color(0xFF0A6FE8)
val BlueLight = Color(0xFFEAF3FF)
val OrangeAccent = Color(0xFFA85B00)
val OrangeSoft = Color(0xFFFFF4E4)
val GreenSuccess = BrandGreen
val RedDanger = Color(0xFFC73D36)
val RedSoft = Color(0xFFFDEEEE)
val GrayBackground = Color(0xFFF5F6F8)
val GrayCard = Color(0xFFFFFFFF)
val GraySurface = Color(0xFFEEF0F3)
val GraySeparator = Color(0xFFDDE1E6)
val GraySecondaryText = InkSecondary
val GrayTertiaryText = InkTertiary

private val LightColorScheme = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandGreenSoft,
    onPrimaryContainer = BrandGreenStrong,
    secondary = OrangeAccent,
    background = GrayBackground,
    surface = Color.White,
    surfaceVariant = GraySurface,
    onBackground = InkPrimary,
    onSurface = InkPrimary,
    onSurfaceVariant = GraySecondaryText,
    error = RedDanger,
    outline = GraySeparator
)

val IOSTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 28.sp,
        lineHeight = 35.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 22.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        color = GraySecondaryText
    ),
)

@Composable
fun GasHazardTheme(content: @Composable () -> Unit) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.White.toArgb()
            window.navigationBarColor = Color.White.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = IOSTypography,
        content = content
    )
}
