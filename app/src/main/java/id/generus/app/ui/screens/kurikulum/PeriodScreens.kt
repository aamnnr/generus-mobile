package id.generus.app.ui.screens.kurikulum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.UserError
import id.generus.app.core.dateRange
import id.generus.app.core.label
import id.generus.app.data.model.Jenjang
import id.generus.app.data.model.Period
import id.generus.app.data.model.PeriodWrite
import id.generus.app.data.model.Target
import id.generus.app.data.model.canEditCurriculum
import id.generus.app.ui.LoadVm
import id.generus.app.ui.OnResult
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.BannerTone
import id.generus.app.ui.components.ConfirmSheet
import id.generus.app.ui.components.DateField
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.FormColumn
import id.generus.app.ui.components.InfoBanner
import id.generus.app.ui.components.LoadableContent
import id.generus.app.ui.components.LoadingBox
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.RefreshableList
import id.generus.app.ui.components.SecondaryButton
import id.generus.app.ui.components.SectionCard
import id.generus.app.ui.components.SelectField
import id.generus.app.ui.components.StickyBottom
import id.generus.app.ui.components.TextInput
import id.generus.app.ui.components.ChipRow
import id.generus.app.ui.components.AppCard
import id.generus.app.ui.nav.KurikulumHome
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.nav.PeriodDetail
import id.generus.app.ui.nav.PeriodForm
import id.generus.app.ui.nav.TargetForm
import id.generus.app.ui.popWithResult
import id.generus.app.ui.screens.capaian.groupTargets
import id.generus.app.ui.screens.capaian.range
import id.generus.app.ui.screens.capaian.tags
import id.generus.app.ui.screens.capaian.title
import id.generus.app.ui.theme.ArabicText
import id.generus.app.ui.vm
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow

data class PeriodDetailData(val period: Period, val jenjang: List<Jenjang>, val current: Jenjang, val targets: List<Target>)

class PeriodDetailVm(c: AppContainer, private val id: String) : LoadVm<PeriodDetailData>(c) {
    val jenjangId = MutableStateFlow<String?>(null)

    init { load() }

    override suspend fun fetch(): PeriodDetailData {
        val period = c.kurikulum.periods().firstOrNull { it.id == id } ?: throw UserError("Periode tidak ditemukan atau belum terbit.")
        val jenjang = c.agenda.jenjang()
        val current = jenjang.firstOrNull { it.id == jenjangId.value } ?: jenjang.firstOrNull { it.name == "Remaja" } ?: jenjang.first()
        return PeriodDetailData(period, jenjang, current, c.kurikulum.targets(period.id, current.id))
    }

    fun setPublished(publish: Boolean, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) { c.kurikulum.setPublished(id, publish) }

    fun move(targetId: String, up: Boolean, onError: (String) -> Unit) = run(onError, { load() }) { c.kurikulum.moveTarget(targetId, up) }

    fun deleteTarget(targetId: String, onError: (String) -> Unit, onOk: () -> Unit) = run({ e ->
        onError(if (e.startsWith("Data masih dipakai")) "Tidak bisa dihapus: sudah ada capaian untuk target ini." else e)
    }, { load(); onOk() }) { c.kurikulum.deleteTarget(targetId) }

    fun deletePeriod(onError: (String) -> Unit, onOk: () -> Unit) = run({ e ->
        onError(if (e.startsWith("Data masih dipakai")) "Tidak bisa dihapus: sudah ada capaian yang diisi untuk periode ini." else e)
    }, onOk) { c.kurikulum.deletePeriod(id) }
}

