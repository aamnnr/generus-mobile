package id.generus.app.ui.screens.capaian

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.ui.theme.ArabicText
import id.generus.app.core.UserError
import id.generus.app.core.formatPosition
import id.generus.app.core.formatShort
import id.generus.app.core.kitabStatus
import id.generus.app.core.label
import id.generus.app.core.parsePosition
import id.generus.app.core.positionHint
import id.generus.app.core.today
import id.generus.app.data.AchievementChange
import id.generus.app.data.model.CapaianStatus
import id.generus.app.data.model.KitabUnit
import id.generus.app.data.model.Target
import id.generus.app.data.model.TargetRosterEntry
import id.generus.app.data.model.canWriteGenerus
import id.generus.app.ui.LoadVm
import id.generus.app.ui.components.AppCard
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.DateField
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.GhostButton
import id.generus.app.ui.components.LoadableContent
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.RefreshableList
import id.generus.app.ui.components.RowDivider
import id.generus.app.ui.components.SecondaryButton
import id.generus.app.ui.components.TextInput
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.theme.of
import id.generus.app.ui.vm
import java.time.LocalDate

data class TargetRosterData(val target: Target, val roster: List<TargetRosterEntry>)

class TargetRosterVm(c: AppContainer, private val targetId: String, private val kelompokId: String) : LoadVm<TargetRosterData>(c) {
    init { load() }

    override suspend fun fetch(): TargetRosterData {
        val t = c.kurikulum.target(targetId) ?: throw UserError("Target tidak ditemukan atau belum terbit.")
        return TargetRosterData(t, c.capaian.targetRoster(targetId, kelompokId))
    }

    fun saveAchievements(changes: Map<String, CapaianStatus>, onError: (String) -> Unit, onOk: (Int) -> Unit) = run(onError, { load() }) {
        if (changes.isEmpty()) throw UserError("Tidak ada perubahan untuk disimpan.")
        onOk(c.capaian.saveAchievements(changes.map { (g, s) -> AchievementChange(targetId, g, s) }))
    }

    fun saveKitab(kitabId: String, ids: Set<String>, position: Int, date: LocalDate, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) {
        if (ids.isEmpty()) throw UserError("Pilih minimal satu generus")
        if (date > today()) throw UserError("Tanggal tidak boleh di masa depan.")
        c.capaian.saveKitabProgress(kitabId, ids.toList(), position, date)
    }
}

