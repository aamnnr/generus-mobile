package id.generus.app.ui.screens.generus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import id.generus.app.core.Transition
import id.generus.app.core.ageOn
import id.generus.app.core.academicYearStart
import id.generus.app.core.formatShort
import id.generus.app.core.isSuggested
import id.generus.app.core.today
import id.generus.app.data.model.PromotionPage
import id.generus.app.data.model.ScopeUnit
import id.generus.app.data.model.UnitLevel
import id.generus.app.ui.LoadVm
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.BannerTone
import id.generus.app.ui.components.CheckRow
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.GhostButton
import id.generus.app.ui.components.InfoBanner
import id.generus.app.ui.components.LoadingBox
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.RefreshableList
import id.generus.app.ui.components.SelectField
import id.generus.app.ui.components.SingleChips
import id.generus.app.ui.components.StickyBottom
import id.generus.app.ui.vm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KenaikanVm(c: AppContainer) : LoadVm<PromotionPage?>(c) {
    private val _units = MutableStateFlow<List<ScopeUnit>>(emptyList())
    val units: StateFlow<List<ScopeUnit>> = _units.asStateFlow()
    val transition = MutableStateFlow(Transition.PraKePelajar)
    val unit = MutableStateFlow<ScopeUnit?>(null)

    init {
        viewModelScope.launch {
            runCatching { c.generus.scopeUnits("read") }.onSuccess { list ->
                _units.value = list
                unit.value = list.firstOrNull { it.level == UnitLevel.desa } ?: list.firstOrNull()
                load()
            }.onFailure { load() }
        }
    }

    override suspend fun fetch(): PromotionPage? {
        val u = unit.value ?: return null
        val t = transition.value
        return c.generus.promotionCandidates(t.from.jenjang, t.from.kategori, u.id)
    }

    fun promote(ids: List<String>, onError: (String) -> Unit, onOk: (String) -> Unit) = run(onError, { load() }) {
        val t = transition.value
        val n = c.generus.promote(t.from.jenjang, t.from.kategori, t.to.jenjang, t.to.kategori, ids)
        val skipped = ids.size - n
        onOk("$n generus dinaikkan ke ${t.to.jenjang} ${t.to.kategori}." + if (skipped > 0) " $skipped dilewati (sudah berubah atau di luar akses Anda)." else "")
    }
}

/** Kenaikan jenjang/kategori massal (padanan /generus/kenaikan). Saran dicentang otomatis; admin memutuskan. */
@Composable
fun KenaikanScreen(nav: NavHostController) {
    val toast = LocalToast.current
    val model = vm { KenaikanVm(it) }
    val state by model.state.collectAsStateWithLifecycle()
    val units by model.units.collectAsStateWithLifecycle()
    val transition by model.transition.collectAsStateWithLifecycle()
    val unit by model.unit.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val today = today()
    val page = state.data
    var checked by remember(page) {
        mutableStateOf(page?.rows.orEmpty().filter { isSuggested(transition, it.birthDate, it.school, today) }.map { it.id }.toSet())
    }

    AppScreen(
        "Kenaikan jenjang",
        onBack = { nav.popBackStack() },
        bottomBar = {
            if (!page?.rows.isNullOrEmpty()) StickyBottom {
                PrimaryButton(
                    "Naikkan ${checked.size} generus",
                    onClick = { model.promote(checked.toList(), { toast(it) }) { toast(it) } },
                    enabled = checked.isNotEmpty(),
                    loading = busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { p ->
        RefreshableList(refreshing = state.loading && page != null, onRefresh = model::load, padding = p) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SingleChips(Transition.entries, transition, { it.label }, { model.transition.value = it; model.load() })
                    Text(
                        "${transition.hint} Saran (dicentang otomatis) berdasarkan usia pada awal tahun ajaran ${academicYearStart(today).formatShort()} dan isian sekolah/kelas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectField(
                        "Wilayah",
                        units,
                        unit,
                        { if (it.level == UnitLevel.desa) "Seluruh Desa ${it.name}" else "Kelompok ${it.name}" },
                        { model.unit.value = it; model.load() },
                        group = { "Desa ${it.desaName}" },
                    )
                }
            }
            when {
                state.error != null && page == null -> item { ErrorBox(state.error!!, model::load) }
                page == null && state.loading -> item { LoadingBox() }
                page == null -> item { EmptyState("Pilih wilayah") }
                page.rows.isEmpty() -> item { EmptyState("Tidak ada generus ${transition.from.jenjang}${transition.from.kategori?.let { " $it" } ?: ""} aktif di wilayah ini") }
                else -> {
                    if (page.truncated) item { InfoBanner("Hanya 500 generus pertama yang ditampilkan; pilih wilayah lebih kecil.", tone = BannerTone.Warning) }
                    item {
                        Row {
                            GhostButton("Centang semua", onClick = { checked = page.rows.map { it.id }.toSet() })
                            GhostButton("Kosongkan", onClick = { checked = emptySet() })
                        }
                    }
                    items(page.rows, key = { it.id }) { g ->
                        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                            CheckRow(
                                g.name,
                                g.id in checked,
                                { on -> checked = if (on) checked + g.id else checked - g.id },
                                meta = "${g.gender.name} · ${ageOn(g.birthDate)} th · ${g.school ?: "sekolah belum diisi"} · ${g.kelompok}" +
                                    if (isSuggested(transition, g.birthDate, g.school, today)) " · disarankan" else "",
                            )
                        }
                    }
                }
            }
        }
    }
}
