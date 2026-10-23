package id.generus.app.ui.screens.generus

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.Qr
import id.generus.app.core.UserError
import id.generus.app.core.ageOn
import id.generus.app.core.formatDateTime
import id.generus.app.core.formatMedium
import id.generus.app.core.formatShort
import id.generus.app.core.kitabStatus
import id.generus.app.core.today
import id.generus.app.data.model.AssessmentMode
import id.generus.app.data.model.CapaianStatus
import id.generus.app.data.model.GenerusAttendance
import id.generus.app.data.model.GenerusDetail
import id.generus.app.data.model.GenerusStatus
import id.generus.app.data.model.Period
import id.generus.app.data.model.ScopeUnit
import id.generus.app.data.model.canMutateGenerus
import id.generus.app.data.model.initials
import id.generus.app.data.model.jenjangLabel
import id.generus.app.data.model.kurikulumEnabled
import id.generus.app.data.model.label
import id.generus.app.data.pickActivePeriod
import id.generus.app.ui.LoadVm
import id.generus.app.ui.OnResult
import id.generus.app.ui.components.AppCard
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.Avatar
import id.generus.app.ui.components.BottomSheet
import id.generus.app.ui.components.ConfirmSheet
import id.generus.app.ui.components.DangerButton
import id.generus.app.ui.components.DateField
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.KeyValue
import id.generus.app.ui.components.ListRow
import id.generus.app.ui.components.LoadingBox
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.SecondaryButton
import id.generus.app.ui.components.SectionCard
import id.generus.app.ui.components.SelectField
import id.generus.app.ui.components.SingleChips
import id.generus.app.ui.components.StatusBadge
import id.generus.app.ui.components.TextInput
import id.generus.app.ui.nav.AgendaDetail
import id.generus.app.ui.nav.CapaianGenerus
import id.generus.app.ui.nav.GenerusForm
import id.generus.app.ui.nav.Kartu
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.theme.StatusColor
import id.generus.app.ui.theme.of
import id.generus.app.ui.vm
import java.time.Instant
import java.time.LocalDate

data class CapaianSummary(val period: Period, val tuntas: Int, val dinilai: Int)

data class GenerusDetailData(
    val g: GenerusDetail,
    val attendance: GenerusAttendance,
    val capaian: CapaianSummary?,
    val mutateTargets: List<ScopeUnit>,
)

class GenerusDetailVm(c: AppContainer, private val id: String, private val mutator: Boolean) : LoadVm<GenerusDetailData>(c) {
    init { load() }

    override suspend fun fetch(): GenerusDetailData {
        val g = c.generus.detail(id) ?: throw UserError("Generus tidak ditemukan atau di luar lingkup Anda.")
        val attendance = c.agenda.generusAttendance(id)
        val capaian = if (kurikulumEnabled) runCatching { capaianSummary(g) }.getOrNull() else null
        val mutate = if (mutator && g.status == GenerusStatus.aktif) runCatching { c.generus.scopeUnits("mutate") }.getOrDefault(emptyList()) else emptyList()
        return GenerusDetailData(g, attendance, capaian, mutate)
    }

    private suspend fun capaianSummary(g: GenerusDetail): CapaianSummary? {
        val period = pickActivePeriod(c.kurikulum.periods(), today()) ?: return null
        val targets = c.kurikulum.targets(period.id, g.jenjangId)
        val rows = c.capaian.generusCapaian(g.id, period.id).rows.associateBy { it.targetId }
        val dinilai = targets.filter { t -> t.id in rows && !t.optional && (t.kitab != null || t.materi?.assessment == AssessmentMode.generus) }
        val tuntas = dinilai.count { t ->
            val r = rows.getValue(t.id)
            (t.kitab?.let { k -> kitabStatus(r.position, k.from, k.to) } ?: r.status) == CapaianStatus.tuntas
        }
        return CapaianSummary(period, tuntas, dinilai.size)
    }

    fun changeStatus(status: GenerusStatus, date: LocalDate, note: String?, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) {
        if (date > today()) throw UserError("Tanggal tidak boleh di masa depan")
        if (!c.generus.changeStatus(id, status, date, note?.trim()?.ifEmpty { null })) throw UserError("Anda tidak punya akses untuk mengubah generus ini.")
    }

    fun mutate(kelompokId: String, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) {
        if (!c.generus.mutate(id, kelompokId)) throw UserError("Anda tidak punya akses untuk memindah generus ini.")
    }

    fun reissue(onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) { c.generus.reissueCard(id) }

    fun delete(onError: (String) -> Unit, onOk: () -> Unit) = run(onError, onOk) {
        if (!c.generus.delete(id)) throw UserError("Anda tidak punya akses untuk menghapus generus ini.")
    }
}

