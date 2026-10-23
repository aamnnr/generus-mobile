package id.generus.app.ui.screens.laporan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.ID_LOCALE
import id.generus.app.core.Share
import id.generus.app.core.UserError
import id.generus.app.core.XlsxWriter
import id.generus.app.core.currentMonth
import id.generus.app.core.formatDateTime
import id.generus.app.core.formatId
import id.generus.app.core.formatShort
import id.generus.app.core.label
import id.generus.app.core.percent
import id.generus.app.core.range
import id.generus.app.core.recentMonths
import id.generus.app.core.today
import id.generus.app.data.TrenPoint
import id.generus.app.data.model.KehadiranUnit
import id.generus.app.data.model.KelompokRef
import id.generus.app.data.model.LaporanGenerusRow
import id.generus.app.data.model.Period
import id.generus.app.data.model.PeringatanAlpa
import id.generus.app.data.model.RekapUnit
import id.generus.app.data.model.UnitLevel
import id.generus.app.data.model.UnitNode
import id.generus.app.data.model.jenjangLabel
import id.generus.app.data.model.kurikulumEnabled
import id.generus.app.data.model.label
import id.generus.app.data.model.laporanRootUnit
import id.generus.app.ui.LoadVm
import id.generus.app.ui.components.AppCard
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.BannerTone
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.InfoBanner
import id.generus.app.ui.components.LoadingBox
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.RefreshableList
import id.generus.app.ui.components.SectionCard
import id.generus.app.ui.components.SelectField
import id.generus.app.ui.components.StatusBadge
import id.generus.app.ui.nav.GenerusDetail
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.screens.capaian.BigStat
import id.generus.app.ui.screens.capaian.StackedProgress
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.vm
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LaporanData(
    val month: YearMonth,
    val unit: UnitNode,
    val ancestors: List<UnitNode>,
    val children: List<UnitNode>,
    val period: Period?,
    val kehadiran: Map<String, KehadiranUnit>,
    val tren: List<TrenPoint>,
    val alpa: List<PeringatanAlpa>,
    val kelompok: List<KelompokRef>,
    val generus: List<LaporanGenerusRow>,
    val capaian: Map<String, RekapUnit>,
)

class LaporanVm(c: AppContainer, private val rootUnitId: String) : LoadVm<LaporanData>(c) {
    val month = MutableStateFlow(currentMonth())
    val unitId = MutableStateFlow<String?>(null)

    init { load() }

    override suspend fun fetch(): LaporanData {
        val m = month.value
        val (from, to) = m.range()
        val tree = c.capaian.unitWithChildren(unitId.value ?: rootUnitId)
        val unit = tree.firstOrNull { it.rel == "self" } ?: throw UserError("Unit tidak ditemukan.")
        val children = tree.filter { it.rel == "child" }
        val isKelompok = unit.level == UnitLevel.kelompok
        // Periode target yang beririsan dengan bulan laporan.
        val period = if (kurikulumEnabled) c.kurikulum.periods().filter { it.publishedAt != null }
            .firstOrNull { it.startsOn <= m.atEndOfMonth() && it.endsOn >= m.atDay(1) } else null
        val unitIds = listOf(unit.id) + children.map { it.id }
        val kehadiran = c.laporan.kehadiranUnits(from, to, unitIds).associateBy { it.unitId }
        if (kehadiran[unit.id] == null) throw UserError("Anda tidak punya akses ke laporan unit ini.")
        val kelompok = if (isKelompok) listOf(KelompokRef(unit.id, unit.name)) else c.laporan.kelompokUnder(unit.id)
        val capaian = period?.let { p -> c.capaian.rekapUnits(p.id, null, (unitIds + kelompok.map { it.id }).distinct()).associateBy { it.unitId } }.orEmpty()
        return LaporanData(
            month = m,
            unit = unit,
            ancestors = tree.filter { it.rel == "anc" },
            children = children,
            period = period,
            kehadiran = kehadiran,
            tren = c.laporan.tren(unit.id, recentMonths(6, m)),
            alpa = c.laporan.peringatanAlpa(unit.id),
            kelompok = kelompok,
            generus = if (isKelompok) c.laporan.laporanGenerus(from, to, unit.id, period?.id) else emptyList(),
            capaian = capaian,
        )
    }

