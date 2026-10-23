package id.generus.app.ui.screens.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.APP_ZONE
import id.generus.app.core.ID_LOCALE
import id.generus.app.core.formatTime
import id.generus.app.core.zoneLabel
import id.generus.app.data.model.AgendaListItem
import id.generus.app.data.model.AgendaPage
import id.generus.app.data.model.canWriteGenerus
import id.generus.app.data.model.label
import id.generus.app.ui.CacheSpec
import id.generus.app.ui.LoadVm
import id.generus.app.ui.components.AppCard
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.ChipRow
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.OfflineStrip
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.RefreshableList
import id.generus.app.ui.components.SecondaryButton
import id.generus.app.ui.components.SkeletonBlock
import id.generus.app.ui.components.StatusBadge
import id.generus.app.ui.nav.AgendaDetail
import id.generus.app.ui.nav.AgendaForm
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.theme.StatusColor
import id.generus.app.ui.vm
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

enum class AgendaRange(val key: String, val label: String) { Mendatang("mendatang", "Mendatang"), Selesai("selesai", "Selesai") }

class AgendaListVm(c: AppContainer, private val range: AgendaRange, uid: String) :
    LoadVm<AgendaPage>(c, CacheSpec("agenda-${range.key}-$uid", AgendaPage.serializer())) {
    private var page = 1

    init {
        load()
        viewModelScope.launch { c.presensi.synced.collect { load() } }
    }

    override suspend fun fetch(): AgendaPage {
        page = 1
        runCatching { c.agenda.lockDue() }
        return c.agenda.list(range.key, 1)
    }

    fun loadMore(onError: (String) -> Unit) = run(onError) {
        val next = c.agenda.list(range.key, page + 1)
        page += 1
        setData { it.copy(rows = it.rows + next.rows, hasMore = next.hasMore) }
    }
}

/** Daftar agenda: mendatang (urut terdekat) atau selesai (urut terbaru). Padanan /agenda. */
@Composable
fun AgendaListScreen(nav: NavHostController) {
    val me = LocalMe.current
    var range by rememberSaveable { mutableStateOf(AgendaRange.Mendatang) }
    val model = vm(key = "agenda-${range.key}") { AgendaListVm(it, range, me.id) }
    val state by model.state.collectAsStateWithLifecycle()
    val stale by model.stale.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val toast = id.generus.app.ui.components.LocalToast.current

    AppScreen(
        "Agenda",
        floatingActionButton = {
            if (me.canWriteGenerus) {
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate(AgendaForm()) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Buat agenda") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { p ->
        RefreshableList(refreshing = state.loading && state.data != null, onRefresh = model::load, padding = p) {
            item { ChipRow(AgendaRange.entries, range, { it.label }, { range = it }) }
            if (stale) item { OfflineStrip("Offline: menampilkan data terakhir yang tersimpan.") }
            val data = state.data
            when {
                data == null && state.error != null -> item { ErrorBox(state.error!!, model::load) }
                data == null -> items(5) { SkeletonBlock(height = 84.dp) }
                data.rows.isEmpty() -> item {
                    EmptyState(
                        if (range == AgendaRange.Mendatang) "Belum ada agenda mendatang" else "Belum ada agenda yang selesai",
                        body = if (me.canWriteGenerus && range == AgendaRange.Mendatang) "Buat agenda pengajian untuk unit Anda." else null,
                        action = if (me.canWriteGenerus && range == AgendaRange.Mendatang) ({ PrimaryButton("Buat agenda", onClick = { nav.navigate(AgendaForm()) }) }) else null,
                    )
                }
                else -> {
                    items(data.rows, key = { it.id }) { a -> AgendaRow(a) { nav.navigate(AgendaDetail(a.id)) } }
                    if (data.hasMore) item {
                        SecondaryButton("Muat lebih banyak", onClick = { model.loadMore { toast(it) } }, loading = busy, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private val DAY_NUM = DateTimeFormatter.ofPattern("d", ID_LOCALE)
private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM", ID_LOCALE)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", ID_LOCALE)

@Composable
fun agendaBadge(a: AgendaListItem, now: Instant = Instant.now()): Pair<String, StatusColor>? {
    val x = GenerusTheme.extra
    return when {
        a.cancelledAt != null -> "Dibatalkan" to x.alpa
        a.lockedAt != null -> "Terkunci" to StatusColor(MaterialTheme.colorScheme.onSurfaceVariant, x.tile)
        !a.startsAt.isAfter(now) && a.endsAt.isAfter(now) -> "Berlangsung" to x.hadir
        else -> null
    }
}

@Composable
private fun AgendaRow(a: AgendaListItem, onClick: () -> Unit) {
    val z = a.startsAt.atZone(APP_ZONE)
    AppCard(Modifier.fillMaxWidth(), onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier.width(52.dp).clip(RoundedCornerShape(12.dp)).background(GenerusTheme.extra.tile).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(z.format(DAY_NUM), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(z.format(MONTH_SHORT).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    a.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (a.cancelledAt != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${z.format(WEEKDAY)} · ${a.startsAt.formatTime()}–${a.endsAt.formatTime()} ${zoneLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${a.level.label} ${a.unitName}" + (a.location?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            agendaBadge(a)?.let { (text, color) -> StatusBadge(text, color) }
        }
    }
}
