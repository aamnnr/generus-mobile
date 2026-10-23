package id.generus.app.ui.screens.kurikulum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.ASPEK_ORDER
import id.generus.app.core.UserError
import id.generus.app.core.dateRange
import id.generus.app.core.label
import id.generus.app.core.today
import id.generus.app.data.model.Aspek
import id.generus.app.data.model.AssessmentMode
import id.generus.app.data.model.KitabItem
import id.generus.app.data.model.KitabUnit
import id.generus.app.data.model.KitabWrite
import id.generus.app.data.model.MateriItem
import id.generus.app.data.model.Period
import id.generus.app.data.model.canEditCurriculum
import id.generus.app.ui.LoadVm
import id.generus.app.ui.OnResult
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.BottomSheet
import id.generus.app.ui.components.ChipRow
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.GhostButton
import id.generus.app.ui.components.ListCard
import id.generus.app.ui.components.ListRow
import id.generus.app.ui.components.LoadingBox
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.RefreshableList
import id.generus.app.ui.components.RowDivider
import id.generus.app.ui.components.SearchPill
import id.generus.app.ui.components.SelectField
import id.generus.app.ui.components.StatusBadge
import id.generus.app.ui.components.SwitchRow
import id.generus.app.ui.components.TextInput
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.nav.MateriForm
import id.generus.app.ui.nav.PeriodDetail
import id.generus.app.ui.nav.PeriodForm
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.theme.StatusColor
import id.generus.app.ui.vm
import kotlinx.coroutines.flow.MutableStateFlow

enum class KurikulumTab(val label: String) { Periode("Periode"), Materi("Pustaka materi"), Kitab("Kitab") }

data class KurikulumData(val periods: List<Period>, val materi: List<MateriItem>, val kitab: List<KitabItem>)

class KurikulumVm(c: AppContainer) : LoadVm<KurikulumData>(c) {
    val showArchived = MutableStateFlow(false)

    init { load() }

    override suspend fun fetch() = KurikulumData(
        periods = c.kurikulum.periods(),
        materi = c.kurikulum.materi(archived = showArchived.value),
        kitab = c.kurikulum.kitab(includeArchived = showArchived.value),
    )

    fun saveKitab(id: String?, title: String, unit: KitabUnit, total: Int, sort: Int, maxTo: Int, onError: (String) -> Unit, onOk: () -> Unit) =
        run(onError, { load(); onOk() }) {
            if (title.trim().length < 2) throw UserError("Isi nama kitab")
            if (total !in 1..100_000) throw UserError("Jumlah minimal 1")
            if (id != null && total < maxTo) throw UserError("Jumlah tidak boleh kurang dari $maxTo (sudah dipakai di target)")
            c.kurikulum.saveKitab(id, KitabWrite(title.trim(), unit, total, sort.coerceIn(0, 999)))
        }

    fun setKitabArchived(id: String, archived: Boolean, onError: (String) -> Unit) = run(onError, { load() }) { c.kurikulum.setKitabArchived(id, archived) }
}

/** Badge periode: Draf / Terbit / Berjalan. */
@Composable
fun PeriodBadges(p: Period) {
    val x = GenerusTheme.extra
    val t = today()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (p.publishedAt == null) StatusBadge("Draf", StatusColor(x.brassText, x.brassSoft))
        else StatusBadge("Terbit", x.izin)
        if (p.startsOn <= t && t <= p.endsOn) StatusBadge("Berjalan", x.hadir, dot = true)
    }
}

