package id.generus.app.ui.screens.generus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.ageOn
import id.generus.app.core.formatId
import id.generus.app.data.GenerusFilters
import id.generus.app.data.JenjangFilter
import id.generus.app.data.model.GenerusListItem
import id.generus.app.data.model.GenerusPage
import id.generus.app.data.model.GenerusStatus
import id.generus.app.data.model.ScopeUnit
import id.generus.app.data.model.UnitLevel
import id.generus.app.data.model.canWriteGenerus
import id.generus.app.data.model.initials
import id.generus.app.data.model.jenjangLabel
import id.generus.app.data.model.label
import id.generus.app.ui.LoadVm
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.Avatar
import id.generus.app.ui.components.BottomSheet
import id.generus.app.ui.components.ChipRow
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.ListCard
import id.generus.app.ui.components.ListRow
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.RefreshableList
import id.generus.app.ui.components.SearchPill
import id.generus.app.ui.components.SecondaryButton
import id.generus.app.ui.components.SelectField
import id.generus.app.ui.components.SkeletonBlock
import id.generus.app.ui.components.StatusBadge
import id.generus.app.ui.nav.GenerusDetail
import id.generus.app.ui.nav.GenerusForm
import id.generus.app.ui.nav.Kartu
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.theme.StatusColor
import id.generus.app.ui.vm
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GenerusListVm(c: AppContainer) : LoadVm<GenerusPage>(c) {
    private val _filters = MutableStateFlow(GenerusFilters())
    val filters: StateFlow<GenerusFilters> = _filters.asStateFlow()
    private val _units = MutableStateFlow<List<ScopeUnit>>(emptyList())
    val units: StateFlow<List<ScopeUnit>> = _units.asStateFlow()
    private var page = 1
    private var searchJob: Job? = null

    init {
        load()
        viewModelScope.launch { runCatching { _units.value = c.generus.scopeUnits("read") } }
    }

    override suspend fun fetch(): GenerusPage {
        page = 1
        return c.generus.list(_filters.value, 1)
    }

    fun update(f: GenerusFilters, debounce: Boolean = false) {
        _filters.value = f
        searchJob?.cancel()
        if (!debounce) return load()
        searchJob = viewModelScope.launch {
            delay(350)
            load()
        }
    }

    fun loadMore(onError: (String) -> Unit) = run(onError) {
        val next = c.generus.list(_filters.value, page + 1)
        page += 1
        setData { it.copy(rows = it.rows + next.rows) }
    }
}

/** Daftar generus (padanan /generus): cari, chip jenjang, filter wilayah & status. */
@Composable
fun GenerusListScreen(nav: NavHostController) {
    val me = LocalMe.current
    val toast = LocalToast.current
    val model = vm { GenerusListVm(it) }
    val state by model.state.collectAsStateWithLifecycle()
    val filters by model.filters.collectAsStateWithLifecycle()
    val units by model.units.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var showFilter by remember { mutableStateOf(false) }
    val activeFilters = (if (filters.unitId != null) 1 else 0) + (if (filters.status != GenerusStatus.aktif) 1 else 0)

    AppScreen(
        "Generus",
        actions = {
            if (me.canWriteGenerus) IconButton(onClick = { nav.navigate(Kartu()) }) { Icon(Icons.Rounded.Badge, contentDescription = "Kartu QR") }
            IconButton(onClick = { showFilter = true }) {
                BadgedBox(badge = { if (activeFilters > 0) Badge { Text("$activeFilters") } }) {
                    Icon(Icons.Rounded.FilterList, contentDescription = "Filter wilayah & status")
                }
            }
        },
        floatingActionButton = {
            if (me.canWriteGenerus) {
                FloatingActionButton(onClick = { nav.navigate(GenerusForm()) }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Icon(Icons.Rounded.Add, contentDescription = "Tambah generus")
                }
            }
        },
    ) { p ->
        RefreshableList(refreshing = state.loading && state.data != null, onRefresh = model::load, padding = p) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SearchPill(filters.q, { model.update(filters.copy(q = it), debounce = true) }, "Cari nama generus")
                    ChipRow(listOf<JenjangFilter?>(null) + JenjangFilter.entries, filters.jenjang, { it?.label?.removeSuffix(" (semua)") ?: "Semua" }, { model.update(filters.copy(jenjang = it)) })
                }
            }
            val data = state.data
            when {
                data == null && state.error != null -> item { ErrorBox(state.error!!, model::load) }
                data == null -> items(6) { SkeletonBlock(height = 64.dp) }
                data.rows.isEmpty() -> item {
                    EmptyState(
                        if (filters.q.isBlank() && filters.jenjang == null) "Belum ada generus" else "Tidak ada generus yang cocok",
                        body = if (me.canWriteGenerus && filters.q.isBlank()) "Tambahkan satu per satu atau import dari template Excel." else null,
                        action = if (me.canWriteGenerus && filters.q.isBlank()) ({ PrimaryButton("Tambah generus", onClick = { nav.navigate(GenerusForm()) }) }) else null,
                    )
                }
                else -> {
                    item {
                        Text("${data.total.formatId()} generus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        ListCard {
                            data.rows.forEachIndexed { i, g ->
                                if (i > 0) id.generus.app.ui.components.RowDivider()
                                GenerusRow(g) { nav.navigate(GenerusDetail(g.id)) }
                            }
                        }
                    }
                    if (data.rows.size < data.total) item {
                        SecondaryButton("Muat lebih banyak", onClick = { model.loadMore { toast(it) } }, loading = busy, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (showFilter) {
        FilterSheet(filters, units, onDismiss = { showFilter = false }) { model.update(it); showFilter = false }
    }
}

@Composable
private fun GenerusRow(g: GenerusListItem, onClick: () -> Unit) {
    ListRow(
        g.name,
        meta = "${g.gender.name} · ${ageOn(g.birthDate)} th · ${jenjangLabel(g.jenjang, g.kategori)} · ${g.kelompok}",
        leading = { Avatar(initials(g.name), size = 40.dp) },
        trailing = {
            if (g.status != GenerusStatus.aktif) StatusBadge(g.status.label, StatusColor(MaterialTheme.colorScheme.onSurfaceVariant, GenerusTheme.extra.tile))
        },
        onClick = onClick,
    )
}

@Composable
private fun FilterSheet(current: GenerusFilters, units: List<ScopeUnit>, onDismiss: () -> Unit, onApply: (GenerusFilters) -> Unit) {
    var unit by remember { mutableStateOf(units.firstOrNull { it.id == current.unitId }) }
    var status by remember { mutableStateOf(current.status) }
    BottomSheet("Filter", onDismiss) {
        SelectField(
            "Wilayah",
            listOf<ScopeUnit?>(null) + units,
            unit,
            { u -> when { u == null -> "Semua wilayah saya"; u.level == UnitLevel.desa -> "Seluruh Desa ${u.name}"; else -> "Kelompok ${u.name}" } },
            { unit = it },
            group = { it?.let { u -> "Desa ${u.desaName}" } },
        )
        SelectField(
            "Status",
            listOf<GenerusStatus?>(GenerusStatus.aktif, GenerusStatus.pindah_sambung, GenerusStatus.menikah, null),
            status,
            { it?.label ?: "Semua status" },
            { status = it },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Atur ulang", onClick = { onApply(current.copy(unitId = null, status = GenerusStatus.aktif)) }, modifier = Modifier.weight(1f))
            PrimaryButton("Terapkan", onClick = { onApply(current.copy(unitId = unit?.id, status = status)) }, modifier = Modifier.weight(1f))
        }
    }
}
