package app.unlimitedmessaging.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.unlimitedmessaging.android.R

/*
 * The Unlimited Messaging identity, the same as the website and dashboard
 * (apps/web/public/styles/global.css and tailwind.config.js in the product
 * repository): off-white page, white surfaces with a faint teal border, deep
 * green actions, ink text. Flat and sharp: 8dp corners, no shadows, no pills.
 */
object UmColors {
    /** Primary actions (dashboard `--purple1`). */
    val Green = Color(0xFF075E54)
    val GreenPressed = Color(0xFF064F47)

    /** The "M" of the logo, links and positive states. */
    val Teal = Color(0xFF128C7E)

    /** Bright accent (dashboard `--purple2`): online, delivered. */
    val Bright = Color(0xFF25D366)

    /** Tinted surfaces: highlighted rows, tonal buttons (tailwind `mist`). */
    val Mist = Color(0xFFF0F7F3)
    val MistStrong = Color(0xFFE8F5F3)

    /** Page background, the website's off-white. */
    val OffWhite = Color(0xFFF8FAFB)
    val White = Color(0xFFFFFFFF)

    val Ink = Color(0xFF0D1F16)
    val InkSoft = Color(0xFF2A3D31)
    val Muted = Color(0xFF637066)

    /** `rgba(18, 140, 126, 0.15)` over white: every card and divider border. */
    val Border = Color(0xFFDBEEEC)
    val BorderSubtle = Color(0xFFEDF6F5)

    val Error = Color(0xFFB91C1C)
    val ErrorSurface = Color(0xFFFDECEC)

    // Dark mode, as the dashboard's `.dark` theme.
    val DarkPage = Color(0xFF09090B)
    val DarkSurface = Color(0xFF0F0F12)
    val DarkElevated = Color(0xFF1A1A1F)
    val DarkBorder = Color(0xFF252529)
    val DarkText = Color(0xFFFFFFFF)
    val DarkTextSecondary = Color(0xFFD1D5DB)
    val DarkMuted = Color(0xFF9CA3AF)
}

private val LightScheme = lightColorScheme(
    primary = UmColors.Green,
    onPrimary = UmColors.White,
    primaryContainer = UmColors.MistStrong,
    onPrimaryContainer = UmColors.Green,
    secondary = UmColors.Teal,
    onSecondary = UmColors.White,
    secondaryContainer = UmColors.Mist,
    onSecondaryContainer = UmColors.Green,
    tertiary = UmColors.Bright,
    onTertiary = UmColors.Ink,
    background = UmColors.OffWhite,
    onBackground = UmColors.Ink,
    surface = UmColors.White,
    onSurface = UmColors.Ink,
    surfaceVariant = UmColors.Mist,
    onSurfaceVariant = UmColors.Muted,
    surfaceTint = Color.Transparent,
    surfaceBright = UmColors.White,
    surfaceDim = UmColors.OffWhite,
    surfaceContainerLowest = UmColors.White,
    surfaceContainerLow = UmColors.White,
    surfaceContainer = UmColors.White,
    surfaceContainerHigh = UmColors.White,
    surfaceContainerHighest = UmColors.Mist,
    inverseSurface = UmColors.Ink,
    inverseOnSurface = UmColors.White,
    inversePrimary = UmColors.Bright,
    outline = UmColors.Border,
    outlineVariant = UmColors.BorderSubtle,
    error = UmColors.Error,
    onError = UmColors.White,
    errorContainer = UmColors.ErrorSurface,
    onErrorContainer = UmColors.Error,
    scrim = Color(0x4D000000),
)

