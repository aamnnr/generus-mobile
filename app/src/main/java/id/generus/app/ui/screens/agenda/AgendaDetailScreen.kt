package id.generus.app.ui.screens.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.formatDateLong
import id.generus.app.core.formatDateTime
import id.generus.app.core.formatId
import id.generus.app.core.formatTime
import id.generus.app.core.targetLabel
import id.generus.app.core.zoneLabel
import id.generus.app.data.AttendanceEntry
import id.generus.app.data.local.OutboxItem
import id.generus.app.data.model.AgendaDetail
import id.generus.app.data.model.AttendanceStatus
import id.generus.app.data.model.Jenjang
import id.generus.app.data.model.ProfileOption
import id.generus.app.data.model.RosterRow
import id.generus.app.data.model.label
import id.generus.app.data.model.letter
import id.generus.app.ui.CacheSpec
import id.generus.app.ui.LoadVm
import id.generus.app.ui.OnResult
import id.generus.app.ui.appContainer
import id.generus.app.ui.components.AppCard
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.BannerTone
import id.generus.app.ui.components.BottomSheet
import id.generus.app.ui.components.ConfirmSheet
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.InfoBanner
import id.generus.app.ui.components.ListRow
import id.generus.app.ui.components.LoadingBox
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.OfflineStrip
import id.generus.app.ui.components.Overline
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.ProgressBar
import id.generus.app.ui.components.RowDivider
import id.generus.app.ui.components.SearchPill
import id.generus.app.ui.components.SecondaryButton
import id.generus.app.ui.components.StatusBadge
import id.generus.app.ui.components.SwitchRow
import id.generus.app.ui.nav.AgendaForm
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.nav.Scanner
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.theme.of
import id.generus.app.ui.vm
import java.time.Instant
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable

@Serializable
data class AgendaDetailData(val agenda: AgendaDetail, val jenjang: List<Jenjang>)

class AgendaDetailVm(c: AppContainer, private val id: String) : LoadVm<AgendaDetailData?>(c, CacheSpec("agenda-$id", AgendaDetailData.serializer().nullable)) {
    init {
        load()
        viewModelScope.launch { c.presensi.synced.collect { load() } }
    }

    override suspend fun fetch(): AgendaDetailData? {
        val agenda = c.agenda.detail(id) ?: return null
        return AgendaDetailData(agenda, runCatching { c.agenda.jenjang() }.getOrDefault(emptyList()))
    }

    fun record(entries: List<AttendanceEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch { c.presensi.enqueue(entries) }
    }

    fun cancel(series: Boolean, onError: (String) -> Unit, onOk: (Int) -> Unit) = run(onError, { load() }) {
        onOk(c.agenda.cancel(id, series))
    }

    fun lock(onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) { c.agenda.lock(id) }

    fun addScanner(userId: String, grantedBy: String, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) {
        val a = state.value.data?.agenda ?: return@run
        c.agenda.addScanner(id, userId, grantedBy, a.delegateUntil)
    }

    fun removeScanner(userId: String, onError: (String) -> Unit) = run(onError, { load() }) { c.agenda.removeScanner(id, userId) }

    suspend fun candidates(selfId: String): List<ProfileOption> = c.agenda.scannerCandidates(selfId)
}