/** Detail generus (padanan /generus/[id]): data diri, capaian, kehadiran, status, kartu QR, mutasi, riwayat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerusDetailScreen(nav: NavHostController, id: String) {
    val me = LocalMe.current
    val toast = LocalToast.current
    val model = vm(key = "generus-$id") { GenerusDetailVm(it, id, me.canMutateGenerus) }
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<String?>(null) }
    val d = state.data
    val g = d?.g
    val writer = g?.canWrite == true

    // Muat ulang setelah kembali dari form ubah.
    OnResult(nav) { model.load() }

    AppScreen(
        g?.name ?: "Generus",
        onBack = { nav.popBackStack() },
        actions = { if (writer) IconButton(onClick = { nav.navigate(GenerusForm(id)) }) { Icon(Icons.Rounded.Edit, contentDescription = "Ubah data diri") } },
    ) { p ->
        when {
            d == null && state.error != null -> ErrorBox(state.error!!, model::load, Modifier.padding(p))
            d == null || g == null -> LoadingBox(Modifier.padding(p).padding(horizontal = 16.dp))
            else -> PullToRefreshBox(isRefreshing = state.loading, onRefresh = model::load, modifier = Modifier.fillMaxSize().padding(p)) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Header(g) }
                    item { DataDiri(g) }
                    d.capaian?.let { cap ->
                        item {
                            SectionCard(
                                "Capaian Periode ${cap.period.name}",
                                subtitle = if (cap.dinilai > 0) "${cap.tuntas} dari ${cap.dinilai} target tuntas" else "Tidak ada target yang dinilai per generus.",
                            ) {
                                SecondaryButton(if (writer) "Lihat & isi capaian" else "Lihat capaian", onClick = { nav.navigate(CapaianGenerus(g.id, cap.period.id)) })
                            }
                        }
                    }
                    item { Kehadiran(d.attendance) { nav.navigate(AgendaDetail(it)) } }
                    if (writer) item {
                        SectionCard(
                            "Status keanggotaan",
                            subtitle = if (g.status == GenerusStatus.aktif) "Tandai bila generus menikah atau pindah ke luar daerah."
                            else "${g.status.label} sejak ${g.statusDate?.formatMedium() ?: "-"}${g.statusNote?.let { " — $it" } ?: ""}.",
                        ) {
                            if (g.status == GenerusStatus.aktif) SecondaryButton("Ubah status", onClick = { sheet = "status" })
                            else PrimaryButton("Aktifkan kembali", onClick = { sheet = "reactivate" }, loading = busy)
                        }
                    }
                    if (writer && g.status == GenerusStatus.aktif && g.qrToken != null) item { KartuSection(g, onShare = { nav.navigate(Kartu(generusId = g.id)) }, onReissue = { sheet = "reissue" }) }
                    if (d.mutateTargets.isNotEmpty()) item {
                        SectionCard("Pindah kelompok", subtitle = "Mutasi di dalam daerah. Riwayat kehadiran tetap tersimpan.") {
                            SecondaryButton("Pindahkan ke kelompok lain", onClick = { sheet = "mutate" })
                        }
                    }
                    item { Riwayat(g) }
                    if (writer) item {
                        SectionCard("Hapus data", subtitle = "Hanya untuk data yang salah input. Untuk generus yang menikah atau pindah, ubah statusnya.") {
                            DangerButton("Hapus ${g.name}", onClick = { sheet = "delete" })
                        }
                    }
                }
            }
        }
    }

    if (g != null) when (sheet) {
        "status" -> StatusSheet(onDismiss = { sheet = null }) { status, date, note ->
            model.changeStatus(status, date, note, { toast(it) }) { sheet = null; toast("Status disimpan.") }
        }
        "reactivate" -> ConfirmSheet(
            "Aktifkan kembali ${g.name}?",
            "Generus akan kembali dihitung dalam presensi & target sejak hari ini. Kartu QR aktif lagi.",
            "Aktifkan kembali",
            onConfirm = { model.changeStatus(GenerusStatus.aktif, today(), null, { toast(it) }) { sheet = null; toast("Generus diaktifkan kembali.") } },
            onDismiss = { sheet = null },
        )
        "reissue" -> ConfirmSheet(
            "Terbitkan kartu baru?",
            "Token QR diganti sehingga kartu lama tidak berlaku lagi. Cetak atau bagikan kartu baru setelahnya.",
            "Terbitkan kartu baru",
            onConfirm = { model.reissue({ toast(it) }) { sheet = null; toast("Kartu baru diterbitkan. Kartu lama tidak berlaku lagi.") } },
            onDismiss = { sheet = null },
        )
        "mutate" -> MutateSheet(d!!.mutateTargets.filter { it.id != g.kelompokId }, onDismiss = { sheet = null }) { k ->
            model.mutate(k, { toast(it) }) { sheet = null; toast("Generus dipindahkan.") }
        }
        "delete" -> ConfirmSheet(
            "Hapus ${g.name}?",
            "Data generus beserta riwayat presensi & capaiannya dihapus permanen. Tidak bisa dibatalkan.",
            "Hapus permanen",
            danger = true,
            onConfirm = { model.delete({ toast(it) }) { toast("Generus dihapus."); nav.popBackStack() } },
            onDismiss = { sheet = null },
        )
    }
}

@Composable
private fun Header(g: GenerusDetail) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Avatar(initials(g.name), size = 56.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusBadge(g.status.label, if (g.status == GenerusStatus.aktif) GenerusTheme.extra.hadir else StatusColor(MaterialTheme.colorScheme.onSurfaceVariant, GenerusTheme.extra.tile))
            Text(
                "${jenjangLabel(g.jenjang, g.kategori)} · ${ageOn(g.birthDate)} tahun · Kelompok ${g.kelompok}, Desa ${g.desa}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DataDiri(g: GenerusDetail) {
    SectionCard("Data diri") {
        KeyValue("Jenis kelamin", g.gender.label)
        KeyValue("Tanggal lahir", g.birthDate.formatMedium())
        KeyValue("Jenjang", jenjangLabel(g.jenjang, g.kategori))
        KeyValue("Sekolah / kelas", g.school ?: "—")
        KeyValue("Orang tua/wali", g.guardianName ?: "—")
        KeyValue("No. HP orang tua/wali", g.guardianPhone ?: "—")
        KeyValue("Catatan", g.notes ?: "—")
    }
}

@Composable
private fun Kehadiran(a: GenerusAttendance, onAgenda: (String) -> Unit) {
    val s = a.stats
    SectionCard(
        "Kehadiran",
        subtitle = if (s.total > 0) "${Math.round(s.hadir * 100.0 / s.total)}% hadir dari ${s.total} agenda yang sudah dikunci · ${s.alpa} alpa" else "Belum ada agenda yang dikunci.",
    ) {
        a.rows.take(10).forEach { r ->
            ListRow(
                r.title,
                meta = "${r.startsAt.formatDateTime()} · ${r.unitName}",
                trailing = { StatusBadge(r.status.label, GenerusTheme.extra.of(r.status)) },
                onClick = { onAgenda(r.agendaId) },
                chevron = false,
            )
        }
    }
}

@Composable
private fun KartuSection(g: GenerusDetail, onShare: () -> Unit, onReissue: () -> Unit) {
    val qr = remember(g.qrToken) { Qr.bitmap(Qr.payload(g.qrToken!!), 480).asImageBitmap() }
    SectionCard("Kartu QR", subtitle = "Dipakai untuk presensi scan di pengajian desa/daerah. Bisa juga dipindai langsung dari layar ini.") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(
                qr,
                contentDescription = "QR kartu ${g.name}",
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp),
            )
            Text(g.qrToken!!.chunked(4).joinToString(" "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Cetak / bagikan", onClick = onShare, modifier = Modifier.weight(1f))
            SecondaryButton("Terbitkan ulang", onClick = onReissue, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Riwayat(g: GenerusDetail) {
    val items = (g.mutations.map { it.at to "Pindah kelompok: ${it.from} → ${it.to}" } +
        g.statuses.map { it.at to "Status: ${it.to.label} (${it.date.formatMedium()})${it.note?.let { n -> " — $n" } ?: ""}" })
        .sortedByDescending { it.first }
    SectionCard("Riwayat") {
        if (items.isEmpty()) Text("Belum ada perubahan status atau kelompok.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { (at: Instant, text) ->
            Text("${at.atZone(id.generus.app.core.APP_ZONE).toLocalDate().formatShort()} — $text", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusSheet(onDismiss: () -> Unit, onSave: (GenerusStatus, LocalDate, String?) -> Unit) {
    var status by remember { mutableStateOf(GenerusStatus.menikah) }
    var date by remember { mutableStateOf<LocalDate?>(today()) }
    var note by remember { mutableStateOf("") }
    BottomSheet("Ubah status keanggotaan", onDismiss) {
        Text("Sejak tanggal ini generus keluar dari sasaran agenda & target, kartu QR nonaktif; riwayat tetap tersimpan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChips(listOf(GenerusStatus.menikah, GenerusStatus.pindah_sambung), status, { it.label }, { status = it })
        DateField("Tanggal", date, { date = it })
        TextInput("Keterangan (opsional)", note, { note = it }, placeholder = if (status == GenerusStatus.pindah_sambung) "mis. daerah tujuan" else null)
        PrimaryButton("Simpan status", onClick = { date?.let { onSave(status, it, note) } }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MutateSheet(options: List<ScopeUnit>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var target by remember { mutableStateOf<ScopeUnit?>(null) }
    BottomSheet("Pindah kelompok", onDismiss) {
        SelectField("Kelompok tujuan", options, target, { "Kelompok ${it.name}" }, { target = it }, group = { "Desa ${it.desaName}" })
        PrimaryButton("Pindahkan", onClick = { target?.let { onSave(it.id) } }, enabled = target != null, modifier = Modifier.fillMaxWidth())
    }
}
