package id.generus.app.ui.screens.generus

import id.generus.app.ui.components.SelectField
import id.generus.app.core.segmentLabel
import id.generus.app.ui.components.UnitPickerField
import id.generus.app.data.model.TreeUnit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import id.generus.app.core.ageAt
import id.generus.app.core.Suggestion
import id.generus.app.core.isPromotionSeason
import id.generus.app.core.suggestPromotion
import id.generus.app.core.academicYearStart
import id.generus.app.core.today
import id.generus.app.data.model.PromotionCandidate
import id.generus.app.data.model.PromotionPage
import id.generus.app.data.model.ScopeUnit
import id.generus.app.data.model.UnitLevel
import id.generus.app.ui.LoadVm
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.ConfirmSheet
import id.generus.app.ui.components.Pill
import id.generus.app.ui.components.SearchPill
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.GhostButton
import id.generus.app.ui.components.LoadingBox
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.RefreshableList
import id.generus.app.ui.components.StickyBottom
import id.generus.app.ui.vm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KenaikanVm(c: AppContainer) : LoadVm<PromotionPage?>(c) {
    private val _units = MutableStateFlow<List<TreeUnit>>(emptyList())
    val units: StateFlow<List<TreeUnit>> = _units.asStateFlow()
    val transition = MutableStateFlow(Transition.PraKePelajar)
    val unit = MutableStateFlow<ScopeUnit?>(null)
    private var scope: List<ScopeUnit> = emptyList()

    fun selectUnit(id: String?) {
        unit.value = scope.firstOrNull { it.id == id } ?: return
        load()
    }

    init {
        viewModelScope.launch {
            runCatching { c.generus.scopeUnits("read") }.onSuccess { list ->
                scope = list
                val kelompok = list.filter { it.level == UnitLevel.kelompok }
                _units.value = if (kelompok.size > 1) c.units.tree(list.map { it.id }) else emptyList()
                unit.value = kelompok.singleOrNull()
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
        onOk("$n generus dinaikkan ke ${segmentLabel(t.to)}." + if (skipped > 0) " $skipped dilewati (sudah berubah atau di luar akses Anda)." else "")
    }
}

private data class PromoteRow(val g: PromotionCandidate, val age: Int, val suggestion: Suggestion)

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
    val refDate = academicYearStart(today)
    val page = state.data
    val rows = remember(page, transition) {
        page?.rows.orEmpty().map { PromoteRow(it, ageAt(it.birthDate, refDate), suggestPromotion(transition, it.birthDate, it.school, today)) }
    }
    val suggestedIds = remember(rows) { rows.filter { it.suggestion.suggested }.map { it.g.id }.toSet() }
    var checked by remember(rows) { mutableStateOf(suggestedIds) }
    var query by remember(rows) { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val target = segmentLabel(transition.to)
    // Kenaikan antarjenjang sekolah: kelas akhir SD = 6, SLTP = 9.
    val lastGrade = when (transition) {
        Transition.PraKePelajar, Transition.PraKeMandiri -> 9
        Transition.SdKePra -> 6
        else -> null
    }
    val schoolExample = if (lastGrade == 9) "SMPN 2 kelas 8" else "SDN 1 kelas 5"

    AppScreen(
        "Kenaikan jenjang",
        onBack = { nav.popBackStack() },
        bottomBar = {
            if (rows.isNotEmpty()) StickyBottom {
                PrimaryButton(
                    "Naikkan ${checked.size} generus ke $target",
                    onClick = { confirm = true },
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
                    Text(
                        "Lakukan di awal tahun ajaran. Generus yang disarankan sudah dicentang; periksa lalu simpan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectField("Jenis kenaikan", Transition.entries, transition, { it.label }, { model.transition.value = it; model.load() })
                    if (units.isNotEmpty()) UnitPickerField("Wilayah", units, unit?.id, { id -> model.selectUnit(id) }, placeholder = "Pilih desa / kelompok")
                }
            }
            when {
                state.error != null && page == null -> item { ErrorBox(state.error!!, model::load) }
                page == null && state.loading -> item { LoadingBox() }
                page == null -> item { EmptyState("Pilih desa atau kelompok terlebih dahulu.") }
                else -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(transition.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                transition.hint + " Centang hanya yang benar-benar naik; yang tidak dicentang tetap di jenjangnya. Usia dihitung per 1 Juli ${refDate.year}." +
                                    (if (lastGrade == null) "" else if (isPromotionSeason(today)) " Generus yang tercatat kelas $lastGrade dianggap baru lulus; kelas di bawahnya tidak disarankan."
                                    else " Di luar Juli–Agustus, generus yang tercatat kelas $lastGrade dianggap masih duduk di kelas $lastGrade dan tidak disarankan.") +
                                    (if (lastGrade != null) " Tulis kelas di kolom Sekolah (mis. \"$schoolExample\") agar saran lebih tepat." else "") +
                                    (if (page.truncated) " Hanya 500 generus pertama yang ditampilkan; pilih wilayah yang lebih kecil." else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (rows.isEmpty()) {
                        item { EmptyState("Tidak ada generus ${segmentLabel(transition.from)} aktif di wilayah ini") }
                    } else {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SearchPill(query, { query = it }, "Cari nama / sekolah / kelas")
                                Text(
                                    buildAnnotatedString {
                                        append("${rows.size} calon · ${suggestedIds.size} disarankan · ")
                                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append("${checked.size} dicentang") }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row {
                                    GhostButton("Centang saran", onClick = { checked = suggestedIds })
                                    GhostButton("Centang semua", onClick = { checked = rows.map { it.g.id }.toSet() })
                                    GhostButton("Kosongkan", onClick = { checked = emptySet() })
                                }
                            }
                        }
                        val q = query.trim().lowercase()
                        val visible = if (q.isEmpty()) rows else rows.filter { "${it.g.name} ${it.g.school ?: ""} ${it.g.kelompok}".lowercase().contains(q) }
                        if (visible.isEmpty()) item {
                            Text("Tidak ada yang cocok dengan pencarian.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(visible, key = { it.g.id }) { r ->
                            val on = r.g.id in checked
                            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                                Row(
                                    Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { checked = if (on) checked - r.g.id else checked + r.g.id }.padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = on, onCheckedChange = { v -> checked = if (v) checked + r.g.id else checked - r.g.id })
                                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(r.g.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                                            if (r.suggestion.suggested) Pill("disarankan")
                                        }
                                        Text(
                                            r.suggestion.reason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (r.suggestion.suggested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            "${r.g.gender.name} · ${r.age} th · ${r.g.school ?: "Sekolah/kelas belum diisi"} · ${r.g.kelompok}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

    if (confirm) {
        ConfirmSheet(
            "Naikkan ${checked.size} generus ke $target?",
            "Jenjang generus yang dicentang akan diperbarui.",
            "Naikkan jenjang",
            onConfirm = {
                confirm = false
                // Semua yang dicentang ikut, termasuk yang sedang tersembunyi oleh pencarian.
                model.promote(checked.toList(), { toast(it) }) { toast(it) }
            },
            onDismiss = { confirm = false },
        )
    }
}