/** Detail agenda + checklist presensi offline-first (padanan /agenda/[id]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaDetailScreen(nav: NavHostController, id: String) {
    val me = LocalMe.current
    val c = appContainer()
    val toast = LocalToast.current
    val model = vm(key = "agenda-$id") { AgendaDetailVm(it, id) }
    val state by model.state.collectAsStateWithLifecycle()
    val stale by model.stale.collectAsStateWithLifecycle()
    val online by c.network.online.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val queued by c.presensi.observeOutbox(id).collectAsStateWithLifecycle(emptyList())

    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var sheet by remember { mutableStateOf<String?>(null) }
    var confirmBulk by remember { mutableStateOf(false) }

    val data = state.data
    val a = data?.agenda
    OnResult(nav) { model.load() }

    AppScreen(
        a?.title ?: "Agenda",
        onBack = { nav.popBackStack() },
        subtitle = a?.let { "${it.level.label} ${it.unitName}" },
        actions = {
            if (a?.canManage == true && a.cancelledAt == null && a.lockedAt == null) {
                IconButton(onClick = { nav.navigate(AgendaForm(a.id)) }) { Icon(Icons.Rounded.Edit, contentDescription = "Ubah agenda") }
            }
        },
        bottomBar = { if (a != null && a.roster.any { it.canWrite }) SyncBar(queued, online) },
    ) { p ->
        when {
            state.data == null && state.error != null -> ErrorBox(state.error!!, model::load, Modifier.padding(p))
            state.data == null && state.loading -> LoadingBox(Modifier.padding(p).padding(horizontal = 16.dp))
            a == null -> ErrorBox("Agenda tidak ditemukan atau Anda tidak punya akses.", null, Modifier.padding(p))
            else -> {
                val queuedMap = queued.associateBy { it.generusId }
                fun current(r: RosterRow): AttendanceStatus? = queuedMap[r.generusId]?.status?.let { AttendanceStatus.valueOf(it) } ?: r.status
                val writable = a.roster.filter { it.canWrite }
                val q = query.trim().lowercase()
                val visible = if (q.isEmpty()) a.roster else a.roster.filter { it.name.lowercase().contains(q) }
                val showGroup = a.roster.map { it.kelompok to it.desa }.distinct().size > 1
                val groups = visible.groupBy { if (showGroup) "${it.kelompok} · Desa ${it.desa}" else "" }
                val rejected = queued.filter { it.error != null }
                val now = Instant.now()
                val canScan = (a.canManage || a.isScanner) && a.lockedAt == null && a.cancelledAt == null

                PullToRefreshBox(isRefreshing = state.loading, onRefresh = model::load, modifier = Modifier.fillMaxSize().padding(p)) {
                    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (stale) item { OfflineStrip("Offline: daftar dari salinan terakhir. Ketukan tetap tersimpan dan dikirim saat online.") }
                        item { HeaderCard(a, data.jenjang) }
                        item { Summary(a.roster.map { current(it) }) }
                        if (canScan) item {
                            PrimaryButton("Scan QR", onClick = { nav.navigate(Scanner(a.id)) }, icon = Icons.Rounded.QrCodeScanner, modifier = Modifier.fillMaxWidth())
                        }
                        if (!a.canManage && writable.isNotEmpty() && a.lockedAt == null) item {
                            InfoBanner("Anda bisa melengkapi presensi anggota kelompok Anda sampai ${a.delegateUntil.formatDateTime()} ${zoneLabel()}.")
                        }
                        if (a.lockedAt != null) item {
                            InfoBanner("Presensi dikunci ${a.lockedAt.formatDateTime()}. Peserta yang belum tercatat menjadi alpa.", icon = Icons.Rounded.Lock)
                        }
                        if (rejected.isNotEmpty()) item {
                            InfoBanner("${rejected.size} perubahan ditolak server: ${rejected.first().error}", tone = BannerTone.Error) {
                                Text("Coba lagi", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { scope.launch { c.presensi.retryRejected(id) } })
                                Text("Buang", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { scope.launch { c.presensi.discardRejected(id) } })
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SearchPill(query, { query = it }, "Cari nama peserta")
                                if (writable.any { current(it) == null } && a.lockedAt == null) {
                                    SecondaryButton("Hadirkan semua yang belum diisi", onClick = { confirmBulk = true }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        if (visible.isEmpty()) item {
                            Text(
                                if (a.roster.isEmpty()) "Belum ada peserta sasaran yang terlihat oleh akun Anda." else "Tidak ada peserta yang cocok.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        groups.forEach { (group, rows) ->
                            if (group.isNotEmpty()) item(key = "g:$group") { Overline(group, Modifier.padding(start = 4.dp, top = 4.dp)) }
                            item(key = "list:$group") {
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                                    Column {
                                        rows.forEachIndexed { i, r ->
                                            if (i > 0) RowDivider()
                                            ChecklistRow(r, current(r), queuedMap[r.generusId], enabled = r.canWrite && a.lockedAt == null && a.cancelledAt == null) { s ->
                                                model.record(listOf(AttendanceEntry(a.id, r.generusId, s)))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Text(
                                "H = Hadir · T = Terlambat · I = Izin · S = Sakit · A = Alpa. Ketukan langsung tersimpan, juga saat sinyal putus.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (a.canManage) item {
                            ManageCard(
                                a = a,
                                now = now,
                                busy = busy,
                                onCancel = { sheet = "cancel" },
                                onLock = { sheet = "lock" },
                                onAddScanner = { sheet = "scanner" },
                                onRemoveScanner = { uid -> model.removeScanner(uid) { toast(it) } },
                            )
                        }
                    }
                }

                if (confirmBulk) {
                    val n = writable.count { current(it) == null }
                    ConfirmSheet(
                        "Hadirkan $n peserta?",
                        "Semua peserta yang belum diisi akan ditandai Hadir. Status lain bisa diubah setelahnya.",
                        "Hadirkan semua",
                        onConfirm = {
                            confirmBulk = false
                            model.record(writable.filter { current(it) == null }.map { AttendanceEntry(a.id, it.generusId, AttendanceStatus.hadir) })
                        },
                        onDismiss = { confirmBulk = false },
                    )
                }
                when (sheet) {
                    "cancel" -> CancelSheet(a.recurrenceId != null, onDismiss = { sheet = null }) { series ->
                        sheet = null
                        model.cancel(series, { toast(it) }) { n -> toast(if (series) "$n agenda dibatalkan." else "Agenda dibatalkan.") }
                    }
                    "lock" -> ConfirmSheet(
                        "Kunci presensi?",
                        "Peserta yang belum tercatat menjadi alpa. Setelah dikunci, presensi tidak bisa diubah lagi lewat aplikasi.",
                        "Kunci presensi",
                        onConfirm = { sheet = null; model.lock({ toast(it) }) { toast("Presensi dikunci. Peserta yang belum tercatat menjadi alpa.") } },
                        onDismiss = { sheet = null },
                    )
                    "scanner" -> ScannerPicker(model, me.id, existing = a.scanners.map { it.userId }.toSet(), onDismiss = { sheet = null }) { uid ->
                        sheet = null
                        model.addScanner(uid, me.id, { toast(it) }) { toast("Petugas scan ditambahkan.") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderCard(a: AgendaDetail, jenjang: List<Jenjang>) {
    AppCard(Modifier.fillMaxWidth()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val x = GenerusTheme.extra
            val now = Instant.now()
            when {
                a.cancelledAt != null -> StatusBadge("Dibatalkan", x.alpa)
                a.lockedAt != null -> StatusBadge("Terkunci", id.generus.app.ui.theme.StatusColor(MaterialTheme.colorScheme.onSurfaceVariant, x.tile))
                !a.startsAt.isAfter(now) && a.endsAt.isAfter(now) -> StatusBadge("Berlangsung", x.hadir, dot = true)
            }
            if (a.recurrenceId != null) StatusBadge("Mingguan", id.generus.app.ui.theme.StatusColor(x.brassText, x.brassSoft))
        }
        Text(a.startsAt.formatDateLong(), style = MaterialTheme.typography.titleSmall)
        Text(
            "${a.startsAt.formatTime()}–${a.endsAt.formatTime()} ${zoneLabel()} · terlambat setelah ${a.lateAfter.formatTime()}" + (a.location?.let { "\n$it" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (jenjang.isNotEmpty()) {
            Text("Sasaran: ${targetLabel(a.targetJenjangIds, a.targetKategoriIds, a.targetGender, jenjang)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Kotak statistik berwarna per status + persentase hadir. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(statuses: List<AttendanceStatus?>) {
    val total = statuses.size
    val counts = AttendanceStatus.entries.associateWith { s -> statuses.count { it == s } }
    val belum = statuses.count { it == null }
    val present = (counts[AttendanceStatus.hadir] ?: 0) + (counts[AttendanceStatus.terlambat] ?: 0)
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(present.formatId(), style = MaterialTheme.typography.headlineMedium)
            Text(" / ${total.formatId()} hadir", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp).weight(1f))
            Text(if (total > 0) "${Math.round(present * 100.0 / total)}%" else "—", style = MaterialTheme.typography.titleMedium)
        }
        ProgressBar(if (total > 0) present.toFloat() / total else 0f, color = GenerusTheme.extra.hadir.solid)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AttendanceStatus.entries.forEach { s -> StatusBadge("${s.letter} ${counts[s] ?: 0}", GenerusTheme.extra.of(s)) }
            StatusBadge("Belum $belum", id.generus.app.ui.theme.StatusColor(MaterialTheme.colorScheme.onSurfaceVariant, GenerusTheme.extra.tile))
        }
    }
}

/** Satu baris checklist: nama (L/P) + titik antrian + tombol H/T/I/S/A 40 dp. */
@Composable
private fun ChecklistRow(r: RosterRow, value: AttendanceStatus?, queued: OutboxItem?, enabled: Boolean, onSelect: (AttendanceStatus) -> Unit) {
    val x = GenerusTheme.extra
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
            Text(" (${r.gender.name})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (queued != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(if (queued.error != null) x.alpa.solid else x.terlambat.solid)
                        .semantics { contentDescription = if (queued.error != null) "Ditolak server" else "Menunggu dikirim" },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AttendanceStatus.entries.forEach { s ->
                val active = value == s
                val c = x.of(s)
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) c.solid else MaterialTheme.colorScheme.surface)
                        .border(1.dp, if (active) c.solid else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .clickable(enabled = enabled && !active, role = Role.RadioButton) { onSelect(s) }
                        .semantics { contentDescription = "${s.label} untuk ${r.name}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        s.letter,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            active -> androidx.compose.ui.graphics.Color.White
                            enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    )
                }
            }
        }
    }
}

