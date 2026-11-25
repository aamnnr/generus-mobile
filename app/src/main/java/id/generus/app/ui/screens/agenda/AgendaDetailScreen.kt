package id.generus.app.ui.screens.agenda

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import id.generus.app.core.userMessage
import id.generus.app.data.model.KelompokAttendance
import id.generus.app.ui.components.Loadable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
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

/** Isi kartu presensi: ringkasan per kelompok, daftar satu kelompok, atau hasil pencarian lintas kelompok. */
sealed interface PresensiView {
    data object Summary : PresensiView
    data class Kelompok(val kelompokId: String) : PresensiView
    data class Search(val q: String) : PresensiView
}

private const val SEARCH_LIMIT = 50

class AgendaDetailVm(c: AppContainer, private val id: String) : LoadVm<AgendaDetailData?>(c, CacheSpec("agenda-$id", AgendaDetailData.serializer().nullable)) {
    val view = MutableStateFlow<PresensiView>(PresensiView.Summary)
    val query = MutableStateFlow("")
    private val _roster = MutableStateFlow(Loadable<List<RosterRow>>(loading = false))
    val roster: StateFlow<Loadable<List<RosterRow>>> = _roster.asStateFlow()
    private var rosterFor: PresensiView = PresensiView.Summary
    private var rosterJob: Job? = null
    private var searchJob: Job? = null

    init {
        load()
        viewModelScope.launch { c.presensi.synced.collect { refreshAll() } }
    }

    // Tampilan awal = ringkasan per kelompok (dihitung di database); daftar nama baru
    // dimuat untuk satu kelompok atau hasil pencarian, agar ringan meski pesertanya ribuan.
    override suspend fun fetch(): AgendaDetailData? {
        val agenda = c.agenda.detailSummary(id) ?: return null
        return AgendaDetailData(agenda, runCatching { c.agenda.jenjang() }.getOrDefault(emptyList()))
    }

    fun refreshAll() {
        refreshQuietly()
        loadRoster(rosterFor, quiet = true)
    }

    fun open(v: PresensiView) {
        searchJob?.cancel()
        if (v !is PresensiView.Search) query.value = ""
        view.value = v
    }

    /** Cari nama di semua kelompok (minimal 2 huruf), dengan jeda ketik. */
    fun search(text: String) {
        query.value = text
        searchJob?.cancel()
        val q = text.trim().take(60)
        searchJob = viewModelScope.launch {
            delay(400)
            view.value = if (q.length >= 2) PresensiView.Search(q) else PresensiView.Summary
        }
    }

    fun loadRoster(v: PresensiView, quiet: Boolean = false) {
        rosterFor = v
        rosterJob?.cancel()
        if (v == PresensiView.Summary) {
            _roster.value = Loadable(loading = false)
            return
        }
        val key = "agenda-$id-" + when (v) {
            is PresensiView.Kelompok -> "k-${v.kelompokId}"
            is PresensiView.Search -> "q-${v.q.lowercase()}"
            PresensiView.Summary -> ""
        }
        val serializer = ListSerializer(RosterRow.serializer())
        if (!quiet) _roster.update { if (it.data != null && rosterFor == v) it.copy(loading = true) else Loadable(loading = true) }
        rosterJob = viewModelScope.launch {
            try {
                val rows = when (v) {
                    is PresensiView.Kelompok -> c.agenda.peserta(id, kelompokId = v.kelompokId)
                    is PresensiView.Search -> c.agenda.peserta(id, q = v.q, limit = SEARCH_LIMIT)
                    PresensiView.Summary -> emptyList()
                }
                c.cache.put(key, serializer, rows)
                _roster.value = Loadable(rows, loading = false)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val cached = if (_roster.value.data == null) c.cache.get(key, serializer) else null
                _roster.update {
                    when {
                        cached != null -> Loadable(cached, loading = false)
                        it.data != null -> it.copy(loading = false)
                        else -> Loadable(loading = false, error = e.userMessage("Gagal memuat daftar peserta."))
                    }
                }
            }
        }
    }

