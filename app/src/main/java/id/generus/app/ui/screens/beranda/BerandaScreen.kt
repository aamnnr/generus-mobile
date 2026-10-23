package id.generus.app.ui.screens.beranda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.currentMonth
import id.generus.app.core.dateRange
import id.generus.app.core.formatDateLong
import id.generus.app.core.formatDateTime
import id.generus.app.core.formatId
import id.generus.app.core.formatTime
import id.generus.app.core.label
import id.generus.app.core.percent
import id.generus.app.core.today
import id.generus.app.core.zoneLabel
import id.generus.app.data.model.AppRole
import id.generus.app.data.model.Beranda
import id.generus.app.data.model.HeroAgenda
import id.generus.app.data.model.Me
import id.generus.app.data.model.Period
import id.generus.app.data.model.RekapUnit
import id.generus.app.data.model.UnitLevel
import id.generus.app.data.model.canReadGenerus
import id.generus.app.data.model.canWriteGenerus
import id.generus.app.data.model.initials
import id.generus.app.data.model.kurikulumEnabled
import id.generus.app.data.model.label
import id.generus.app.data.model.rekapRootUnit
import id.generus.app.data.pickActivePeriod
import id.generus.app.ui.CacheSpec
import id.generus.app.ui.LoadVm
import id.generus.app.ui.components.AppCard
import id.generus.app.ui.components.Avatar
import id.generus.app.ui.components.BannerTone
import id.generus.app.ui.components.EightStar
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.IconTile
import id.generus.app.ui.components.InfoBanner
import id.generus.app.ui.components.KeyValue
import id.generus.app.ui.components.MosqueSilhouette
import id.generus.app.ui.components.OfflineStrip
import id.generus.app.ui.components.Overline
import id.generus.app.ui.components.Pill
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.ProgressBar
import id.generus.app.ui.components.SkeletonBlock
import id.generus.app.ui.components.StatBox
import id.generus.app.ui.nav.AgendaDetail
import id.generus.app.ui.nav.AgendaForm
import id.generus.app.ui.nav.AgendaList
import id.generus.app.ui.nav.CapaianHome
import id.generus.app.ui.nav.GenerusList
import id.generus.app.ui.nav.Kartu
import id.generus.app.ui.nav.Kenaikan
import id.generus.app.ui.nav.Lainnya
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.nav.RekapCapaian
import id.generus.app.ui.nav.Scanner
import id.generus.app.ui.nav.navigateTab
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.vm
import java.time.Instant
import kotlinx.coroutines.launch

@kotlinx.serialization.Serializable
data class BerandaData(
    val beranda: Beranda?,
    val period: Period?,
    val rekap: RekapUnit?,
    /** Data dari cache karena offline. */
    val offline: Boolean = false,
)

class BerandaVm(c: AppContainer, private val me: Me) : LoadVm<BerandaData>(c, CacheSpec("beranda-${me.id}", BerandaData.serializer())) {
    init {
        load()
        viewModelScope.launch { c.presensi.synced.collect { load() } }
    }

    override suspend fun fetch(): BerandaData {
        runCatching { c.agenda.lockDue() }
        val beranda = c.agenda.beranda()
        if (!kurikulumEnabled) return BerandaData(beranda, null, null)
        val period = pickActivePeriod(c.kurikulum.periods(), today())
        // Rekap capaian unit tertinggi user (atau kelompoknya), semua jenjang.
        val unit = me.rekapRootUnit()?.unitId ?: me.roles.firstOrNull { it.level == UnitLevel.kelompok && it.role != AppRole.kurikulum }?.unitId
        val rekap = if (period != null && unit != null) runCatching { c.capaian.rekapUnits(period.id, null, listOf(unit)).firstOrNull() }.getOrNull() else null
        return BerandaData(beranda, period, rekap)
    }
}