/** Target satu periode per jenjang (padanan /kurikulum/periode/[id]), seperti tabel dokumen PPG. */
@Composable
fun PeriodDetailScreen(nav: NavHostController, id: String) {
    val me = LocalMe.current
    val toast = LocalToast.current
    val editor = me.canEditCurriculum
    val model = vm(key = "period-$id") { PeriodDetailVm(it, id) }
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Target?>(null) }
    OnResult(nav) { model.load() }
    val d = state.data

    AppScreen(
        d?.let { "Periode ${it.period.name}" } ?: "Periode",
        onBack = { nav.popBackStack() },
        subtitle = d?.period?.let { "Semester ${it.semester} · ${dateRange(it.startsOn, it.endsOn)}" },
        actions = { if (editor && d != null) IconButton(onClick = { nav.navigate(PeriodForm(id)) }) { Icon(Icons.Rounded.Settings, contentDescription = "Pengaturan periode") } },
        floatingActionButton = {
            if (editor && d != null) ExtendedFloatingActionButton(
                onClick = { nav.navigate(TargetForm(id, d.current.id)) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Tambah target") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { p ->
        RefreshableList(refreshing = state.loading && d != null, onRefresh = model::load, padding = p) {
            when {
                d == null && state.error != null -> item { ErrorBox(state.error!!, model::load) }
                d == null -> item { LoadingBox() }
                else -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PeriodBadges(d.period)
                            if (editor) {
                                if (d.period.publishedAt == null) {
                                    InfoBanner("Masih draf: hanya Tim Kurikulum yang bisa melihat. Terbitkan setelah target semua jenjang selesai disusun.", tone = BannerTone.Warning)
                                    PrimaryButton("Terbitkan", onClick = { confirm = "publish" }, loading = busy, modifier = Modifier.fillMaxWidth())
                                } else {
                                    SecondaryButton("Kembalikan ke draf", onClick = { confirm = "unpublish" }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                            ChipRow(d.jenjang, d.current, { it.name }, { model.jenjangId.value = it.id; model.load() })
                        }
                    }
                    val groups = groupTargets(d.targets)
                    if (groups.isEmpty()) item {
                        EmptyState("Belum ada target untuk ${d.current.name}", body = if (editor) "Tambahkan target dari pustaka materi, materi baru, atau rentang kitab." else null)
                    }
                    groups.forEachIndexed { gi, g ->
                        item(key = g.aspek.name) {
                            AppCard(Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                        Text("${gi + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
                                    }
                                    Text(g.aspek.label, style = MaterialTheme.typography.titleMedium)
                                }
                                val flat = g.mapel.flatMap { it.targets }
                                g.mapel.forEach { mp ->
                                    Text(mp.mapel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    mp.strategy?.let { Text("Strategi: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    mp.targets.forEach { t ->
                                        val idx = flat.indexOf(t)
                                        TargetContent(t)
                                        if (editor) Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                            IconButton(onClick = { model.move(t.id, true) { toast(it) } }, enabled = idx > 0 && !busy) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "Naikkan") }
                                            IconButton(onClick = { model.move(t.id, false) { toast(it) } }, enabled = idx < flat.lastIndex && !busy) { Icon(Icons.Rounded.ArrowDownward, contentDescription = "Turunkan") }
                                            IconButton(onClick = { nav.navigate(TargetForm(id, d.current.id, t.id)) }) { Icon(Icons.Rounded.Edit, contentDescription = "Ubah target") }
                                            IconButton(onClick = { deleting = t }) { Icon(Icons.Rounded.Delete, contentDescription = "Hapus target", tint = MaterialTheme.colorScheme.error) }
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

    when (confirm) {
        "publish" -> ConfirmSheet(
            "Terbitkan periode?",
            "Target akan terlihat oleh admin & kyai, dan capaian bisa mulai diisi kelompok.",
            "Terbitkan",
            onConfirm = { confirm = null; model.setPublished(true, { toast(it) }) { toast("Periode diterbitkan.") } },
            onDismiss = { confirm = null },
        )
        "unpublish" -> ConfirmSheet(
            "Kembalikan ke draf?",
            "Target tidak lagi terlihat oleh kelompok sampai diterbitkan ulang. Capaian yang sudah diisi tetap tersimpan.",
            "Kembalikan ke draf",
            onConfirm = { confirm = null; model.setPublished(false, { toast(it) }) { toast("Periode kembali menjadi draf.") } },
            onDismiss = { confirm = null },
        )
    }
    deleting?.let { t ->
        ConfirmSheet(
            "Hapus target?",
            t.title(),
            "Hapus",
            danger = true,
            onConfirm = { deleting = null; model.deleteTarget(t.id, { toast(it) }) { toast("Target dihapus.") } },
            onDismiss = { deleting = null },
        )
    }
}

/** Isi satu target: judul/rentang, keterangan sasaran, teks Arab & terjemahan. */
@Composable
fun TargetContent(t: Target) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(t.title() + (t.range()?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyLarge)
        val tags = t.tags()
        if (tags.isNotEmpty()) Text(tags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        t.materi?.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        t.materi?.arabic?.let { Text(it, style = ArabicText, modifier = Modifier.fillMaxWidth()) }
        t.materi?.translation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        t.materi?.source?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

data class PeriodFormData(val existing: Period?, val periods: List<Period>)

class PeriodFormVm(c: AppContainer, private val id: String?) : LoadVm<PeriodFormData>(c) {
    init { load() }

    override suspend fun fetch(): PeriodFormData {
        val periods = c.kurikulum.periods()
        return PeriodFormData(id?.let { pid -> periods.firstOrNull { it.id == pid } ?: throw UserError("Periode tidak ditemukan.") }, periods)
    }

    fun save(name: String, semester: String, starts: LocalDate, ends: LocalDate, copyFrom: String?, onError: (String) -> Unit, onOk: (String) -> Unit) = run(onError) {
        if (id == null) onOk(c.kurikulum.createPeriod(name, semester, starts, ends, copyFrom))
        else {
            c.kurikulum.updatePeriod(id, PeriodWrite(name, semester, starts, ends))
            onOk(id)
        }
    }

    fun delete(onError: (String) -> Unit, onOk: () -> Unit) = run({ e ->
        onError(if (e.startsWith("Data masih dipakai")) "Tidak bisa dihapus: sudah ada capaian yang diisi untuk periode ini." else e)
    }, onOk) { c.kurikulum.deletePeriod(id!!) }
}

/** Buat / ubah periode target (padanan /kurikulum/periode/baru & pengaturan periode). */
@Composable
fun PeriodFormScreen(nav: NavHostController, id: String?) {
    val toast = LocalToast.current
    val model = vm(key = "period-form-$id") { PeriodFormVm(it, id) }
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    AppScreen(if (id == null) "Periode baru" else "Pengaturan periode", onBack = { nav.popBackStack() }) { p ->
        LoadableContent(state, model::load, Modifier.padding(p)) { d ->
            val e = d.existing
            var name by remember { mutableStateOf(e?.name ?: "") }
            var semester by remember { mutableStateOf(e?.semester ?: "") }
            var starts by remember { mutableStateOf(e?.startsOn) }
            var ends by remember { mutableStateOf(e?.endsOn) }
            var copyFrom by remember { mutableStateOf<Period?>(null) }
            var error by remember { mutableStateOf<String?>(null) }
            var confirmDelete by remember { mutableStateOf(false) }

            fun submit() {
                error = when {
                    name.trim().length < 3 -> "Nama periode minimal 3 huruf"
                    name.trim().length > 60 -> "Nama periode maksimal 60 huruf"
                    semester.isBlank() -> "Isi semester, mis. 1 - 2026"
                    starts == null || ends == null -> "Isi tanggal mulai dan selesai"
                    ends!! < starts!! -> "Tanggal selesai harus setelah tanggal mulai"
                    else -> null
                }
                if (error != null) return
                model.save(name.trim(), semester.trim(), starts!!, ends!!, copyFrom?.id, { error = it }) { savedId ->
                    toast(if (e == null) "Periode dibuat." else "Periode disimpan.")
                    if (e == null) nav.navigate(PeriodDetail(savedId)) { popUpTo(KurikulumHome) } else nav.popWithResult()
                }
            }

            Column(Modifier.imePadding()) {
                FormColumn(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (error != null) InfoBanner(error!!, tone = BannerTone.Error)
                    TextInput("Nama periode", name, { name = it }, placeholder = "mis. Oktober 2026", supporting = "Seperti di dokumen PPG.")
                    TextInput("Semester", semester, { semester = it }, placeholder = "mis. 1 - 2026")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DateField("Mulai", starts, { starts = it }, Modifier.weight(1f))
                        DateField("Selesai", ends, { ends = it }, Modifier.weight(1f))
                    }
                    if (e == null && d.periods.isNotEmpty()) {
                        SelectField(
                            "Salin target dari (opsional)",
                            listOf<Period?>(null) + d.periods,
                            copyFrom,
                            { it?.let { x -> "Periode ${x.name}" } ?: "Tidak menyalin" },
                            { copyFrom = it },
                            supporting = "Semua target (semua jenjang) disalin; ubah yang berbeda sebelum diterbitkan.",
                        )
                    }
                    if (e != null) {
                        SectionCard("Hapus periode", subtitle = "Hanya bila belum ada capaian yang diisi.") {
                            SecondaryButton("Hapus periode ${e.name}", onClick = { confirmDelete = true })
                        }
                    }
                }
                StickyBottom { PrimaryButton(if (e == null) "Buat periode" else "Simpan", onClick = ::submit, loading = busy, modifier = Modifier.fillMaxWidth()) }
            }
            if (confirmDelete) ConfirmSheet(
                "Hapus periode ${e?.name}?",
                "Semua target di periode ini ikut terhapus.",
                "Hapus periode",
                danger = true,
                onConfirm = {
                    confirmDelete = false
                    model.delete({ toast(it) }) { toast("Periode dihapus."); nav.popBackStack(KurikulumHome, inclusive = false) }
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}
