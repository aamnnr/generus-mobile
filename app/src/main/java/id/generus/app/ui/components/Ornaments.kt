package id.generus.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import id.generus.app.core.Brand
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.theme.Kliik
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Ornamen dekoratif (DESIGN.md §5). Tidak dibaca pembaca layar.

private val MOSQUE_SILHOUETTE: Path = PathParser().parsePathString(
    "M20 120V52l6-14 6 14v68zM168 120V52l6-14 6 14v68zM48 120V82h104v38zM100 20c-25 0-42 21-42 46v16h84V66c0-25-17-46-42-46zM98 4h4v14h-4z",
).toPath()

private val CHECK_ORNAMENT: Path = PathParser().parsePathString("M48 64l34 32 72-76").toPath()

/**
 * Watermark kartu hero, login, empty state (viewBox 200×120): siluet masjid (desain lama),
 * atau centang besar bergaris — motif dari ikon KLIIK JM.
 */
@Composable
fun MosqueSilhouette(color: Color, modifier: Modifier = Modifier) {
    if (Brand.isKliik) {
        Canvas(modifier) {
            scale(size.width / 200f, size.height / 120f, pivot = Offset.Zero) {
                drawPath(CHECK_ORNAMENT, color, style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        return
    }
    Canvas(modifier) {
        scale(size.width / 200f, size.height / 120f, pivot = Offset.Zero) {
            drawPath(MOSQUE_SILHOUETTE, color)
        }
    }
}

private val CHECK_BADGE: Path = PathParser().parsePathString("m7.8 12.3 2.8 2.7 5.6-6").toPath()

/** Ikon kartu info: bintang delapan (rub el hizb) bergaris, atau lencana centang (KLIIK JM). */
@Composable
fun EightStar(color: Color, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    if (Brand.isKliik) {
        Canvas(modifier.size(size)) {
            scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
                val line = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawCircle(color, radius = 9.5f, center = Offset(12f, 12f), style = line)
                drawPath(CHECK_BADGE, color, style = line)
            }
        }
        return
    }
    Box(modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val c = this.size.width / 2f
            val r = c * 0.9f
            val inner = r * sqrt(0.5f) / cos(PI.toFloat() / 8f)
            val path = Path()
            for (i in 0 until 16) {
                val a = i * PI.toFloat() / 8f - PI.toFloat() / 2f
                val d = if (i % 2 == 0) r else inner
                val x = c + d * cos(a)
                val y = c + d * sin(a)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color, style = Stroke(width = this.size.width * 0.07f))
        }
    }
}

/**
 * Garis miring tipis (motif kaki huruf K logo KLIIK JM), arah 122° seperti CSS
 * `repeating-linear-gradient(122deg, …)`: tebal `line`, berulang tiap `period`.
 */
fun DrawScope.diagonalStripes(color: Color, line: Float, period: Float) {
    val reach = size.width + size.height
    rotate(32f, pivot = center) {
        var x = center.x - reach
        while (x < center.x + reach) {
            drawRect(color, topLeft = Offset(x, center.y - reach), size = Size(line, reach * 2))
            x += period
        }
    }
}

/**
 * Latar kartu hero: KLIIK JM = navy + cahaya cyan di pojok kanan atas + garis miring
 * (.brand-kliik .bg-hero); desain lama = gradasi hijau tua.
 */
@Composable
fun Modifier.heroBackground(shape: Shape = RoundedCornerShape(20.dp)): Modifier {
    val x = GenerusTheme.extra
    if (!Brand.isKliik) return clip(shape).background(Brush.linearGradient(listOf(x.hero, x.heroDeep)))
    return clip(shape).background(x.hero).drawBehind {
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFF008ED8).copy(alpha = 0.55f), Color.Transparent),
                center = Offset(size.width, 0f),
                radius = maxOf(size.width * 1.2f, size.height * 0.95f) * 0.58f,
            ),
        )
        diagonalStripes(Color.White.copy(alpha = 0.055f), 2.dp.toPx(), 16.dp.toPx())
    }
}

/** Lingkaran bergaris miring di pojok kanan atas kartu (.kliik-motif); tidak ada pada desain lama. */
fun Modifier.kliikMotif(corner: Dp = 16.dp): Modifier {
    if (!Brand.isKliik) return this
    return drawBehind {
        val d = 72.dp.toPx()
        val off = 17.6.dp.toPx()
        val topLeft = Offset(size.width + off - d, -off)
        val card = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner.toPx()))) }
        val circle = Path().apply { addOval(Rect(topLeft, Size(d, d))) }
        clipPath(card) {
            clipPath(circle) {
                diagonalStripes(Kliik.Cyan.copy(alpha = 0.18f), 3.dp.toPx() / 1.6f, 9.dp.toPx() / 1.6f)
            }
        }
    }
}

/** Aksen garis navy–cyan di atas judul (.kliik-accent); tidak ada pada desain lama. */
@Composable
fun KliikAccent(modifier: Modifier = Modifier) {
    if (!Brand.isKliik) return
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier.padding(bottom = 6.dp).size(36.dp, 4.dp)) {
        val r = CornerRadius(size.height / 2)
        clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }) {
            drawRect(primary, size = Size(size.width * 0.58f, size.height))
            drawRect(Kliik.Cyan, topLeft = Offset(size.width * 0.58f, 0f), size = Size(size.width * 0.42f, size.height))
        }
    }
}
