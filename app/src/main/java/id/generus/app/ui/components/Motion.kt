package id.generus.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// Gerak (DESIGN.md §9): 150–220 ms, kurva lembut. Mengikuti "skala durasi animasi"
// Android, sehingga ikut mati bila pengguna mematikan animasi.

/** Kurva yang sama dengan PWA: cubic-bezier(0.2, 0.7, 0.2, 1). */
val EaseOutSoft = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

const val DUR_ENTER = 220
const val DUR_EXIT = 160
const val DUR_COLOR = 180

/**
 * Konten baru muncul: fade + naik 6 dp (padanan `.page-body > *` dan `.animate-enter`).
 * Hanya sekali per item — kartu yang digulir keluar lalu masuk lagi tidak dianimasikan ulang.
 */
fun Modifier.appear(delayMs: Int = 0): Modifier = composed {
    var shown by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (shown) 1f else 0f) }
    val rise = with(LocalDensity.current) { 6.dp.toPx() }
    LaunchedEffect(Unit) {
        if (!shown) {
            progress.animateTo(1f, tween(DUR_ENTER, delayMs, EaseOutSoft))
            shown = true
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

/** Efek tekan: skala 0,98 (DESIGN.md §9). */
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(120), label = "press")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Kilau bergerak untuk kerangka pemuatan. */
fun Modifier.shimmer(): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-x",
    )
    drawWithCache {
        val w = size.width
        val brush = Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(x * w - w * 0.4f, 0f),
            end = Offset(x * w + w * 0.4f, size.height),
        )
        onDrawBehind { drawRect(brush) }
    }
}
