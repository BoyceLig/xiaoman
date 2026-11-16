package dev.xiaoman.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* 小满的品牌色：冷白纸感底 + 墨蓝主色 + 青蓝点缀，一个强调色贯穿全局。 */
private val Navy = Color(0xFF1E3A5F)
private val NavyDeep = Color(0xFF122A47)
private val Mist = Color(0xFFE4EDF7)
private val Paper = Color(0xFFF4F7FB)
private val Cyan = Color(0xFF0E7490)
private val CyanPanel = Color(0xFFDDF1F6)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Mist,
    onPrimaryContainer = NavyDeep,
    inversePrimary = Color(0xFF8FB8E0),
    secondary = Color(0xFF4A5D75),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF2F7),
    onSecondaryContainer = Color(0xFF24313F),
    tertiary = Cyan,
    onTertiary = Color.White,
    tertiaryContainer = CyanPanel,
    onTertiaryContainer = Color(0xFF05485A),
    background = Paper,
    onBackground = Color(0xFF16202B),
    surface = Color.White,
    onSurface = Color(0xFF16202B),
    surfaceVariant = Color(0xFFEEF2F7),
    onSurfaceVariant = Color(0xFF5E6B7A),
    surfaceTint = Navy,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F6FA),
    surfaceContainer = Color(0xFFEEF2F7),
    surfaceContainerHigh = Color(0xFFE7EDF4),
    surfaceContainerHighest = Color(0xFFDEE6EF),
    outline = Color(0xFF94A1B0),
    outlineVariant = Color(0xFFDCE4EE),
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E5),
    onErrorContainer = Color(0xFF7A150E),
    inverseSurface = Color(0xFF1F2A36),
    inverseOnSurface = Color(0xFFF0F4F9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB8E0),
    onPrimary = Color(0xFF0A2138),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFCFE2F5),
    inversePrimary = Navy,
    secondary = Color(0xFFA9BACC),
    onSecondary = Color(0xFF14202C),
    secondaryContainer = Color(0xFF1F2A36),
    onSecondaryContainer = Color(0xFFCBD8E5),
    tertiary = Color(0xFF67C8DE),
    onTertiary = Color(0xFF04323D),
    tertiaryContainer = Color(0xFF0B4655),
    onTertiaryContainer = Color(0xFFB8E9F5),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF151B23),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF1D2530),
    onSurfaceVariant = Color(0xFFA9B6C5),
    surfaceTint = Color(0xFF8FB8E0),
    surfaceContainerLowest = Color(0xFF090D12),
    surfaceContainerLow = Color(0xFF121821),
    surfaceContainer = Color(0xFF171E27),
    surfaceContainerHigh = Color(0xFF1D2530),
    surfaceContainerHighest = Color(0xFF253040),
    outline = Color(0xFF7D8B9B),
    outlineVariant = Color(0xFF2A3543),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE6EDF5),
    inverseOnSurface = Color(0xFF1F2A36),
)

private val XiaomanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

private val XiaomanTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

/** Stable Xiaoman colors; readable light/dark surfaces independent of the phone wallpaper. */
@Composable
fun XiaomanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = XiaomanTypography,
        shapes = XiaomanShapes,
        content = content,
    )
}
