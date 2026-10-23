package id.generus.app.ui.screens.capaian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.UserError
import id.generus.app.core.dateRange
import id.generus.app.core.formatPosition
import id.generus.app.core.formatRange
import id.generus.app.core.kitabStatus
import id.generus.app.core.label
import id.generus.app.core.parsePosition
import id.generus.app.core.positionHint
import id.generus.app.core.today
import id.generus.app.data.AchievementChange
import id.generus.app.data.model.AssessmentMode
import id.generus.app.data.model.CapaianStatus
import id.generus.app.data.model.GenerusCapaianRow
import id.generus.app.data.model.GenerusDetail
import id.generus.app.data.model.KitabUnit
import id.generus.app.data.model.Period
import id.generus.app.data.model.Target
import id.generus.app.data.model.canWriteGenerus
import id.generus.app.data.model.jenjangLabel
import id.generus.app.ui.LoadVm
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.LoadingBox
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.RefreshableList
import id.generus.app.ui.components.SecondaryButton
import id.generus.app.ui.components.SectionCard
import id.generus.app.ui.components.SelectField
import id.generus.app.ui.components.StatusBadge
import id.generus.app.ui.components.TextInput
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.theme.StatusColor
import id.generus.app.ui.theme.of
import id.generus.app.ui.vm
import kotlinx.coroutines.flow.MutableStateFlow

data class GenerusCapaianItem(val target: Target, val row: GenerusCapaianRow)

data class GenerusCapaianData(val g: GenerusDetail, val periods: List<Period>, val period: Period?, val items: List<GenerusCapaianItem>)

class GenerusCapaianVm(c: AppContainer, private val generusId: String, periodId: String?) : LoadVm<GenerusCapaianData>(c) {
    val periodId = MutableStateFlow(periodId)

    init { load() }

    override suspend fun fetch(): GenerusCapaianData {
        val g = c.generus.detail(generusId) ?: throw UserError("Generus tidak ditemukan atau di luar lingkup Anda.")
        val periods = c.kurikulum.periods().filter { it.publishedAt != null }
        val period = periods.firstOrNull { it.id == periodId.value } ?: id.generus.app.data.pickActivePeriod(periods, today())
        if (period == null) return GenerusCapaianData(g, periods, null, emptyList())
        val targets = c.kurikulum.targets(period.id, g.jenjangId).associateBy { it.id }
        val rows = c.capaian.generusCapaian(generusId, period.id).rows
        val items = rows.mapNotNull { r -> targets[r.targetId]?.let { GenerusCapaianItem(it, r) } }
            .sortedWith(compareBy({ id.generus.app.core.ASPEK_ORDER.indexOf(it.target.aspek) }, { it.target.sortOrder }))
        return GenerusCapaianData(g, periods, period, items)
    }

    fun saveStatus(targetId: String, status: CapaianStatus, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) {
        c.capaian.saveAchievements(listOf(AchievementChange(targetId, generusId, status)))
    }

    fun saveKitab(kitabId: String, position: Int, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) {
        c.capaian.saveKitabProgress(kitabId, listOf(generusId), position, today())
    }
}

