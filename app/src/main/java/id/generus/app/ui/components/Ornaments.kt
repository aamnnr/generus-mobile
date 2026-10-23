package id.generus.app.ui.components

import androidx.compose.foundation.Canvas
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

/** Siluet masjid garis sederhana (viewBox 200×120) untuk watermark kartu hero, login, empty state. */
@Composable
fun MosqueSilhouette(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 200f, size.height / 120f, pivot = Offset.Zero) {
            drawPath(MOSQUE_SILHOUETTE, color)
        }
    }
}

/** Bintang delapan (rub el hizb) bergaris. */
@Composable
fun EightStar(color: Color, size: Dp = 28.dp, modifier: Modifier = Modifier) {
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