    /** Laporan bulanan .xlsx: ringkasan per unit, rincian per generus, peringatan (padanan /laporan/ekspor). */
    suspend fun export(context: android.content.Context): java.io.File {
        val d = state.value.data ?: throw UserError("Laporan belum dimuat.")
        val (from, to) = d.month.range()
        val generus = d.generus.ifEmpty { c.laporan.laporanGenerus(from, to, d.unit.id, d.period?.id) }
        return withContext(Dispatchers.Default) {
            val modul = kurikulumEnabled
            val pct = { a: Int, b: Int -> if (b > 0) Math.round(a * 100.0 / b).toInt() else null }
            val ringkasan = mutableListOf<List<Any?>>(
                listOf("Laporan Bulanan Generus"),
                listOf("Bulan", d.month.label()),
                listOf("Unit", unitLabel(d.unit)),
            )
            if (modul) ringkasan += listOf("Periode target", d.period?.let { "${it.name} (${it.startsOn} s.d. ${it.endsOn})" } ?: "-")
            ringkasan += listOf("Dibuat", Instant.now().formatDateTime())
            ringkasan.add(emptyList())
            val header = mutableListOf<Any?>("Unit", "Generus aktif", "Agenda terkunci", "Presensi", "Hadir", "Terlambat", "Izin", "Sakit", "Alpa", "% Kehadiran")
            if (modul) header.addAll(listOf("Capaian tuntas", "Capaian dinilai", "% Capaian", "Materi disampaikan", "Materi × kelompok"))
            val headerRow = ringkasan.size
            ringkasan.add(header)
            for (u in listOf(d.unit) + d.children) {
                val k = d.kehadiran[u.id] ?: continue
                val row = mutableListOf<Any?>(unitLabel(u), k.generus, k.agendas, k.total, k.hadir, k.terlambat, k.izin, k.sakit, k.alpa, k.rate)
                val cap = d.capaian[u.id]
                if (modul) row.addAll(listOf(cap?.tuntas, cap?.slots, cap?.let { pct(it.tuntas, it.slots) }, cap?.delivered, cap?.deliverable))
                ringkasan.add(row)
            }
            val rincianHeader = mutableListOf<Any?>("Desa", "Kelompok", "Nama", "L/P", "Jenjang", "Status", "Presensi", "Hadir", "Terlambat", "Izin", "Sakit", "Alpa", "% Kehadiran")
            if (modul) rincianHeader.addAll(listOf("Capaian tuntas", "Capaian dinilai"))
            rincianHeader.add("Alpa 3× berturut")
            val rincian = listOf<List<Any?>>(rincianHeader) + generus.map { g ->
                val row = mutableListOf<Any?>(g.desa, g.kelompok, g.name, g.gender.name, jenjangLabel(g.jenjang, g.kategori), g.status.name, g.total, g.hadir, g.terlambat, g.izin, g.sakit, g.alpa, pct(g.hadir + g.terlambat, g.total))
                if (modul) row.addAll(listOf(g.capSlots.takeIf { it > 0 }?.let { g.capTuntas }, g.capSlots.takeIf { it > 0 }))
                row.add(if (g.alpaStreak) "Ya" else "")
                row
            }
            val peringatan = listOf(listOf<Any?>("Nama", "Kelompok", "Agenda terakhir")) +
                d.alpa.ifEmpty { null }?.map { listOf<Any?>(it.name, it.kelompok, it.lastAt.formatDateTime()) }.orEmpty().ifEmpty { listOf(listOf("Tidak ada generus dengan alpa 3× berturut-turut")) }
            val safe = unitLabel(d.unit).replace(Regex("[^\\w-]+"), "-")
            XlsxWriter()
                .sheet("Ringkasan", ringkasan, listOf(28) + List(14) { 12 }, headerRow)
                .sheet("Generus", rincian, listOf(16, 16, 28, 5, 18, 14) + List(10) { 10 })
                .sheet("Peringatan alpa", peringatan, listOf(30, 18, 18))
                .write(Share.file(context, "Laporan-$safe-${d.month}.xlsx"))
        }
    }
}