private val DarkScheme = darkColorScheme(
    primary = UmColors.Bright,
    onPrimary = UmColors.Ink,
    primaryContainer = Color(0xFF0E2A22),
    onPrimaryContainer = UmColors.Bright,
    secondary = Color(0xFF3FBFAE),
    onSecondary = UmColors.Ink,
    secondaryContainer = UmColors.DarkElevated,
    onSecondaryContainer = UmColors.Bright,
    tertiary = UmColors.Bright,
    onTertiary = UmColors.Ink,
    background = UmColors.DarkPage,
    onBackground = UmColors.DarkText,
    surface = UmColors.DarkSurface,
    onSurface = UmColors.DarkText,
    surfaceVariant = UmColors.DarkElevated,
    onSurfaceVariant = UmColors.DarkMuted,
    surfaceTint = Color.Transparent,
    surfaceBright = UmColors.DarkElevated,
    surfaceDim = UmColors.DarkPage,
    surfaceContainerLowest = UmColors.DarkPage,
    surfaceContainerLow = UmColors.DarkSurface,
    surfaceContainer = UmColors.DarkSurface,
    surfaceContainerHigh = UmColors.DarkElevated,
    surfaceContainerHighest = UmColors.DarkElevated,
    inverseSurface = UmColors.DarkText,
    inverseOnSurface = UmColors.Ink,
    inversePrimary = UmColors.Green,
    outline = UmColors.DarkBorder,
    outlineVariant = UmColors.DarkElevated,
    error = Color(0xFFF87171),
    onError = UmColors.Ink,
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFCA5A5),
)

/** Colors outside the Material roles: status accents shared by every screen. */
@Immutable
data class UmExtraColors(
    val success: Color,
    val link: Color,
    val muted: Color,
    val textSecondary: Color,
)

private val LightExtras = UmExtraColors(
    success = UmColors.Teal,
    link = UmColors.Teal,
    muted = UmColors.Muted,
    textSecondary = UmColors.InkSoft,
)
private val DarkExtras = UmExtraColors(
    success = UmColors.Bright,
    link = Color(0xFF3FBFAE),
    muted = UmColors.DarkMuted,
    textSecondary = UmColors.DarkTextSecondary,
)

val LocalUmColors = staticCompositionLocalOf { LightExtras }

// The website's typefaces, bundled (OFL, see licenses/): Bricolage Grotesque
// for titles, DM Sans for everything else. Variable fonts, one file each.
@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int) = Font(
    R.font.bricolage_grotesque,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun dmSans(weight: Int) = Font(
    R.font.dm_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Bricolage = FontFamily(bricolage(600), bricolage(700), bricolage(800))
val DmSans = FontFamily(dmSans(400), dmSans(500), dmSans(600), dmSans(700))

private val UmTypography = Typography().run {
    fun TextStyle.display(weight: FontWeight, letterSpacing: Float) =
        copy(fontFamily = Bricolage, fontWeight = weight, letterSpacing = letterSpacing.sp)

    fun TextStyle.body(weight: FontWeight = FontWeight.Normal) = copy(fontFamily = DmSans, fontWeight = weight)

    Typography(
        displayLarge = displayLarge.display(FontWeight.ExtraBold, -1.2f),
        displayMedium = displayMedium.display(FontWeight.ExtraBold, -1f),
        displaySmall = displaySmall.display(FontWeight.ExtraBold, -0.8f),
        headlineLarge = headlineLarge.display(FontWeight.ExtraBold, -0.8f),
        headlineMedium = headlineMedium.display(FontWeight.ExtraBold, -0.6f),
        headlineSmall = headlineSmall.display(FontWeight.Bold, -0.4f),
        titleLarge = titleLarge.display(FontWeight.Bold, -0.3f),
        titleMedium = titleMedium.display(FontWeight.Bold, -0.1f).copy(fontSize = 17.sp),
        titleSmall = titleSmall.body(FontWeight.SemiBold),
        bodyLarge = bodyLarge.body().copy(letterSpacing = 0.sp),
        bodyMedium = bodyMedium.body().copy(letterSpacing = 0.sp),
        bodySmall = bodySmall.body().copy(letterSpacing = 0.sp),
        labelLarge = labelLarge.body(FontWeight.Bold).copy(letterSpacing = 0.sp, fontSize = 15.sp),
        labelMedium = labelMedium.body(FontWeight.SemiBold),
        labelSmall = labelSmall.body(FontWeight.Medium).copy(letterSpacing = 0.4.sp),
    )
}

/** 8dp everywhere controls are concerned (the dashboard's 0.5rem), 12dp for cards. */
val UmShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun GatewayTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    androidx.compose.runtime.CompositionLocalProvider(LocalUmColors provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(colorScheme = scheme, typography = UmTypography, shapes = UmShapes, content = content)
    }
}