/** Kepala target: judul, mapel, isi materi (teks Arab, terjemahan, sumber) bila ada. */
@Composable
fun TargetHeader(t: Target) {
    AppCard(Modifier.fillMaxWidth()) {
        Text("${t.aspek.label} · ${t.mapel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(t.title() + (t.range()?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
        val tags = t.tags()
        if (tags.isNotEmpty()) Text(tags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        t.materi?.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        t.materi?.arabic?.let { Text(it, style = ArabicText, modifier = Modifier.fillMaxWidth()) }
        t.materi?.translation?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        t.materi?.source?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        t.strategy?.let { Text("Strategi: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Checklist capaian satu target untuk seluruh generus sasaran kelompok (padanan /capaian/target/[id]). */
@Composable
fun CapaianTargetScreen(nav: NavHostController, targetId: String, kelompokId: String) {
    val me = LocalMe.current
    val toast = LocalToast.current
    val writer = me.canWriteGenerus
    val model = vm(key = "target-$targetId-$kelompokId") { TargetRosterVm(it, targetId, kelompokId) }
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    // Hanya yang diubah; sisanya mengikuti data server terbaru.
    var edits by remember(state.data) { mutableStateOf(mapOf<String, CapaianStatus>()) }
    val rows = state.data?.roster.orEmpty()
    val value = { r: TargetRosterEntry -> edits[r.generusId] ?: r.status }
    val changed = rows.filter { value(it) != it.status }

    AppScreen(
        "Capaian materi",
        onBack = { nav.popBackStack() },
        bottomBar = {
            if (writer && rows.isNotEmpty()) Surface(shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    PrimaryButton(
                        if (changed.isEmpty()) "Belum ada perubahan" else "Simpan ${changed.size} perubahan",
                        onClick = {
                            model.saveAchievements(changed.associate { it.generusId to value(it) }, { toast(it) }) { n -> toast("$n capaian disimpan."); edits = emptyMap() }
                        },
                        enabled = changed.isNotEmpty(),
                        loading = busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { p ->
        LoadableContent(state, model::load, Modifier.padding(p)) { d ->
            RefreshableList(refreshing = state.loading, onRefresh = model::load, padding = androidx.compose.foundation.layout.PaddingValues()) {
                item { TargetHeader(d.target) }
                if (rows.isEmpty()) {
                    item { EmptyState("Tidak ada generus sasaran di kelompok ini") }
                    return@RefreshableList
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val count = { s: CapaianStatus -> rows.count { value(it) == s } }
                        Text(
                            "${count(CapaianStatus.tuntas)} tuntas · ${count(CapaianStatus.proses)} proses · ${count(CapaianStatus.belum)} belum",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (writer) {
                            GhostButton("Semua tuntas", onClick = { edits = rows.associate { it.generusId to CapaianStatus.tuntas } })
                            if (changed.isNotEmpty()) GhostButton("Urungkan", onClick = { edits = emptyMap() })
                        }
                    }
                }
                item {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                        Column {
                            rows.forEachIndexed { i, r ->
                                if (i > 0) RowDivider()
                                val dirty = value(r) != r.status
                                Column(
                                    Modifier.fillMaxWidth().background(if (dirty) GenerusTheme.extra.brassSoft else MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(r.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${r.gender.name}${r.kategori?.let { " · $it" } ?: ""}${if (!r.aktif) " · sudah tidak aktif" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    StatusChips(value(r), { s -> edits = edits + (r.generusId to s) }, r.name, enabled = writer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Progres kitab massal: pilih generus yang mengaji bersama → catat posisi yang sama (padanan /capaian/kitab/[id]). */
@Composable
fun CapaianKitabScreen(nav: NavHostController, targetId: String, kelompokId: String) {
    val me = LocalMe.current
    val toast = LocalToast.current
    val writer = me.canWriteGenerus
    val model = vm(key = "kitab-$targetId-$kelompokId") { TargetRosterVm(it, targetId, kelompokId) }
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var position by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<LocalDate?>(today()) }
    val d = state.data
    val k = d?.target?.kitab
    val parsed = if (k != null && position.isNotBlank()) parsePosition(k.unit, position, k.total) else null

    AppScreen(
        "Progres kitab",
        onBack = { nav.popBackStack() },
        bottomBar = {
            if (writer && k != null && d.roster.isNotEmpty()) Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextInput(
                            "Posisi terakhir",
                            position,
                            { position = it },
                            modifier = Modifier.weight(1.4f),
                            placeholder = if (k.unit == KitabUnit.ayat) "mis. Al-Baqarah 280" else "1–${k.total}",
                            keyboardType = if (k.unit == KitabUnit.ayat) KeyboardType.Text else KeyboardType.Number,
                            supporting = when {
                                position.isBlank() -> positionHint(k.unit, k.total)
                                parsed != null -> "✓ ${formatPosition(k.unit, parsed)}"
                                else -> null
                            },
                            error = if (position.isNotBlank() && parsed == null) "Tidak dikenali" else null,
                        )
                        DateField("Tanggal", date, { date = it }, Modifier.weight(1f))
                    }
                    PrimaryButton(
                        "Catat untuk ${selected.size} generus",
                        onClick = {
                            val pos = parsed ?: return@PrimaryButton toast("Isi posisi terakhir (${positionHint(k.unit, k.total)})")
                            model.saveKitab(k.id, selected, pos, date ?: today(), { toast(it) }) {
                                toast("Posisi ${formatPosition(k.unit, pos)} dicatat untuk ${selected.size} generus.")
                                selected = emptySet()
                                position = ""
                            }
                        },
                        enabled = selected.isNotEmpty() && parsed != null,
                        loading = busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { p ->
        LoadableContent(state, model::load, Modifier.padding(p)) { data ->
            val kitab = data.target.kitab
            RefreshableList(refreshing = state.loading, onRefresh = model::load, padding = androidx.compose.foundation.layout.PaddingValues()) {
                item { TargetHeader(data.target) }
                if (kitab == null || data.roster.isEmpty()) {
                    item { EmptyState("Tidak ada generus sasaran di kelompok ini") }
                    return@RefreshableList
                }
                if (writer) item {
                    val notDone = data.roster.filter { kitabStatus(it.position, kitab.from, kitab.to) != CapaianStatus.tuntas }.map { it.generusId }.toSet()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Pilih semua", onClick = { selected = data.roster.map { it.generusId }.toSet() }, icon = Icons.Rounded.DoneAll)
                        SecondaryButton("Belum tuntas", onClick = { selected = notDone }, enabled = notDone.isNotEmpty())
                        if (selected.isNotEmpty()) GhostButton("Kosongkan", onClick = { selected = emptySet() })
                    }
                }
                item {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                        Column {
                            data.roster.forEachIndexed { i, r ->
                                if (i > 0) RowDivider()
                                val s = kitabStatus(r.position, kitab.from, kitab.to)
                                val on = r.generusId in selected
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                        .clickable(enabled = writer, role = Role.Checkbox) { selected = if (on) selected - r.generusId else selected + r.generusId }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (writer) Checkbox(on, { selected = if (it) selected + r.generusId else selected - r.generusId })
                                    Column(Modifier.weight(1f).padding(start = if (writer) 0.dp else 8.dp)) {
                                        Text(r.name, style = MaterialTheme.typography.titleSmall)
                                        Text("${r.gender.name}${r.kategori?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                                        Text(
                                            r.position?.let { formatPosition(kitab.unit, it) } ?: "Belum ada",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (s == CapaianStatus.belum) MaterialTheme.colorScheme.onSurfaceVariant else GenerusTheme.extra.of(s).solid,
                                        )
                                        r.recordedOn?.let { Text(it.formatShort(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