/** Kurikulum (padanan /kurikulum): periode target, pustaka materi, lintasan kitab. */
@Composable
fun KurikulumHomeScreen(nav: NavHostController) {
    val me = LocalMe.current
    val toast = LocalToast.current
    val editor = me.canEditCurriculum
    val model = vm { KurikulumVm(it) }
    val state by model.state.collectAsStateWithLifecycle()
    val archived by model.showArchived.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(KurikulumTab.Periode) }
    var q by rememberSaveable { mutableStateOf("") }
    var aspek by rememberSaveable { mutableStateOf<Aspek?>(null) }
    var kitabSheet by remember { mutableStateOf<KitabItem?>(null) }
    var newKitab by remember { mutableStateOf(false) }
    OnResult(nav) { model.load() }

    AppScreen(
        "Kurikulum",
        floatingActionButton = {
            if (editor) {
                val (text, action) = when (tab) {
                    KurikulumTab.Periode -> "Periode baru" to { nav.navigate(PeriodForm()) }
                    KurikulumTab.Materi -> "Materi baru" to { nav.navigate(MateriForm()) }
                    KurikulumTab.Kitab -> "Kitab baru" to { newKitab = true }
                }
                ExtendedFloatingActionButton(
                    onClick = action,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(text) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { p ->
        RefreshableList(refreshing = state.loading && state.data != null, onRefresh = model::load, padding = p) {
            item { ChipRow(KurikulumTab.entries, tab, { it.label }, { tab = it }) }
            val d = state.data
            when {
                d == null && state.error != null -> item { ErrorBox(state.error!!, model::load) }
                d == null -> item { LoadingBox() }
                tab == KurikulumTab.Periode -> {
                    if (d.periods.isEmpty()) item { EmptyState("Belum ada periode", body = if (editor) "Buat periode target, mis. \"Oktober 2026\"." else "Tim Kurikulum belum menerbitkan target.") }
                    else item {
                        ListCard {
                            d.periods.forEachIndexed { i, per ->
                                if (i > 0) RowDivider()
                                ListRow(
                                    "Periode ${per.name}",
                                    meta = "Semester ${per.semester} · ${dateRange(per.startsOn, per.endsOn)} · ${per.targetCount} target",
                                    trailing = { PeriodBadges(per) },
                                    onClick = { nav.navigate(PeriodDetail(per.id)) },
                                )
                            }
                        }
                    }
                }
                tab == KurikulumTab.Materi -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SearchPill(q, { q = it }, "Cari judul atau mapel")
                            ChipRow(listOf<Aspek?>(null) + ASPEK_ORDER, aspek, { it?.label ?: "Semua" }, { aspek = it })
                            if (editor) SwitchRow("Tampilkan arsip", archived, { model.showArchived.value = it; model.load() })
                        }
                    }
                    val list = d.materi.filter { m ->
                        (aspek == null || m.aspek == aspek) && (q.isBlank() || m.title.contains(q, true) || m.mapel.contains(q, true))
                    }
                    if (list.isEmpty()) item { EmptyState("Belum ada materi yang cocok") }
                    else item {
                        ListCard {
                            list.forEachIndexed { i, m ->
                                if (i > 0) RowDivider()
                                ListRow(
                                    m.title,
                                    meta = "${m.aspek.label} · ${m.mapel} · ${if (m.assessment == AssessmentMode.generus) "per generus" else "per kelompok"} · dipakai ${m.usedIn}×",
                                    trailing = { if (m.archived) StatusBadge("Arsip", StatusColor(MaterialTheme.colorScheme.onSurfaceVariant, GenerusTheme.extra.tile)) },
                                    onClick = { nav.navigate(MateriForm(m.id)) },
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (editor) item { SwitchRow("Tampilkan arsip", archived, { model.showArchived.value = it; model.load() }) }
                    if (d.kitab.isEmpty()) item { EmptyState("Belum ada lintasan kitab") }
                    else item {
                        ListCard {
                            d.kitab.forEachIndexed { i, k ->
                                if (i > 0) RowDivider()
                                ListRow(
                                    k.title,
                                    meta = "${k.unit.label} · ${k.total} · dipakai ${k.usedIn}×",
                                    trailing = { if (k.archived) StatusBadge("Arsip", StatusColor(MaterialTheme.colorScheme.onSurfaceVariant, GenerusTheme.extra.tile)) },
                                    onClick = if (editor) ({ kitabSheet = k }) else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (newKitab || kitabSheet != null) {
        KitabSheet(
            kitabSheet,
            onDismiss = { newKitab = false; kitabSheet = null },
            onArchive = { k -> model.setKitabArchived(k.id, !k.archived) { toast(it) }; kitabSheet = null },
        ) { title, unit, total, sort ->
            val k = kitabSheet
            model.saveKitab(k?.id, title, unit, total, sort, k?.maxTo ?: 0, { toast(it) }) {
                toast(if (k != null) "Kitab disimpan." else "Kitab ditambahkan.")
                newKitab = false
                kitabSheet = null
            }
        }
    }
}

@Composable
private fun KitabSheet(k: KitabItem?, onDismiss: () -> Unit, onArchive: (KitabItem) -> Unit, onSave: (String, KitabUnit, Int, Int) -> Unit) {
    var title by remember { mutableStateOf(k?.title ?: "") }
    var unit by remember { mutableStateOf(k?.unit ?: KitabUnit.halaman) }
    var total by remember { mutableStateOf(k?.total?.toString() ?: "") }
    var sort by remember { mutableStateOf(k?.sortOrder?.toString() ?: "0") }
    BottomSheet(if (k == null) "Kitab baru" else "Ubah kitab", onDismiss) {
        TextInput("Nama kitab / lintasan", title, { title = it }, placeholder = "mis. Makna Hadist K. Sholat")
        SelectField("Satuan", KitabUnit.entries, unit, { it.label }, { unit = it; if (it == KitabUnit.ayat) total = "6236" }, enabled = k == null || k.usedIn == 0)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextInput("Jumlah", total, { total = it.filter(Char::isDigit) }, Modifier.weight(1f), keyboardType = KeyboardType.Number, supporting = k?.takeIf { it.maxTo > 0 }?.let { "Min. ${it.maxTo}" })
            TextInput("Urutan", sort, { sort = it.filter(Char::isDigit) }, Modifier.weight(1f), keyboardType = KeyboardType.Number)
        }
        PrimaryButton("Simpan", onClick = { onSave(title, unit, total.toIntOrNull() ?: 0, sort.toIntOrNull() ?: 0) }, modifier = Modifier.fillMaxWidth())
        if (k != null) GhostButton(if (k.archived) "Kembalikan dari arsip" else "Arsipkan", onClick = { onArchive(k) }, modifier = Modifier.fillMaxWidth())
    }
}
