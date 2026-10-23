package id.generus.app.ui.screens.capaian

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.dateRange
import id.generus.app.core.formatShort
import id.generus.app.core.label
import id.generus.app.core.percent
import id.generus.app.core.today
import id.generus.app.data.model.AssessmentMode
import id.generus.app.data.model.Delivery
import id.generus.app.data.model.Jenjang
import id.generus.app.data.model.Period
import id.generus.app.data.model.ScopeUnit
import id.generus.app.data.model.Target
import id.generus.app.data.model.TargetCount
import id.generus.app.data.model.canWriteGenerus
import id.generus.app.data.model.rekapRootUnit
import id.generus.app.data.pickActivePeriod
import id.generus.app.ui.LoadVm
import id.generus.app.ui.OnResult
import id.generus.app.ui.components.AppCard
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.BottomSheet
import id.generus.app.ui.components.ChipRow
import id.generus.app.ui.components.DateField
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.GhostButton
import id.generus.app.ui.components.LoadingBox
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.RefreshableList
import id.generus.app.ui.components.SecondaryButton
import id.generus.app.ui.components.SelectField
import id.generus.app.ui.components.StatusBadge
import id.generus.app.ui.components.TextInput
import id.generus.app.ui.nav.CapaianKitab
import id.generus.app.ui.nav.CapaianTarget
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.nav.RekapCapaian
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.vm
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow

data class CapaianSelection(val periodId: String? = null, val kelompokId: String? = null, val jenjangId: String? = null)

data class CapaianHomeData(
    val periods: List<Period>,
    val kelompok: List<ScopeUnit>,
    val jenjang: List<Jenjang>,
    val period: Period?,
    val selectedKelompok: ScopeUnit?,
    val selectedJenjang: Jenjang?,
    val targets: List<Target>,
    val counts: Map<String, TargetCount>,
    val deliveries: Map<String, Delivery>,
)

class CapaianHomeVm(c: AppContainer, private val writer: Boolean) : LoadVm<CapaianHomeData>(c) {
    val selection = MutableStateFlow(CapaianSelection())
    private var base: Triple<List<Period>, List<ScopeUnit>, List<Jenjang>>? = null

    init { load() }

    override suspend fun fetch(): CapaianHomeData {
        val (periods, units, jenjang) = base ?: Triple(
            c.kurikulum.periods().filter { it.publishedAt != null },
            c.generus.scopeUnits(if (writer) "write" else "read").filter { it.level == id.generus.app.data.model.UnitLevel.kelompok },
            c.agenda.jenjang(),
        ).also { base = it }
        val sel = selection.value
        val period = periods.firstOrNull { it.id == sel.periodId } ?: pickActivePeriod(periods, today())
        val kelompok = units.firstOrNull { it.id == sel.kelompokId } ?: units.firstOrNull()
        val current = jenjang.firstOrNull { it.id == sel.jenjangId } ?: jenjang.firstOrNull { it.name == "Remaja" } ?: jenjang.firstOrNull()
        if (period == null || kelompok == null || current == null) {
            return CapaianHomeData(periods, units, jenjang, period, kelompok, current, emptyList(), emptyMap(), emptyMap())
        }
        val targets = c.kurikulum.targets(period.id, current.id)
        val summary = if (targets.isEmpty()) null else c.capaian.kelompokSummary(period.id, current.id, kelompok.id)
        return CapaianHomeData(
            periods, units, jenjang, period, kelompok, current, targets,
            summary?.counts.orEmpty().associateBy { it.id },
            summary?.deliveries.orEmpty().associateBy { it.targetId },
        )
    }

    fun select(s: CapaianSelection) {
        selection.value = s
        load()
    }

    fun setDelivery(targetId: String, kelompokId: String, delivered: Boolean, date: LocalDate, note: String?, onError: (String) -> Unit, onOk: () -> Unit) =
        run(onError, { load(); onOk() }) {
            if (date > today()) throw id.generus.app.core.UserError("Tanggal tidak boleh di masa depan.")
            c.capaian.setDelivery(targetId, kelompokId, delivered, date, note)
        }
}