private data class Tile(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** Beranda (DESIGN.md §8.1): sapaan, hero agenda, ubin aksi cepat, ringkasan bulan, target periode, statistik. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BerandaScreen(nav: NavHostController) {
    val me = LocalMe.current
    val model = vm { BerandaVm(it, me) }
    val state by model.state.collectAsStateWithLifecycle()
    val stale by model.stale.collectAsStateWithLifecycle()
    val online by id.generus.app.ui.appContainer().network.online.collectAsStateWithLifecycle()
    val offline = stale || !online

    val reader = me.canReadGenerus
    val writer = me.canWriteGenerus
    val data = state.data
    val hero = data?.beranda?.hero
    val scanTasks = data?.beranda?.scanTasks.orEmpty()

    val tiles = buildList {
        if (writer || scanTasks.isNotEmpty()) {
            val scanTarget = when {
                hero != null && hero.ongoing() && (hero.canManage || hero.isScanner) -> hero.id
                scanTasks.isNotEmpty() -> scanTasks.first().id
                else -> null
            }
            add(Tile("Scan QR", Icons.Rounded.QrCodeScanner) { if (scanTarget != null) nav.navigate(Scanner(scanTarget)) else nav.navigateTab(AgendaList) })
        }
        if (reader) {
            add(Tile("Presensi", Icons.Rounded.Checklist) { if (hero != null) nav.navigate(AgendaDetail(hero.id)) else nav.navigateTab(AgendaList) })
            add(Tile("Generus", Icons.Rounded.Groups) { nav.navigateTab(GenerusList) })
        }
        if (writer) add(Tile("Kartu QR", Icons.Rounded.Badge) { nav.navigate(Kartu()) })
        else if (reader) add(Tile("Agenda", Icons.Rounded.CalendarMonth) { nav.navigateTab(AgendaList) })
    }

    PullToRefreshBox(isRefreshing = state.loading && data != null, onRefresh = model::load, modifier = Modifier.statusBarsPadding()) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Greeting(me) { nav.navigateTab(Lainnya) } }
            if (offline) item { OfflineStrip("Anda sedang offline. Data mungkin belum terbaru.") }
            if (writer && today().monthValue in 7..8) {
                item {
                    InfoBanner("Awal tahun ajaran: periksa generus yang lulus SLTP atau selesai sekolah di Kenaikan jenjang.", tone = BannerTone.Info) {
                        Text("Buka", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable { nav.navigate(Kenaikan) })
                    }
                }
            }
            if (scanTasks.isNotEmpty()) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Text("Tugas scan", style = MaterialTheme.typography.titleMedium)
                        Text("Anda ditunjuk sebagai petugas pemindai QR.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        scanTasks.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, style = MaterialTheme.typography.titleSmall)
                                    Text("${t.unitName} · ${t.startsAt.formatDateTime()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                PrimaryButton("Pindai", onClick = { nav.navigate(Scanner(t.id)) }, icon = Icons.Rounded.QrCodeScanner)
                            }
                        }
                    }
                }
            }
            when {
                data == null && state.error != null -> item { ErrorBox(state.error!!, model::load) }
                data == null -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SkeletonBlock(height = 200.dp, radius = 20.dp)
                        SkeletonBlock(height = 88.dp)
                        SkeletonBlock(height = 140.dp)
                    }
                }
                else -> {
                    if (reader) item { HeroCard(hero, writer, onOpen = { nav.navigate(AgendaDetail(it)) }, onScan = { nav.navigate(Scanner(it)) }, onCreate = { nav.navigate(AgendaForm()) }) }
                    if (tiles.isNotEmpty()) item { QuickTiles(tiles) }
                    if (reader && data.beranda != null) item { MonthCard(data.beranda) { nav.navigateTab(AgendaList) } }
                    if (data.period != null) {
                        item {
                            PeriodCard(data.period, data.rekap) {
                                if (me.rekapRootUnit() != null) nav.navigate(RekapCapaian(periodId = data.period.id)) else nav.navigateTab(CapaianHome)
                            }
                        }
                    }
                    if (reader && data.beranda != null) {
                        item {
                            val counts = data.beranda.counts
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatBox("Generus aktif", counts.total.formatId(), Modifier.weight(1f))
                                StatBox("Pra Remaja", counts.praRemaja.formatId(), Modifier.weight(1f))
                                StatBox("Remaja", counts.remaja.formatId(), Modifier.weight(1f))
                            }
                        }
                    }
                    if (writer) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                EightStar(MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(
                                    "Kartu QR dipakai pada pengajian desa & daerah. Kartu hilang? Terbitkan ulang dari halaman generus lalu cetak.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun HeroAgenda.ongoing(now: Instant = Instant.now()): Boolean = !startsAt.isAfter(now)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Greeting(me: Me, onAvatar: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text("Assalamu'alaikum,", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(me.name, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (me.roles.isEmpty()) Text("Belum punya peran.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                me.roles.forEach { Pill("${it.label()} · ${it.unitName}") }
            }
        }
        Box(Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onAvatar)) { Avatar(initials(me.name)) }
    }
}