private fun unitLabel(u: UnitNode): String = if (u.name.lowercase().startsWith(u.level.label.lowercase())) u.name else "${u.level.label} ${u.name}"

/** Laporan bulanan (padanan /laporan): kehadiran, capaian, tren, peringatan, drill-down & ekspor Excel. */
@Composable
fun LaporanScreen(nav: NavHostController) {
    val me = LocalMe.current
    val root = me.laporanRootUnit()
    if (root == null) {
        AppScreen("Laporan") { p -> EmptyState("Laporan hanya untuk admin & kyai", Modifier.padding(p)) }
        return
    }
    val context = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val model = vm { LaporanVm(it, root.unitId) }
    val state by model.state.collectAsStateWithLifecycle()
    val month by model.month.collectAsStateWithLifecycle()
    var exporting by remember { mutableStateOf(false) }
    val d = state.data
    val modul = kurikulumEnabled

    fun go(unit: String) { model.unitId.value = unit; model.load() }

    AppScreen(
        "Laporan",
        subtitle = d?.let { "${it.month.label()} · ${unitLabel(it.unit)}" },
        actions = {
            IconButton(enabled = d != null && !exporting, onClick = {
                exporting = true
                scope.launch {
                    try {
                        Share.send(context, model.export(context), Share.XLSX, "Bagikan laporan Excel")
                    } catch (e: Throwable) {
                        toast(if (e is UserError) e.message ?: "" else "Gagal membuat file laporan.")
                    } finally {
                        exporting = false
                    }
                }
            }) { Icon(Icons.Rounded.FileDownload, contentDescription = "Unduh Excel") }
        },
    ) { p ->
        RefreshableList(refreshing = state.loading && d != null, onRefresh = model::load, padding = p) {
            item {
                SelectField("Bulan", recentMonths(12), month, { it.label() }, { model.month.value = it; model.load() })
            }
            when {
                d == null && state.error != null -> item { ErrorBox(state.error!!, model::load) }
                d == null -> item { LoadingBox() }
                else -> {
                    if (d.ancestors.isNotEmpty()) item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            (d.ancestors + d.unit).forEachIndexed { i, u ->
                                if (i > 0) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                val last = u.id == d.unit.id
                                Text(
                                    unitLabel(u),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                    modifier = if (last) Modifier else Modifier.clickable(role = Role.Button) { go(u.id) },
                                )
                            }
                        }
                    }
                    val total = d.kehadiran.getValue(d.unit.id)
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BigStat("Kehadiran", total.rate?.let { "$it%" } ?: "–", "${total.agendas} agenda terkunci · ${total.generus.formatId()} generus", Modifier.weight(1f)) {
                                StackedProgress(total.hadir + total.terlambat, 0, total.total)
                            }
                            if (modul) {
                                val cap = d.capaian[d.unit.id]
                                BigStat("Capaian target", cap?.takeIf { it.slots > 0 }?.let { "${percent(it.tuntas, it.slots)}%" } ?: "–", d.period?.let { "Periode ${it.name}" } ?: "Tidak ada periode", Modifier.weight(1f)) {
                                    if (cap != null) StackedProgress(cap.tuntas, cap.proses, cap.slots)
                                }
                            }
                        }
                    }
                    item { TrenCard(d.tren, d.month) }
                    item { Peringatan(d, modul) { id -> nav.navigate(GenerusDetail(id)) } }
                    if (d.unit.level != UnitLevel.kelompok && d.children.isNotEmpty()) item { ChildrenCard(d) { go(it) } }
                    if (d.unit.level == UnitLevel.kelompok) item { GenerusCard(d.generus, modul) { nav.navigate(GenerusDetail(it)) } }
                }
            }
        }
    }
}

