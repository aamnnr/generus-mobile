@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package id.generus.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.generus.app.R
import id.generus.app.data.model.AttendanceStatus
import id.generus.app.data.model.CapaianStatus

// Sistem desain Generus (docs/DESIGN.md di proyek PWA): hijau tua + krem,
// kartu lembut, status selalu teks + warna.

object Palette {
    val Green950 = Color(0xFF0E2A20)
    val Green900 = Color(0xFF133A2C)
    val Green800 = Color(0xFF1A4A39)
    val Green700 = Color(0xFF215943)
    val Green600 = Color(0xFF2D6E54)
    val Green500 = Color(0xFF4B8A6E)
    val Green200 = Color(0xFFC9DDD1)
    val Green100 = Color(0xFFE2EDE6)
    val Green50 = Color(0xFFEFF5F1)
    val Cream50 = Color(0xFFFBFAF6)
    val Cream100 = Color(0xFFF6F3EC)
    val Cream200 = Color(0xFFEEE9DE)
    val Cream300 = Color(0xFFE3DDCF)
    val Ink900 = Color(0xFF1C2620)
    val Ink600 = Color(0xFF56605A)
    val Brass500 = Color(0xFFB08A4E)
    val Brass700 = Color(0xFF8A6A35)
    val Brass100 = Color(0xFFF3EAD9)
}

/** Warna status presensi & capaian: warna penuh (teks putih lolos AA) + latar lembut badge. */
@Immutable
data class StatusColor(val solid: Color, val soft: Color)

@Immutable
data class Extra(
    val hero: Color,
    val heroForeground: Color,
    val tile: Color,
    val brass: Color,
    val brassText: Color,
    val brassSoft: Color,
    val track: Color,
    val bar: Color,
    val hadir: StatusColor,
    val terlambat: StatusColor,
    val izin: StatusColor,
    val sakit: StatusColor,
    val alpa: StatusColor,
)

private val LightExtra = Extra(
    hero = Palette.Green800,
    heroForeground = Color.White,
    tile = Palette.Cream200,
    brass = Palette.Brass500,
    brassText = Palette.Brass700,
    brassSoft = Palette.Brass100,
    track = Palette.Green200,
    bar = Palette.Green600,
    hadir = StatusColor(Color(0xFF2F7A4F), Color(0xFFE3F0E8)),
    terlambat = StatusColor(Color(0xFF9C620F), Color(0xFFF6EBD8)),
    izin = StatusColor(Color(0xFF356C8C), Color(0xFFE2ECF2)),
    sakit = StatusColor(Color(0xFF76558A), Color(0xFFEEE6F2)),
    alpa = StatusColor(Color(0xFFB2392F), Color(0xFFF6E2DF)),
)

private val DarkExtra = LightExtra.copy(
    tile = Color(0xFF202D26),
    brassText = Color(0xFFE8D3AE),
    brassSoft = Color(0xFF2B3A32),
    track = Color(0xFF2B3A32),
    bar = Color(0xFF7FC09E),
    hadir = StatusColor(Color(0xFF2F7A4F), Color(0xFF1F3A2B)),
    terlambat = StatusColor(Color(0xFF9C620F), Color(0xFF3A2E1A)),
    izin = StatusColor(Color(0xFF356C8C), Color(0xFF1C2E3A)),
    sakit = StatusColor(Color(0xFF76558A), Color(0xFF30263A)),
    alpa = StatusColor(Color(0xFFB2392F), Color(0xFF3E2220)),
)

private val LightColors = lightColorScheme(
    primary = Palette.Green700,
    onPrimary = Color.White,
    primaryContainer = Palette.Green100,
    onPrimaryContainer = Palette.Green800,
    secondary = Palette.Green600,
    onSecondary = Color.White,
    secondaryContainer = Palette.Green100,
    onSecondaryContainer = Palette.Green800,
    tertiary = Palette.Brass700,
    tertiaryContainer = Palette.Brass100,
    onTertiaryContainer = Palette.Brass700,
    background = Palette.Cream100,
    onBackground = Palette.Ink900,
    surface = Color.White,
    onSurface = Palette.Ink900,
    surfaceVariant = Palette.Cream200,
    onSurfaceVariant = Palette.Ink600,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Palette.Cream50,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Palette.Cream50,
    surfaceContainerHighest = Palette.Cream200,
    outline = Palette.Cream300,
    outlineVariant = Palette.Cream300,
    error = Color(0xFFB2392F),
    onError = Color.White,
    errorContainer = Color(0xFFF6E2DF),
    onErrorContainer = Color(0xFFB2392F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FC09E),
    onPrimary = Color(0xFF0F1713),
    primaryContainer = Color(0xFF202D26),
    onPrimaryContainer = Color(0xFFECEFE9),
    secondary = Color(0xFF7FC09E),
    secondaryContainer = Color(0xFF202D26),
    onSecondaryContainer = Color(0xFFECEFE9),
    tertiary = Color(0xFFE8D3AE),
    tertiaryContainer = Color(0xFF2B3A32),
    onTertiaryContainer = Color(0xFFE8D3AE),
    background = Color(0xFF0F1713),
    onBackground = Color(0xFFECEFE9),
    surface = Color(0xFF17221C),
    onSurface = Color(0xFFECEFE9),
    surfaceVariant = Color(0xFF202D26),
    onSurfaceVariant = Color(0xFFA3ADA6),
    surfaceContainerLowest = Color(0xFF17221C),
    surfaceContainerLow = Color(0xFF17221C),
    surfaceContainer = Color(0xFF17221C),
    surfaceContainerHigh = Color(0xFF202D26),
    surfaceContainerHighest = Color(0xFF202D26),
    outline = Color(0xFF2B3A32),
    outlineVariant = Color(0xFF2B3A32),
    error = Color(0xFFE07A70),
    onError = Color(0xFF0F1713),
    errorContainer = Color(0xFF3E2220),
    onErrorContainer = Color(0xFFE07A70),
)

private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Sans = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700))

/** Lora: hanya sapaan beranda, angka besar, judul login. */
val Display = FontFamily(
    Font(R.font.lora, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

/** Amiri untuk teks Arab (materi, doa, dalil). */
val Arabic = FontFamily(Font(R.font.amiri_regular, FontWeight.Normal), Font(R.font.amiri_bold, FontWeight.Bold))

private val Base = Typography()

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.66.sp),
    displayLarge = Base.displayLarge.copy(fontFamily = Display),
    displayMedium = Base.displayMedium.copy(fontFamily = Display),
)

/** Gaya teks Arab: ≥ 1,25× teks latin, kanan-ke-kiri. */
val ArabicText = TextStyle(fontFamily = Arabic, fontSize = 22.sp, lineHeight = 40.sp, textDirection = TextDirection.Rtl)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val LocalExtra = staticCompositionLocalOf { LightExtra }

object GenerusTheme {
    val extra: Extra @Composable get() = LocalExtra.current
}

fun Extra.of(status: AttendanceStatus): StatusColor = when (status) {
    AttendanceStatus.hadir -> hadir
    AttendanceStatus.terlambat -> terlambat
    AttendanceStatus.izin -> izin
    AttendanceStatus.sakit -> sakit
    AttendanceStatus.alpa -> alpa
}

fun Extra.of(status: CapaianStatus): StatusColor = when (status) {
    CapaianStatus.tuntas -> hadir
    CapaianStatus.proses -> terlambat
    CapaianStatus.belum -> StatusColor(Palette.Ink600, tile)
}

@Composable
fun GenerusTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtra provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