/** Capaian satu generus pada satu periode (padanan /capaian/generus/[id]). */
@Composable
fun CapaianGenerusScreen(nav: NavHostController, generusId: String, periodId: String?) {
    val me = LocalMe.current
    val toast = LocalToast.current
    val writer = me.canWriteGenerus
    val model = vm(key = "cap-gen-$generusId") { GenerusCapaianVm(it, generusId, periodId) }
    val state by model.state.collectAsStateWithLifecycle()
    val d = state.data

    AppScreen(
        d?.let { "Capaian ${it.g.name}" } ?: "Capaian generus",
        onBack = { nav.popBackStack() },
        subtitle = d?.let { x -> "${jenjangLabel(x.g.jenjang, x.g.kategori)} · Kelompok ${x.g.kelompok}" + (x.period?.let { " · ${dateRange(it.startsOn, it.endsOn)}" } ?: "") },
    ) { p ->
        RefreshableList(refreshing = state.loading && d != null, onRefresh = model::load, padding = p) {
            when {
                d == null && state.error != null -> item { ErrorBox(state.error!!, model::load) }
                d == null -> item { LoadingBox() }
                else -> {
                    if (d.periods.size > 1 && d.period != null) item {
                        SelectField("Periode", d.periods, d.period, { "Periode ${it.name}" }, { model.periodId.value = it.id; model.load() })
                    }
                    if (d.period == null) item { EmptyState("Belum ada periode target yang terbit") }
                    else if (d.items.isEmpty()) item { EmptyState("Tidak ada target untuk generus ini di periode ini") }
                    val kitab = d.items.filter { it.target.kitab != null }
                    val perGenerus = d.items.filter { it.target.materi?.assessment == AssessmentMode.generus }
                    val perKelompok = d.items.filter { it.target.materi?.assessment == AssessmentMode.kelompok }
                    if (kitab.isNotEmpty()) item {
                        SectionCard("Bacaan & makna", subtitle = "Posisi terakhir dibandingkan rentang target periode.") {
                            kitab.forEachIndexed { i, it ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                KitabItem(it, writer) { pos -> model.saveKitab(it.target.kitab!!.id, pos, { e -> toast(e) }) { toast("Posisi dicatat.") } }
                            }
                        }
                    }
                    if (perGenerus.isNotEmpty()) item {
                        SectionCard("Materi per generus") {
                            perGenerus.forEachIndexed { i, it ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ItemLabel(it.target)
                                    if (writer) {
                                        StatusChips(it.row.status, { s -> model.saveStatus(it.target.id, s, { e -> toast(e) }) { toast("Capaian disimpan.") } }, it.target.title())
                                    } else {
                                        StatusBadge(it.row.status.label, GenerusTheme.extra.of(it.row.status))
                                    }
                                }
                            }
                        }
                    }
                    if (perKelompok.isNotEmpty()) item {
                        SectionCard("Materi per kelompok", subtitle = "Ditandai oleh admin kelompok di halaman Capaian.") {
                            perKelompok.forEach {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { ItemLabel(it.target) }
                                    StatusBadge(
                                        if (it.row.deliveredOn != null) "Disampaikan" else "Belum",
                                        if (it.row.deliveredOn != null) GenerusTheme.extra.hadir else StatusColor(MaterialTheme.colorScheme.onSurfaceVariant, GenerusTheme.extra.tile),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemLabel(t: Target) {
    Text(t.materi?.title ?: t.title(), style = MaterialTheme.typography.bodyLarge)
    Text("${t.aspek.label} · ${t.mapel}${if (t.optional) " · pilihan" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun KitabItem(item: GenerusCapaianItem, writer: Boolean, onSave: (Int) -> Unit) {
    val k = item.target.kitab!!
    val s = kitabStatus(item.row.position, k.from, k.to)
    var input by remember { mutableStateOf("") }
    val parsed = if (input.isNotBlank()) parsePosition(k.unit, input, k.total) else null
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(k.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            StatusBadge(s.label, GenerusTheme.extra.of(s))
        }
        Text(
            "Target ${formatRange(k.unit, k.from, k.to)} · Terakhir: ${item.row.position?.let { formatPosition(k.unit, it) } ?: "belum dicatat"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (writer) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(
                    "Posisi hari ini",
                    input,
                    { input = it },
                    modifier = Modifier.weight(1f),
                    keyboardType = if (k.unit == KitabUnit.ayat) KeyboardType.Text else KeyboardType.Number,
                    supporting = if (input.isBlank()) positionHint(k.unit, k.total) else parsed?.let { "✓ ${formatPosition(k.unit, it)}" },
                    error = if (input.isNotBlank() && parsed == null) "Tidak dikenali" else null,
                )
                SecondaryButton("Catat", onClick = { parsed?.let { onSave(it); input = "" } }, enabled = parsed != null, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