/** Kartu hero (DESIGN.md §7.2): agenda berlangsung / berikutnya. Maksimal satu per layar. */
@Composable
private fun HeroCard(hero: HeroAgenda?, writer: Boolean, onOpen: (String) -> Unit, onScan: (String) -> Unit, onCreate: () -> Unit) {
    val x = GenerusTheme.extra
    val white = x.heroForeground
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(x.hero, id.generus.app.ui.theme.Palette.Green900))),
    ) {
        MosqueSilhouette(white.copy(alpha = 0.10f), Modifier.align(Alignment.TopEnd).padding(top = 8.dp).width(190.dp).height(114.dp))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hero == null) {
                Overline("Agenda", color = white.copy(alpha = 0.8f))
                Text("Belum ada agenda mendatang.", style = MaterialTheme.typography.titleMedium, color = white)
                if (writer) HeroButton("Buat agenda", onCreate)
                return@Column
            }
            val ongoing = hero.ongoing()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ongoing) Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF9FD8B8)))
                Overline("${if (ongoing) "Sedang berlangsung" else "Agenda berikutnya"} · ${hero.level.label}", color = white.copy(alpha = 0.8f))
            }
            Column {
                Text(hero.title, style = MaterialTheme.typography.titleMedium, color = white, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${hero.startsAt.formatDateLong()} · ${hero.startsAt.formatTime()}–${hero.endsAt.formatTime()} ${zoneLabel()}" + (hero.location?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = white.copy(alpha = 0.8f),
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text((if (ongoing) hero.present else hero.targets).formatId(), style = MaterialTheme.typography.headlineLarge, color = white)
                Text(
                    if (ongoing) "/ ${hero.targets.formatId()} tercatat hadir" else "peserta sasaran",
                    style = MaterialTheme.typography.bodyMedium,
                    color = white.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (ongoing) ProgressBar(if (hero.targets > 0) hero.present.toFloat() / hero.targets else 0f, height = 6.dp, color = white, track = white.copy(alpha = 0.2f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroButton(if (ongoing) "Buka presensi" else "Lihat agenda", { onOpen(hero.id) }, Modifier.weight(1f))
                if (ongoing && (hero.canManage || hero.isScanner)) {
                    Row(
                        Modifier.heightIn(min = 44.dp).clip(CircleShape).border(1.dp, white.copy(alpha = 0.5f), CircleShape)
                            .clickable(role = Role.Button) { onScan(hero.id) }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = white, modifier = Modifier.size(18.dp))
                        Text("Scan QR", style = MaterialTheme.typography.labelLarge, color = white)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 44.dp).clip(CircleShape).background(Color.White).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = GenerusTheme.extra.hero)
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = GenerusTheme.extra.hero, modifier = Modifier.size(18.dp))
    }
}

/** Ubin aksi cepat (DESIGN.md §7.3). */
@Composable
private fun QuickTiles(tiles: List<Tile>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.take(4).forEach { t ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = t.onClick).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconTile(t.icon, size = 56.dp)
                Text(t.label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MonthCard(b: Beranda, onAgenda: () -> Unit) {
    val rate = b.month.rate
    AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(currentMonth().label(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("Lihat agenda", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(role = Role.Button, onClick = onAgenda))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text("Kehadiran", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(rate?.let { "${Math.round(it * 100)}%" } ?: "—", style = MaterialTheme.typography.titleSmall)
            }
            ProgressBar((rate ?: 0.0).toFloat())
            Text("${b.month.locked} dari ${b.month.agendas} agenda sudah dikunci", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        KeyValue("Agenda bulan ini", b.month.agendas.formatId())
        KeyValue("Alpa 3× berturut-turut", "${b.alpaStreak} generus", valueColor = if (b.alpaStreak > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PeriodCard(period: Period, rekap: RekapUnit?, onOpen: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(Icons.AutoMirrored.Rounded.MenuBook)
            Column(Modifier.weight(1f)) {
                Text("Target Periode ${period.name}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${dateRange(period.startsOn, period.endsOn)} · ${period.targetCount} target dari Tim Kurikulum PPG",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (rekap != null && rekap.slots + rekap.deliverable > 0) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RekapStat("Capaian per generus", percent(rekap.tuntas, rekap.slots), Modifier.weight(1f))
                RekapStat("Materi disampaikan", percent(rekap.delivered, rekap.deliverable), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RekapStat(label: String, pct: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$pct%", style = MaterialTheme.typography.titleLarge)
        ProgressBar(pct / 100f, height = 6.dp, color = GenerusTheme.extra.hadir.solid)
    }
}