/** Capaian per kelompok (padanan /capaian): ringkasan, target per aspek → mapel, tanda penyampaian. */
@Composable
fun CapaianHomeScreen(nav: NavHostController) {
    val me = LocalMe.current
    val toast = LocalToast.current
    val writer = me.canWriteGenerus
    val model = vm { CapaianHomeVm(it, writer) }
    val state by model.state.collectAsStateWithLifecycle()
    var deliverFor by remember { mutableStateOf<Target?>(null) }
    OnResult(nav) { model.load() }
    val d = state.data

    AppScreen(
        "Capaian",
        subtitle = d?.period?.let { p -> "Periode ${p.name} · ${dateRange(p.startsOn, p.endsOn)}" },
        actions = {
            if (me.rekapRootUnit() != null) {
                IconButton(onClick = { nav.navigate(RekapCapaian(periodId = d?.period?.id, jenjangId = d?.selectedJenjang?.id)) }) {
                    Icon(Icons.Rounded.Insights, contentDescription = "Rekap lintas kelompok")
                }
            }
        },
    ) { p ->
        RefreshableList(refreshing = state.loading && d != null, onRefresh = model::load, padding = p) {
            when {
                d == null && state.error != null -> item { ErrorBox(state.error!!, model::load) }
                d == null -> item { LoadingBox() }
                d.period == null -> item { EmptyState("Belum ada periode target yang terbit", body = "Capaian bisa diisi setelah Tim Kurikulum menerbitkan target periode.") }
                d.selectedKelompok == null -> item { EmptyState("Tidak ada kelompok dalam lingkup Anda") }
                else -> {
                    item { Selectors(d, model) }
                    if (d.targets.isEmpty()) {
                        item { EmptyState("Belum ada target ${d.selectedJenjang?.name ?: ""} di periode ini") }
                    } else {
                        item { Summary(d) }
                        groupTargets(d.targets).forEach { g ->
                            item(key = g.aspek.name) {
                                AppCard(Modifier.fillMaxWidth()) {
                                    Text(g.aspek.label, style = MaterialTheme.typography.titleMedium)
                                    g.mapel.forEach { mp ->
                                        Text(mp.mapel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                        mp.targets.forEach { t ->
                                            TargetLine(t, d.counts[t.id], d.deliveries[t.id], writer,
                                                onOpen = {
                                                    val k = d.selectedKelompok.id
                                                    nav.navigate(if (t.kitab != null) CapaianKitab(t.id, k) else CapaianTarget(t.id, k))
                                                },
                                                onDeliver = { deliverFor = t },
                                                onUndeliver = { model.setDelivery(t.id, d.selectedKelompok.id, false, today(), null, { toast(it) }) { toast("Tanda dibatalkan.") } },
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
    }

    deliverFor?.let { t ->
        DeliverySheet(t, onDismiss = { deliverFor = null }) { date, note ->
            val k = d?.selectedKelompok?.id ?: return@DeliverySheet
            model.setDelivery(t.id, k, true, date, note, { toast(it) }) { deliverFor = null; toast("Ditandai sudah disampaikan.") }
        }
    }
}

@Composable
private fun Selectors(d: CapaianHomeData, model: CapaianHomeVm) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (d.periods.size > 1) {
            SelectField("Periode", d.periods, d.period, { "Periode ${it.name}" }, { model.select(model.selection.value.copy(periodId = it.id)) })
        }
        if (d.kelompok.size > 1) {
            SelectField("Kelompok", d.kelompok, d.selectedKelompok, { "Kelompok ${it.name}" }, { model.select(model.selection.value.copy(kelompokId = it.id)) }, group = { "Desa ${it.desaName}" })
        }
        if (d.selectedJenjang != null) {
            ChipRow(d.jenjang, d.selectedJenjang, { it.name }, { model.select(model.selection.value.copy(jenjangId = it.id)) })
        }
    }
}

@Composable
private fun Summary(d: CapaianHomeData) {
    val counted = d.targets.filter { !it.optional && (it.kitab != null || it.materi?.assessment == AssessmentMode.generus) }
    val tuntas = counted.sumOf { d.counts[it.id]?.tuntas ?: 0 }
    val proses = counted.sumOf { d.counts[it.id]?.proses ?: 0 }
    val slots = counted.sumOf { d.counts[it.id]?.total ?: 0 }
    val perKelompok = d.targets.filter { !it.optional && it.materi?.assessment == AssessmentMode.kelompok }
    val delivered = perKelompok.count { d.deliveries[it.id] != null }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigStat("Per generus", "${percent(tuntas, slots)}%", "$tuntas dari $slots tuntas", Modifier.weight(1f)) { StackedProgress(tuntas, proses, slots) }
        BigStat("Per kelompok", "$delivered/${perKelompok.size}", "Materi disampaikan", Modifier.weight(1f)) { SimpleBar(delivered, perKelompok.size) }
    }
}

@Composable
private fun TargetLine(t: Target, count: TargetCount?, delivery: Delivery?, writer: Boolean, onOpen: () -> Unit, onDeliver: () -> Unit, onUndeliver: () -> Unit) {
    val tags = t.tags()
    if (t.materi?.assessment == AssessmentMode.kelompok) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t.title(), style = MaterialTheme.typography.bodyLarge)
            if (tags.isNotEmpty()) Text(tags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (delivery != null) {
                    StatusBadge("✓ Disampaikan ${delivery.deliveredOn.formatShort()}", GenerusTheme.extra.hadir)
                    if (writer) GhostButton("Batalkan", onClick = onUndeliver)
                } else if (writer) {
                    SecondaryButton("Tandai disampaikan", onClick = onDeliver, icon = Icons.Rounded.Check)
                } else {
                    Text("Belum disampaikan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t.title() + (t.range()?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyLarge)
            if (tags.isNotEmpty()) Text(tags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StackedProgress(count?.tuntas ?: 0, count?.proses ?: 0, count?.total ?: 0)
        }
        Column(Modifier.width(56.dp), horizontalAlignment = Alignment.End) {
            Text("${count?.tuntas ?: 0}/${count?.total ?: 0}", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End)
            Text("tuntas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeliverySheet(t: Target, onDismiss: () -> Unit, onSave: (LocalDate, String?) -> Unit) {
    var date by remember { mutableStateOf<LocalDate?>(today()) }
    var note by remember { mutableStateOf("") }
    BottomSheet("Tandai sudah disampaikan", onDismiss) {
        Text(t.title(), style = MaterialTheme.typography.bodyMedium)
        DateField("Tanggal disampaikan", date, { date = it })
        TextInput("Catatan (opsional)", note, { note = it })
        PrimaryButton("Simpan", onClick = { date?.let { onSave(it, note.trim().ifEmpty { null }) } }, modifier = Modifier.fillMaxWidth())
    }
}
