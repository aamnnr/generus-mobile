package id.generus.app.ui.screens.capaian

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.generus.app.core.formatRange
import id.generus.app.core.label
import id.generus.app.data.model.Aspek
import id.generus.app.data.model.CapaianStatus
import id.generus.app.data.model.Gender
import id.generus.app.data.model.Target
import id.generus.app.ui.components.AppCard
import id.generus.app.ui.components.Overline
import id.generus.app.ui.components.ProgressBar
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.theme.of

// Komponen bersama layar capaian & kurikulum.

data class MapelGroup(val mapel: String, val strategy: String?, val targets: List<Target>)
data class AspekGroup(val aspek: Aspek, val mapel: List<MapelGroup>)

/** Kelompokkan target berurutan menjadi blok aspek → mapel seperti tabel dokumen PPG. */
fun groupTargets(rows: List<Target>): List<AspekGroup> =
    rows.groupBy { it.aspek }.map { (aspek, list) ->
        AspekGroup(aspek, list.groupBy { it.mapel }.map { (mapel, ts) -> MapelGroup(mapel, ts.firstNotNullOfOrNull { it.strategy }, ts) })
    }

/** Judul target: materi, atau kitab + catatan. */
fun Target.title(): String = kitab?.let { k -> k.title + (note?.let { " · $it" } ?: "") } ?: materi?.title ?: mapel

fun Target.range(): String? = kitab?.let { formatRange(it.unit, it.from, it.to) }

/** Keterangan sasaran khusus: Putra/Putri, kategori, pilihan. */
fun Target.tags(): String = listOfNotNull(
    when (gender) { Gender.L -> "Putra"; Gender.P -> "Putri"; null -> null },
    kategoriName,
    if (optional) "Pilihan" else null,
).joinToString(" · ")

/** Bilah progres bertumpuk: hijau = tuntas, kuning = proses. */
@Composable
fun StackedProgress(done: Int, proses: Int, total: Int, modifier: Modifier = Modifier) {
    val x = GenerusTheme.extra
    val d = if (total > 0) done.toFloat() / total else 0f
    val p = if (total > 0) proses.toFloat() / total else 0f
    Row(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (d > 0f) Box(Modifier.weight(d).height(6.dp).background(x.hadir.solid))
        if (p > 0f) Box(Modifier.weight(p).height(6.dp).background(x.terlambat.solid))
        if (1f - d - p > 0.0001f) Box(Modifier.weight(1f - d - p).height(6.dp))
    }
}

/** Pilihan belum / proses / tuntas (pil 36 dp). */
@Composable
fun StatusChips(value: CapaianStatus, onChange: (CapaianStatus) -> Unit, name: String, enabled: Boolean = true) {
    val x = GenerusTheme.extra
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CapaianStatus.entries.forEach { s ->
            val active = value == s
            val c = x.of(s)
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(CircleShape)
                    .background(if (active) c.solid else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (active) c.solid else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(enabled = enabled && !active, role = Role.RadioButton) { onChange(s) }
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = "${s.label} untuk $name" },
                contentAlignment = Alignment.Center,
            ) {
                Text(s.label, style = MaterialTheme.typography.labelMedium, color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Kotak statistik besar dengan bilah di bawahnya. */
@Composable
fun BigStat(label: String, value: String, sub: String, modifier: Modifier = Modifier, bar: @Composable () -> Unit = {}) {
    AppCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Overline(label)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            bar()
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SimpleBar(done: Int, total: Int) = ProgressBar(if (total > 0) done.toFloat() / total else 0f, height = 6.dp, color = GenerusTheme.extra.hadir.solid, track = MaterialTheme.colorScheme.surfaceVariant)