    fun record(entries: List<AttendanceEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch { c.presensi.enqueue(entries) }
    }

    fun cancel(series: Boolean, onError: (String) -> Unit, onOk: (Int) -> Unit) = run(onError, { load() }) {
        onOk(c.agenda.cancel(id, series))
    }

    fun lock(onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { refreshAll(); onOk() }) { c.agenda.lock(id) }

    fun addScanner(userId: String, grantedBy: String, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) {
        val a = state.value.data?.agenda ?: return@run
        c.agenda.addScanner(id, userId, grantedBy, a.delegateUntil)
    }

    fun removeScanner(userId: String, onError: (String) -> Unit) = run(onError, { load() }) { c.agenda.removeScanner(id, userId) }

    suspend fun candidates(selfId: String): List<ProfileOption> = c.agenda.scannerCandidates(selfId)
}

/** Detail agenda: rekap per kelompok + checklist presensi offline-first (padanan /agenda/[id]). */
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
    val chosen by model.view.collectAsStateWithLifecycle()
    val search by model.query.collectAsStateWithLifecycle()
    val roster by model.roster.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var localQuery by rememberSaveable { mutableStateOf("") }
    var onlyEmpty by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var confirmBulk by remember { mutableStateOf(false) }
    var liveAt by remember { mutableStateOf<Instant?>(null) }

    val data = state.data
    val a = data?.agenda
    OnResult(nav) { model.refreshAll() }

    // Satu kelompok saja → langsung tampilkan daftarnya.
    val single = a != null && a.summary.size <= 1
    val view: PresensiView = when {
        a == null -> PresensiView.Summary
        single -> a.summary.firstOrNull()?.let { PresensiView.Kelompok(it.kelompokId) } ?: PresensiView.Summary
        else -> chosen
    }
    LaunchedEffect(view) {
        model.loadRoster(view)
        localQuery = ""
    }

    // Perbarui otomatis sejak 1 jam sebelum mulai sampai batas pelengkapan, agar hasil
    // pemindai petugas lain langsung terlihat. Berhenti saat aplikasi tidak tampil.
    val now = Instant.now()
    val live = a != null && a.lockedAt == null && a.cancelledAt == null &&
        !now.isBefore(a.startsAt.minusSeconds(3600)) && !now.isAfter(a.delegateUntil)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(15_000)
                if (c.network.online.value) {
                    model.refreshAll()
                    liveAt = Instant.now()
                }
            }
        }
    }

    BackHandler(enabled = !single && view != PresensiView.Summary) { model.open(PresensiView.Summary) }

    AppScreen(
        a?.title ?: "Agenda",
        onBack = { nav.popBackStack() },
        subtitle = a?.let { "${it.level.label} ${it.unitName}" },
        actions = {
            if (a?.canManage == true && a.cancelledAt == null && a.lockedAt == null) {
                IconButton(onClick = { nav.navigate(AgendaForm(a.id)) }) { Icon(Icons.Rounded.Edit, contentDescription = "Ubah agenda") }
            }
        },
        bottomBar = { if (roster.data?.any { it.canWrite } == true && a?.cancelledAt == null) SyncBar(queued, online) },
    ) { p ->
        when {
            state.data == null && state.error != null -> ErrorBox(state.error!!, model::load, Modifier.padding(p))
            state.data == null && state.loading -> LoadingBox(Modifier.padding(p).padding(horizontal = 16.dp))
            a == null -> ErrorBox("Agenda tidak ditemukan atau Anda tidak punya akses.", null, Modifier.padding(p))
            else -> {
                val queuedMap = queued.associateBy { it.generusId }
                fun current(r: RosterRow): AttendanceStatus? = queuedMap[r.generusId]?.status?.let { AttendanceStatus.valueOf(it) } ?: r.status
                val cancelled = a.cancelledAt != null
                // Pengelola tetap bisa mengoreksi setelah dikunci; server yang menentukan can_write.
                val rows = roster.data.orEmpty().map { if (cancelled) it.copy(canWrite = false) else it }
                val writable = rows.filter { it.canWrite }
                val q = localQuery.trim().lowercase()
                val visible = rows.filter { (q.isEmpty() || it.name.lowercase().contains(q)) && (!onlyEmpty || current(it) == null) }
                val showGroup = view is PresensiView.Search
                val groups = visible.groupBy { if (showGroup) "${it.kelompok} · Desa ${it.desa}" else "" }
                val rejected = queued.filter { it.error != null }
                val sum = KelompokAttendance.sum(a.summary)
                val scanOpen = a.lockedAt == null && !cancelled && (a.canManage || a.isScanner)
                val selected = (view as? PresensiView.Kelompok)?.let { v -> a.summary.firstOrNull { it.kelompokId == v.kelompokId } }
                val started = !now.isBefore(a.startsAt)

                PullToRefreshBox(isRefreshing = state.loading || roster.loading, onRefresh = model::refreshAll, modifier = Modifier.fillMaxSize().padding(p)) {
                    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (stale) item { OfflineStrip("Offline: data dari salinan terakhir. Ketukan tetap tersimpan dan dikirim saat online.") }
                        item { HeaderCard(a, data.jenjang) }
                        if (scanOpen) item {
                            PrimaryButton("Buka pemindai QR", onClick = { nav.navigate(Scanner(a.id)) }, icon = Icons.Rounded.QrCodeScanner, modifier = Modifier.fillMaxWidth())
                        }
                        item { StatGrid(sum) }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Kehadiran ${Math.round(sum.rate * 100)}%" +
                                        (if (sum.belum > 0) " · ${sum.belum.formatId()} belum diisi" else "") +
                                        (if (a.lockedAt == null && !cancelled) " · admin di bawahnya bisa melengkapi anggotanya sampai ${a.delegateUntil.formatDateTime()}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (live) LiveIndicator(liveAt)
                            }
                        }
                        if (a.lockedAt != null) item {
                            InfoBanner("Presensi dikunci ${a.lockedAt.formatDateTime()}. Peserta yang belum tercatat menjadi alpa.", icon = Icons.Rounded.Lock)
                        }
                        if (rejected.isNotEmpty()) item {
                            InfoBanner("${rejected.size} perubahan ditolak server: ${rejected.first().error}", tone = BannerTone.Error) {
                                Text("Coba lagi", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { scope.launch { c.presensi.retryRejected(id) } })
                                Text("Buang perubahan ini", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { scope.launch { c.presensi.discardRejected(id) } })
                            }
                        }

                        // Judul kartu presensi
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (view != PresensiView.Summary && !single) {
                                    Row(
                                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { model.open(PresensiView.Summary) }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Semua kelompok", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(
                                    when {
                                        view is PresensiView.Kelompok && !single && selected != null -> "${selected.kelompok} · Desa ${selected.desa}"
                                        view is PresensiView.Search -> "Hasil pencarian “${view.q}”"
                                        else -> "Presensi"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    when {
                                        cancelled -> "Agenda dibatalkan."
                                        view == PresensiView.Summary -> "Hasil pemindai QR masuk otomatis. Pilih kelompok atau cari nama untuk melihat daftar dan mengisi presensi manual."
                                        !started -> "Agenda belum dimulai. Izin/sakit sudah bisa dicatat."
                                        writable.isNotEmpty() -> "Ketuk status untuk setiap peserta; tersimpan otomatis."
                                        else -> "Anda hanya bisa melihat presensi ini."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // Pencarian lintas kelompok di ringkasan; di daftar satu kelompok cukup pencarian lokal.
                        if (!single && view !is PresensiView.Kelompok) item {
                            SearchPill(search, model::search, "Cari nama di semua kelompok")
                        }

                        if (view == PresensiView.Summary) {
                            if (a.summary.isEmpty()) item {
                                Text("Tidak ada peserta sasaran yang terlihat oleh Anda.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val byDesa = a.summary.groupBy { it.desaId }
                            val multiDesa = byDesa.size > 1
                            byDesa.forEach { (desaId, items) ->
                                item(key = "desa:$desaId") {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (multiDesa) {
                                            val t = KelompokAttendance.sum(items)
                                            Row(Modifier.padding(horizontal = 4.dp)) {
                                                Overline("Desa ${items.first().desa}", Modifier.weight(1f))
                                                Text("${t.present.formatId()}/${t.total.formatId()} · ${Math.round(t.rate * 100)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                                            Column {
                                                items.forEachIndexed { i, k ->
                                                    if (i > 0) RowDivider()
                                                    KelompokRow(k) { model.open(PresensiView.Kelompok(k.kelompokId)) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            if (selected != null && !single) item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CompositionBar(selected)
                                    Text(
                                        AttendanceStatus.entries.joinToString(" · ") { "${it.label} ${selected.count(it)}" } + " · Belum ${selected.belum}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (rows.isNotEmpty()) item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SearchPill(localQuery, { localQuery = it }, "Cari nama")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        FilterChip(
                                            selected = onlyEmpty,
                                            onClick = { onlyEmpty = !onlyEmpty },
                                            label = { Text("Hanya yang belum diisi") },
                                            leadingIcon = if (onlyEmpty) { { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null,
                                            shape = CircleShape,
                                        )
                                    }
                                    if (writable.any { current(it) == null }) {
                                        SecondaryButton("Hadirkan semua yang belum diisi", onClick = { confirmBulk = true }, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                            when {
                                roster.data == null && roster.error != null -> item { ErrorBox(roster.error!!, { model.loadRoster(view) }) }
                                roster.data == null -> item { LoadingBox() }
                                rows.isEmpty() -> item {
                                    Text(
                                        if (showGroup) "Tidak ada peserta dengan nama itu." else "Tidak ada peserta sasaran yang terlihat oleh Anda.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                visible.isEmpty() -> item {
                                    Text("Tidak ada peserta yang cocok.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            groups.forEach { (group, items) ->
                                if (group.isNotEmpty()) item(key = "g:$group") { Overline(group, Modifier.padding(start = 4.dp, top = 4.dp)) }
                                item(key = "list:$group") {
                                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                                        Column {
                                            items.forEachIndexed { i, r ->
                                                if (i > 0) RowDivider()
                                                ChecklistRow(r, current(r), queuedMap[r.generusId], enabled = r.canWrite) { s ->
                                                    model.record(listOf(AttendanceEntry(a.id, r.generusId, s)))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (view is PresensiView.Search && rows.size >= SEARCH_LIMIT) item {
                                Text("Menampilkan $SEARCH_LIMIT hasil pertama. Perjelas nama yang dicari, atau buka per kelompok.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (rows.isNotEmpty()) item {
                                Text(
                                    "H = Hadir · T = Terlambat · I = Izin · S = Sakit · A = Alpa. Ketukan langsung tersimpan, juga saat sinyal putus.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (a.canManage && !cancelled) item {
                            ManageCard(
                                a = a,
                                now = now,
                                busy = busy,
                                scanOpen = scanOpen,
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
                        "Peserta yang belum tercatat menjadi alpa. Setelah dikunci, pengelola agenda masih bisa mengoreksi; setiap perubahan tercatat.",
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

/** Titik hijau berdenyut + waktu pembaruan terakhir (padanan LiveRefresh di PWA). */
@Composable
private fun LiveIndicator(at: Instant?) {
    val pulse = rememberInfiniteTransition(label = "live")
    val scale by pulse.animateFloat(1f, 2.2f, infiniteRepeatable(tween(1000), RepeatMode.Restart), label = "ping")
    val alpha by pulse.animateFloat(0.6f, 0f, infiniteRepeatable(tween(1000), RepeatMode.Restart), label = "fade")
    val green = GenerusTheme.extra.hadir.solid
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }.clip(CircleShape).background(green))
            Box(Modifier.size(8.dp).clip(CircleShape).background(green))
        }
        Text(
            "Langsung · diperbarui tiap 15 detik" + (at?.let { " (terakhir ${java.time.format.DateTimeFormatter.ofPattern("HH.mm.ss").withZone(java.time.ZoneId.systemDefault()).format(it)})" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Kotak angka: Peserta + lima status (3 kolom). */
@Composable
private fun StatGrid(sum: KelompokAttendance) {
    val x = GenerusTheme.extra
    val cells = listOf(Triple("Peserta", sum.total, null as AttendanceStatus?)) + AttendanceStatus.entries.map { Triple(it.label, sum.count(it), it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value, s) ->
                    val tone = s?.let { x.of(it) }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = tone?.soft ?: MaterialTheme.colorScheme.surface,
                        shadowElevation = if (tone == null) 1.dp else 0.dp,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = tone?.solid ?: MaterialTheme.colorScheme.onSurface)
                            Text(value.formatId(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = tone?.solid ?: MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

/** Batang komposisi status; sisa (abu-abu) = belum diisi. */
@Composable
private fun CompositionBar(s: KelompokAttendance) {
    val x = GenerusTheme.extra
    val desc = AttendanceStatus.entries.joinToString(", ") { "${it.label} ${s.count(it)}" } + ", belum ${s.belum}"
    Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(x.track).semantics { contentDescription = desc }) {
        if (s.total > 0) AttendanceStatus.entries.forEach { st ->
            val n = s.count(st)
            if (n > 0) Box(Modifier.fillMaxHeight().weight(n.toFloat()).background(x.of(st).solid))
        }
        if (s.total > 0 && s.belum > 0) Spacer(Modifier.weight(s.belum.toFloat()))
    }
}

@Composable
private fun KelompokRow(k: KelompokAttendance, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(k.kelompok, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${k.present.formatId()}/${k.total.formatId()} · ${Math.round(k.rate * 100)}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CompositionBar(k)
            if (k.belum > 0) Text("${k.belum} belum diisi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
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
    scanOpen: Boolean,
    onCancel: () -> Unit,
    onLock: () -> Unit,
    onAddScanner: () -> Unit,
    onRemoveScanner: (String) -> Unit,
) {
    if (scanOpen) {
        AppCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Petugas scan", style = MaterialTheme.typography.titleMedium)
                    Text("Akses pemindai sementara untuk agenda ini, sampai ${a.delegateUntil.formatDateTime()}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onAddScanner) { Icon(Icons.Rounded.PersonAdd, contentDescription = "Tambah petugas scan", tint = MaterialTheme.colorScheme.primary) }
            }
            if (a.scanners.isEmpty()) {
                Text("Belum ada petugas. Pengelola agenda selalu bisa memindai.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            a.scanners.forEach { s ->
                val expired = s.expiresAt.isBefore(now)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (expired) TextDecoration.LineThrough else null,
                        )
                        Text("${s.email ?: ""} · s.d. ${s.expiresAt.formatDateTime()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemoveScanner(s.userId) }) { Icon(Icons.Rounded.PersonRemove, contentDescription = "Cabut akses ${s.name}", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    AppCard(Modifier.fillMaxWidth()) {
        Text("Kelola agenda", style = MaterialTheme.typography.titleMedium)
        if (a.lockedAt == null) {
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
        } else {
            Text(
                "Dikunci ${a.lockedAt.formatDateTime()}. Anda masih bisa mengoreksi presensi; setiap perubahan tercatat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