/** Bilah status kirim lengket di bawah (DESIGN.md §7.10). */
@Composable
private fun SyncBar(queued: List<OutboxItem>, online: Boolean) {
    val waiting = queued.count { it.error == null }
    val x = GenerusTheme.extra
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (waiting == 0) x.hadir.solid else if (!online) x.terlambat.solid else x.izin.solid))
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    waiting == 0 -> "Semua perubahan tersimpan di server"
                    !online -> "$waiting perubahan tersimpan di perangkat, dikirim otomatis saat online"
                    else -> "Mengirim $waiting perubahan…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManageCard(
    a: AgendaDetail,
    now: Instant,
    busy: Boolean,
    onCancel: () -> Unit,
    onLock: () -> Unit,
    onAddScanner: () -> Unit,
    onRemoveScanner: (String) -> Unit,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Text("Kelola agenda", style = MaterialTheme.typography.titleMedium)
        if (a.cancelledAt == null && a.lockedAt == null) {
            if (!now.isBefore(a.endsAt)) {
                PrimaryButton("Kunci presensi", onClick = onLock, icon = Icons.Rounded.Lock, loading = busy, modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    "Presensi bisa dikunci setelah agenda selesai, dan terkunci otomatis pada ${a.delegateUntil.formatDateTime()} ${zoneLabel()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SecondaryButton("Batalkan agenda", onClick = onCancel, icon = Icons.Rounded.EventBusy, modifier = Modifier.fillMaxWidth())
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Petugas scan", style = MaterialTheme.typography.titleSmall)
                Text("Akses sementara untuk memindai QR agenda ini saja.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (a.cancelledAt == null && a.lockedAt == null) {
                IconButton(onClick = onAddScanner) { Icon(Icons.Rounded.PersonAdd, contentDescription = "Tambah petugas scan", tint = MaterialTheme.colorScheme.primary) }
            }
        }
        if (a.scanners.isEmpty()) {
            Text("Belum ada petugas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        a.scanners.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleSmall)
                    Text("${s.email ?: ""} · s.d. ${s.expiresAt.formatDateTime()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onRemoveScanner(s.userId) }) { Icon(Icons.Rounded.PersonRemove, contentDescription = "Cabut akses ${s.name}", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun CancelSheet(recurring: Boolean, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var series by remember { mutableStateOf(false) }
    ConfirmSheet(
        "Batalkan agenda?",
        "Agenda yang dibatalkan tidak dihitung dalam laporan kehadiran.",
        "Batalkan agenda",
        danger = true,
        onConfirm = { onConfirm(series) },
        onDismiss = onDismiss,
        extra = { if (recurring) SwitchRow("Juga pekan-pekan berikutnya", series, { series = it }, meta = "Agenda mingguan yang belum dikunci dalam seri ini.") },
    )
}

@Composable
private fun ScannerPicker(model: AgendaDetailVm, selfId: String, existing: Set<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var list by remember { mutableStateOf<List<ProfileOption>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { model.candidates(selfId) }.onSuccess { list = it }.onFailure { error = "Gagal memuat daftar akun." }
    }
    BottomSheet("Tambah petugas scan", onDismiss) {
        Text("Akses berlaku sampai batas pelengkapan presensi agenda ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SearchPill(q, { q = it }, "Cari nama atau email")
        when {
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            list == null -> LoadingBox(Modifier.heightIn(max = 120.dp))
            else -> {
                val filtered = list!!.filter { it.id !in existing && (q.isBlank() || it.name.contains(q, true) || (it.email ?: "").contains(q, true)) }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.id }) { p -> ListRow(p.name, meta = p.email, onClick = { onPick(p.id) }, chevron = false) }
                    if (filtered.isEmpty()) item { Text("Tidak ada akun yang cocok.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
                }
            }
        }
    }
}
