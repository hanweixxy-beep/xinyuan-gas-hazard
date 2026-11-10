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

// 云端版视觉规范。保留旧变量名作为兼容别名，让现有业务表单同步换肤。
val InkPrimary = Color(0xFF27231F)
val InkSecondary = Color(0xFF6B655F)
val InkTertiary = Color(0xFF9B948C)
val BrandGreen = Color(0xFFD97757)
val BrandGreenStrong = Color(0xFFB65D42)
val BluePrimary = Color(0xFF5D6F87)
val BlueLight = Color(0xFFEEF1F4)
val OrangeAccent = Color(0xFFA66B2E)
val OrangeSoft = Color(0xFFF7EDDF)
val GreenSuccess = Color(0xFF3F6B55)
val RedDanger = Color(0xFFA4473C)
val GrayBackground = Color(0xFFFAF9F5)
val GrayCard = Color(0xFFFFFFFF)
val GraySurface = Color(0xFFF5F2EA)
val GraySeparator = Color(0xFFDED8CE)
val BrandGreenSoft = GrayBackground
val RedSoft = GrayBackground
val GraySecondaryText = InkSecondary
val GrayTertiaryText = InkTertiary

private val LightColorScheme = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandGreenSoft,
    onPrimaryContainer = BrandGreenStrong,
    secondary = GreenSuccess,
    secondaryContainer = GrayBackground,
    onSecondaryContainer = InkPrimary,
    background = GrayBackground,
    surface = Color.White,
    surfaceVariant = GraySurface,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = GrayCard,
    surfaceContainerLow = GrayBackground,
    surfaceContainer = GrayBackground,
    surfaceContainerHigh = GrayBackground,
    surfaceContainerHighest = GraySurface,
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
            window.statusBarColor = GrayBackground.toArgb()
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
