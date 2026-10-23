package id.generus.app.ui.screens.capaian

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.UserError
import id.generus.app.core.dateRange
import id.generus.app.core.formatId
import id.generus.app.core.label
import id.generus.app.core.percent
import id.generus.app.core.today
import id.generus.app.data.model.AssessmentMode
import id.generus.app.data.model.Jenjang
import id.generus.app.data.model.Period
import id.generus.app.data.model.RekapTarget
import id.generus.app.data.model.RekapUnit
import id.generus.app.data.model.Target
import id.generus.app.data.model.UnitLevel
import id.generus.app.data.model.UnitNode
import id.generus.app.data.model.canReadGenerus
import id.generus.app.data.model.label
import id.generus.app.data.model.rekapRootUnit
import id.generus.app.data.pickActivePeriod
import id.generus.app.ui.LoadVm
import id.generus.app.ui.components.AppCard
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.ChipRow
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.LoadingBox
import id.generus.app.ui.components.RefreshableList
import id.generus.app.ui.components.SelectField
import id.generus.app.ui.components.StatusBadge
import id.generus.app.ui.nav.CapaianHome
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.nav.RekapCapaian
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.vm

data class RekapData(
    val periods: List<Period>,
    val period: Period?,
    val jenjang: List<Jenjang>,
    /** null = semua jenjang. */
    val jenjangId: String?,
    val unit: UnitNode,
    val ancestors: List<UnitNode>,
    val children: List<Pair<UnitNode, RekapUnit>>,
    val total: RekapUnit?,
    val targets: List<Target>,
    val perTarget: Map<String, RekapTarget>,
)

class RekapVm(c: AppContainer, private val rootUnitId: String, private val unitId: String?, private val periodId: String?, private val jenjangParam: String?) : LoadVm<RekapData>(c) {
    init { load() }

    override suspend fun fetch(): RekapData {
        val periods = c.kurikulum.periods().filter { it.publishedAt != null }
        val jenjang = c.agenda.jenjang()
        val tree = c.capaian.unitWithChildren(unitId ?: rootUnitId)
        val unit = tree.firstOrNull { it.rel == "self" } ?: throw UserError("Unit tidak ditemukan.")
        val ancestors = tree.filter { it.rel == "anc" }
        val children = tree.filter { it.rel == "child" }
        val period = periods.firstOrNull { it.id == periodId } ?: pickActivePeriod(periods, today())
        val jenjangId = if (jenjangParam == "semua") null else (jenjang.firstOrNull { it.id == jenjangParam } ?: jenjang.firstOrNull { it.name == "Remaja" } ?: jenjang.firstOrNull())?.id
        if (period == null) return RekapData(periods, null, jenjang, jenjangId, unit, ancestors, emptyList(), null, emptyList(), emptyMap())
        val rows = c.capaian.rekapUnits(period.id, jenjangId, listOf(unit.id) + children.map { it.id })
        val targets = jenjangId?.let { c.kurikulum.targets(period.id, it) }.orEmpty()
        val perTarget = jenjangId?.let { c.capaian.rekapTargets(period.id, it, unit.id) }.orEmpty()
        return RekapData(
            periods, period, jenjang, jenjangId, unit, ancestors,
            children.mapNotNull { ch -> rows.firstOrNull { it.unitId == ch.id }?.let { ch to it } },
            rows.firstOrNull { it.unitId == unit.id },
            targets, perTarget,
        )
    }
}

/** "Desa Cibeureum"; nama yang sudah diawali tingkatnya tidak diulang. */
private fun unitLabel(u: UnitNode): String = if (u.name.lowercase().startsWith(u.level.label.lowercase())) u.name else "${u.level.label} ${u.name}"