@Composable
private fun TrenCard(tren: List<TrenPoint>, current: YearMonth) {
    val fmt = DateTimeFormatter.ofPattern("MMM", ID_LOCALE)
    SectionCard("Tren kehadiran 6 bulan") {
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            tren.forEach { t ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(t.rate?.let { "$it%" } ?: "–", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier.padding(top = 4.dp).fillMaxWidth().height((90 * (t.rate ?: 0) / 100).coerceAtLeast(2).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (t.month == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    )
                    Text(t.month.format(fmt), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun Peringatan(d: LaporanData, modul: Boolean, onGenerus: (String) -> Unit) {
    val x = GenerusTheme.extra
    SectionCard("Peringatan") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alpa 3× berturut-turut", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            StatusBadge("${d.alpa.size} generus", if (d.alpa.isEmpty()) x.hadir else x.alpa)
        }
        d.alpa.take(8).forEach { a ->
            Text(
                "${a.name} · ${a.kelompok} · terakhir ${a.lastAt.formatShortDate()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onGenerus(a.generusId) }.padding(vertical = 4.dp),
            )
        }
        if (d.alpa.size > 8) Text("dan ${d.alpa.size - 8} lainnya (lihat Excel)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val p = d.period
        if (modul && p != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            val kosong = d.kelompok.filter { k -> d.capaian[k.id]?.let { c -> c.slots + c.deliverable > 0 && c.tuntas + c.proses + c.delivered == 0 } == true }
            val t = today()
            val nearEnd = t <= p.endsOn && ChronoUnit.DAYS.between(t, p.endsOn) <= 7
            Text("Capaian belum diisi", style = MaterialTheme.typography.titleSmall)
            Text(
                "Periode ${p.name} berakhir ${p.endsOn.formatShort()}" + if (nearEnd) " — kurang dari sepekan lagi." else ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (kosong.isEmpty()) Text("Semua kelompok sudah mulai mengisi.", style = MaterialTheme.typography.bodyMedium)
            else InfoBanner("${kosong.size} kelompok belum mengisi sama sekali: ${kosong.joinToString(", ") { it.name }}", tone = if (nearEnd) BannerTone.Error else BannerTone.Warning)
        }
    }
}

@Composable
private fun ChildrenCard(d: LaporanData, onOpen: (String) -> Unit) {
    val childLabel = if (d.unit.level == UnitLevel.daerah) "desa" else "kelompok"
    AppCard(Modifier.fillMaxWidth()) {
        Text("Per $childLabel", style = MaterialTheme.typography.titleMedium)
        d.children.mapNotNull { u -> d.kehadiran[u.id]?.let { u to it } }.forEachIndexed { i, (u, k) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            val cap = d.capaian[u.id]
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(u.id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(unitLabel(u), style = MaterialTheme.typography.titleSmall)
                    StackedProgress(k.hadir + k.terlambat, 0, k.total)
                    Text(
                        "${k.generus} generus · ${k.agendas} agenda · H ${k.hadir + k.terlambat} / I ${k.izin} / S ${k.sakit} / A ${k.alpa}" +
                            (cap?.takeIf { it.slots > 0 }?.let { " · capaian ${percent(it.tuntas, it.slots)}%" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(k.rate?.let { "$it%" } ?: "–", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GenerusCard(rows: List<LaporanGenerusRow>, modul: Boolean, onOpen: (String) -> Unit) {
    val x = GenerusTheme.extra
    AppCard(Modifier.fillMaxWidth()) {
        Text("Per generus", style = MaterialTheme.typography.titleMedium)
        if (rows.isEmpty()) Text("Belum ada data generus.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEachIndexed { i, g ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(g.generusId) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(g.name, style = MaterialTheme.typography.titleSmall)
                        if (g.alpaStreak) StatusBadge("Alpa 3×", x.alpa)
                        if (g.status != id.generus.app.data.model.GenerusStatus.aktif) StatusBadge("Nonaktif", id.generus.app.ui.theme.StatusColor(MaterialTheme.colorScheme.onSurfaceVariant, x.tile))
                    }
                    Text(
                        "${jenjangLabel(g.jenjang, g.kategori)} · H ${g.hadir} T ${g.terlambat} I ${g.izin} S ${g.sakit} A ${g.alpa}" +
                            if (modul && g.capSlots > 0) " · capaian ${g.capTuntas}/${g.capSlots}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${g.hadir + g.terlambat}/${g.total}", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

private fun Instant.formatShortDate(): String = atZone(id.generus.app.core.APP_ZONE).toLocalDate().formatShort()