/** Rekap capaian lintas unit (padanan /capaian/rekap): agregat, juga untuk Tim Kurikulum. */
@Composable
fun RekapCapaianScreen(nav: NavHostController, unitId: String?, periodId: String?, jenjangId: String?) {
    val me = LocalMe.current
    val root = me.rekapRootUnit() ?: me.roles.firstOrNull()
    if (root == null) {
        AppScreen("Rekap capaian", onBack = { nav.popBackStack() }) { p -> EmptyState("Belum punya peran", Modifier.padding(p)) }
        return
    }
    val model = vm(key = "rekap-$unitId-$periodId-$jenjangId") { RekapVm(it, root.unitId, unitId, periodId, jenjangId) }
    val state by model.state.collectAsStateWithLifecycle()
    val d = state.data
    val reader = me.canReadGenerus
    fun go(unit: String? = d?.unit?.id, period: String? = d?.period?.id, jenjang: String? = d?.jenjangId ?: "semua") =
        nav.navigate(RekapCapaian(unit, period, jenjang)) { launchSingleTop = true }

    AppScreen(
        "Rekap capaian",
        onBack = if (nav.previousBackStackEntry != null) ({ nav.popBackStack() }) else null,
        subtitle = d?.period?.let { "Periode ${it.name} · ${dateRange(it.startsOn, it.endsOn)}" },
    ) { p ->
        RefreshableList(refreshing = state.loading && d != null, onRefresh = model::load, padding = p) {
            when {
                d == null && state.error != null -> item { ErrorBox(state.error!!, model::load) }
                d == null -> item { LoadingBox() }
                d.period == null -> item { EmptyState("Belum ada periode target yang terbit") }
                else -> {
                    item {
                        // Jalur wilayah (breadcrumb).
                        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            (d.ancestors + d.unit).forEachIndexed { i, u ->
                                if (i > 0) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                val last = u.id == d.unit.id
                                Text(
                                    unitLabel(u),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                    modifier = if (last) Modifier else Modifier.clickable(role = Role.Button) { go(unit = u.id) },
                                )
                            }
                        }
                    }
                    if (d.periods.size > 1) item {
                        SelectField("Periode", d.periods, d.period, { "Periode ${it.name}" }, { go(period = it.id) })
                    }
                    item {
                        val options = listOf<Jenjang?>(null) + d.jenjang
                        ChipRow(options, d.jenjang.firstOrNull { it.id == d.jenjangId }, { it?.name ?: "Semua" }, { go(jenjang = it?.id ?: "semua") })
                    }
                    val total = d.total
                    if (total == null) {
                        item { EmptyState("Belum ada data rekap untuk unit ini") }
                    } else {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                BigStat("Per generus", "${percent(total.tuntas, total.slots)}%", "${total.tuntas.formatId()} dari ${total.slots.formatId()} tuntas", Modifier.weight(1f)) {
                                    StackedProgress(total.tuntas, total.proses, total.slots)
                                }
                                BigStat("Disampaikan", "${percent(total.delivered, total.deliverable)}%", "${total.delivered} dari ${total.deliverable}", Modifier.weight(1f)) {
                                    SimpleBar(total.delivered, total.deliverable)
                                }
                            }
                        }
                        item { BigStat("Generus sasaran", total.generus.formatId(), "${total.kelompok} kelompok", Modifier.fillMaxWidth()) }
                    }
                    if (d.children.isNotEmpty()) item {
                        val pcts = d.children.filter { it.second.slots > 0 }.map { percent(it.second.tuntas, it.second.slots) }
                        val min = pcts.minOrNull()
                        // Tandai "terendah" hanya bila satu unit tertinggal sendiri (bukan seri).
                        val lowest = if (pcts.size > 1 && pcts.count { it == min } == 1) min else null
                        val childLabel = if (d.unit.level == UnitLevel.daerah) "Desa" else "Kelompok"
                        AppCard(Modifier.fillMaxWidth()) {
                            Text("Per ${childLabel.lowercase()}", style = MaterialTheme.typography.titleMedium)
                            Text("Hijau = tuntas, kuning = proses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            d.children.forEachIndexed { i, (u, r) ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                val pct = percent(r.tuntas, r.slots)
                                val open: (() -> Unit)? = when {
                                    u.level != UnitLevel.kelompok -> ({ go(unit = u.id) })
                                    reader -> ({ nav.navigate(CapaianHome) })
                                    else -> null
                                }
                                Row(
                                    Modifier.fillMaxWidth().then(if (open != null) Modifier.clickable(role = Role.Button, onClick = open) else Modifier).padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("$childLabel ${u.name}", style = MaterialTheme.typography.titleSmall)
                                            if (lowest != null && r.slots > 0 && pct == lowest) StatusBadge("Terendah", GenerusTheme.extra.alpa)
                                        }
                                        StackedProgress(r.tuntas, r.proses, r.slots)
                                        Text("${r.generus} generus · disampaikan ${r.delivered}/${r.deliverable}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("$pct%", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
                                    if (open != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (d.jenjangId != null && d.targets.isNotEmpty()) item {
                        AppCard(Modifier.fillMaxWidth()) {
                            Text("Per target", style = MaterialTheme.typography.titleMedium)
                            Text("Seluruh ${unitLabel(d.unit)}. Materi per kelompok dihitung dari jumlah kelompok yang sudah menyampaikan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            groupTargets(d.targets).forEach { g ->
                                Text(g.aspek.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                                g.mapel.flatMap { it.targets }.forEach { t ->
                                    val r = d.perTarget[t.id] ?: return@forEach
                                    val perKelompok = t.materi?.assessment == AssessmentMode.kelompok
                                    val pct = if (perKelompok) percent(r.delivered, r.kelompok) else percent(r.tuntas, r.slots)
                                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                "${t.kitab?.title ?: t.materi?.title} · ${t.mapel}" + (t.range()?.let { " · $it" } ?: "") + if (t.optional) " · pilihan" else "",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            if (perKelompok) SimpleBar(r.delivered, r.kelompok) else StackedProgress(r.tuntas, r.proses, r.slots)
                                        }
                                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(84.dp)) {
                                            Text(if (t.optional) "–" else "$pct%", style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                if (perKelompok) "${r.delivered}/${r.kelompok} kelompok" else "${r.tuntas}/${r.slots} tuntas",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.End,
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
}
