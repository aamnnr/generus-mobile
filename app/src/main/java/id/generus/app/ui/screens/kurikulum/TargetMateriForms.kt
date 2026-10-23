package id.generus.app.ui.screens.kurikulum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.ASPEK_ORDER
import id.generus.app.core.UserError
import id.generus.app.core.formatPosition
import id.generus.app.core.juzLabel
import id.generus.app.core.label
import id.generus.app.core.parsePosition
import id.generus.app.core.positionHint
import id.generus.app.core.positionInput
import id.generus.app.data.model.Aspek
import id.generus.app.data.model.AssessmentMode
import id.generus.app.data.model.Gender
import id.generus.app.data.model.Jenjang
import id.generus.app.data.model.Kategori
import id.generus.app.data.model.KitabItem
import id.generus.app.data.model.KitabUnit
import id.generus.app.data.model.MateriItem
import id.generus.app.data.model.MateriWrite
import id.generus.app.data.model.Target
import id.generus.app.data.model.TargetWrite
import id.generus.app.data.model.canEditCurriculum
import id.generus.app.data.model.label
import id.generus.app.ui.LoadVm
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.BannerTone
import id.generus.app.ui.components.Chip
import id.generus.app.ui.components.FieldLabel
import id.generus.app.ui.components.FormColumn
import id.generus.app.ui.components.GhostButton
import id.generus.app.ui.components.InfoBanner
import id.generus.app.ui.components.LoadableContent
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.SelectField
import id.generus.app.ui.components.SingleChips
import id.generus.app.ui.components.StickyBottom
import id.generus.app.ui.components.SwitchRow
import id.generus.app.ui.components.TextInput
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.popWithResult
import id.generus.app.ui.theme.ArabicText
import id.generus.app.ui.vm

enum class TargetKind(val label: String) { Materi("Dari pustaka"), Baru("Materi baru"), Kitab("Rentang kitab") }

data class TargetFormData(
    val jenjang: Jenjang,
    val targets: List<Target>,
    val materi: List<MateriItem>,
    val kitab: List<KitabItem>,
    val mapel: Map<Aspek, List<String>>,
    val existing: Target?,
)

class TargetFormVm(c: AppContainer, private val periodId: String, private val jenjangId: String, private val targetId: String?) : LoadVm<TargetFormData>(c) {
    init { load() }

    override suspend fun fetch(): TargetFormData {
        val jenjang = c.agenda.jenjang().firstOrNull { it.id == jenjangId } ?: throw UserError("Jenjang tidak ditemukan.")
        val targets = c.kurikulum.targets(periodId, jenjangId)
        return TargetFormData(
            jenjang, targets, c.kurikulum.materi(), c.kurikulum.kitab(), c.kurikulum.mapelNames(),
            targetId?.let { id -> targets.firstOrNull { it.id == id } ?: throw UserError("Target tidak ditemukan.") },
        )
    }

    fun save(row: TargetWrite, newMateri: MateriWrite?, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, onOk) {
        val materiId = newMateri?.let { c.kurikulum.saveMateri(null, it) } ?: row.materiId
        val write = row.copy(materiId = materiId)
        if (targetId != null) c.kurikulum.updateTarget(targetId, write)
        else {
            val next = (state.value.data?.targets?.maxOfOrNull { it.sortOrder } ?: -1) + 1
            c.kurikulum.insertTarget(write.copy(sortOrder = next))
        }
    }
}

/** Tambah / ubah target periode (padanan TargetForm di kurikulum/forms.tsx). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TargetFormScreen(nav: NavHostController, periodId: String, jenjangId: String, targetId: String?) {
    val toast = LocalToast.current
    val model = vm(key = "target-form-$periodId-$jenjangId-$targetId") { TargetFormVm(it, periodId, jenjangId, targetId) }
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()

    AppScreen(if (targetId == null) "Tambah target" else "Ubah target", onBack = { nav.popBackStack() }) { p ->
        LoadableContent(state, model::load, Modifier.padding(p)) { d ->
            val e = d.existing
            var kind by remember { mutableStateOf(if (e?.kitab != null) TargetKind.Kitab else TargetKind.Materi) }
            var aspek by remember { mutableStateOf(e?.aspek ?: Aspek.alim) }
            var mapel by remember { mutableStateOf(e?.mapel ?: "") }
            var materi by remember { mutableStateOf(e?.materi?.let { m -> d.materi.firstOrNull { it.id == m.id } }) }
            var title by remember { mutableStateOf("") }
            var body by remember { mutableStateOf("") }
            var arabic by remember { mutableStateOf("") }
            var translation by remember { mutableStateOf("") }
            var source by remember { mutableStateOf("") }
            var assessment by remember { mutableStateOf(AssessmentMode.generus) }
            var kitab by remember { mutableStateOf(e?.kitab?.let { k -> d.kitab.firstOrNull { it.id == k.id } }) }
            var from by remember { mutableStateOf(e?.kitab?.let { positionInput(it.unit, it.from) } ?: "") }
            var to by remember { mutableStateOf(e?.kitab?.let { positionInput(it.unit, it.to) } ?: "") }
            var note by remember { mutableStateOf(e?.note ?: "") }
            var strategy by remember { mutableStateOf(e?.strategy ?: "") }
            var kategori by remember { mutableStateOf<Kategori?>(e?.kategoriId?.let { k -> d.jenjang.kategori.firstOrNull { it.id == k } }) }
            var gender by remember { mutableStateOf(e?.gender) }
            var optional by remember { mutableStateOf(e?.optional ?: false) }
            var error by remember { mutableStateOf<String?>(null) }

            // Strategi terisi otomatis dari target lain dengan aspek & mapel yang sama.
            fun autoStrategy(a: Aspek, m: String) {
                if (strategy.isBlank()) d.targets.firstOrNull { it.aspek == a && it.mapel.equals(m.trim(), true) && it.strategy != null }?.strategy?.let { strategy = it }
            }

            val k = kitab
            val fromPos = k?.let { parsePosition(it.unit, from, it.total) }
            val toPos = k?.let { parsePosition(it.unit, to, it.total) }

            fun submit() {
                error = when {
                    mapel.trim().length < 2 -> "Isi mata pelajaran"
                    kind == TargetKind.Materi && materi == null -> "Pilih materi dari pustaka"
                    kind == TargetKind.Baru && title.trim().length < 2 -> "Isi judul materi baru"
                    kind == TargetKind.Kitab && k == null -> "Pilih kitab"
                    kind == TargetKind.Kitab && fromPos == null -> "Posisi awal tidak dikenali (${positionHint(k!!.unit, k.total)})"
                    kind == TargetKind.Kitab && toPos == null -> "Posisi akhir tidak dikenali (${positionHint(k!!.unit, k.total)})"
                    kind == TargetKind.Kitab && toPos!! < fromPos!! -> "Posisi akhir harus setelah posisi awal"
                    else -> null
                }
                if (error != null) return
                val row = TargetWrite(
                    periodId = periodId,
                    jenjangId = jenjangId,
                    kategoriId = kategori?.id,
                    gender = gender,
                    aspek = aspek,
                    mapel = mapel.trim(),
                    materiId = if (kind == TargetKind.Materi) materi!!.id else null,
                    kitabId = if (kind == TargetKind.Kitab) k!!.id else null,
                    rangeFrom = if (kind == TargetKind.Kitab) fromPos else null,
                    rangeTo = if (kind == TargetKind.Kitab) toPos else null,
                    note = note.trim().ifEmpty { null }.takeIf { kind == TargetKind.Kitab },
                    strategy = strategy.trim().ifEmpty { null },
                    optional = optional,
                )
                val newMateri = if (kind == TargetKind.Baru) MateriWrite(
                    aspek, mapel.trim(), title.trim(), body.trim().ifEmpty { null }, arabic.trim().ifEmpty { null },
                    translation.trim().ifEmpty { null }, source.trim().ifEmpty { null }, assessment,
                ) else null
                model.save(row, newMateri, { error = it }) {
                    toast(if (e == null) "Target ditambahkan." else "Target disimpan.")
                    nav.popWithResult()
                }
            }

            Column(Modifier.imePadding()) {
                FormColumn(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (error != null) InfoBanner(error!!, tone = BannerTone.Error)
                    Text("Jenjang ${d.jenjang.name}", style = MaterialTheme.typography.titleSmall)
                    SingleChips(TargetKind.entries, kind, { it.label }, { kind = it })
                    SelectField("Aspek", ASPEK_ORDER, aspek, { it.label }, { aspek = it; materi = null })
                    TextInput("Mata pelajaran", mapel, { mapel = it; autoStrategy(aspek, it) }, placeholder = "mis. Hafalan, Tata Krama")
                    val suggestions = d.mapel[aspek].orEmpty().filter { it != mapel && (mapel.isBlank() || it.contains(mapel, true)) }.take(8)
                    if (suggestions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { s -> Chip(s, false, onClick = { mapel = s; autoStrategy(aspek, s) }) }
                    }
                    when (kind) {
                        TargetKind.Materi -> {
                            val options = d.materi.filter { it.aspek == aspek }.sortedWith(compareBy({ it.mapel }, { it.title }))
                            SelectField(
                                "Materi", options, materi, { it.title }, { materi = it; if (mapel.isBlank()) { mapel = it.mapel; autoStrategy(aspek, it.mapel) } },
                                group = { it.mapel },
                                supporting = if (options.isEmpty()) "Belum ada materi untuk aspek ini; pilih \"Materi baru\"." else materi?.let { if (it.assessment == AssessmentMode.generus) "Dinilai per generus" else "Ditandai per kelompok" },
                            )
                            materi?.arabic?.let { Text(it, style = ArabicText, modifier = Modifier.fillMaxWidth()) }
                        }
                        TargetKind.Baru -> {
                            TextInput("Judul materi", title, { title = it }, placeholder = "mis. Dalil Jamaah")
                            TextInput("Uraian (opsional)", body, { body = it }, singleLine = false, minLines = 2, supporting = "Rincian poin, satu per baris.")
                            TextInput("Teks Arab (opsional)", arabic, { arabic = it }, singleLine = false, minLines = 2)
                            TextInput("Terjemahan (opsional)", translation, { translation = it }, singleLine = false)
                            TextInput("Sumber (opsional)", source, { source = it }, placeholder = "mis. QS Ali 'Imran 103")
                            Column {
                                FieldLabel("Cara penilaian")
                                SingleChips(AssessmentMode.entries, assessment, { if (it == AssessmentMode.generus) "Per generus" else "Per kelompok" }, { assessment = it })
                            }
                        }
                        TargetKind.Kitab -> {
                            SelectField("Kitab", d.kitab, kitab, { "${it.title} (${it.unit.label})" }, { kitab = it })
                            if (k != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TextInput("Dari", from, { from = it }, Modifier.weight(1f), placeholder = if (k.unit == KitabUnit.ayat) "Al-Baqarah 253" else "26",
                                        supporting = fromPos?.let { "✓ ${formatPosition(k.unit, it)}" }, error = if (from.isNotBlank() && fromPos == null) "Tidak dikenali" else null)
                                    TextInput("Sampai", to, { to = it }, Modifier.weight(1f), placeholder = if (k.unit == KitabUnit.ayat) "Ali Imron 91" else "38",
                                        supporting = toPos?.let { "✓ ${formatPosition(k.unit, it)}" }, error = if (to.isNotBlank() && toPos == null) "Tidak dikenali" else null)
                                }
                                if (k.unit == KitabUnit.ayat && fromPos != null && toPos != null && toPos >= fromPos) {
                                    GhostButton("Isi keterangan: ${juzLabel(fromPos, toPos)}", onClick = { note = juzLabel(fromPos, toPos) })
                                }
                                TextInput("Keterangan (opsional)", note, { note = it }, placeholder = "mis. Juz 3")
                            }
                        }
                    }
                    TextInput("Strategi pembelajaran (opsional)", strategy, { strategy = it }, singleLine = false, minLines = 2, placeholder = "mis. Disampaikan dalam KBM @ 2 x sebulan")
                    if (d.jenjang.kategori.isNotEmpty()) {
                        SelectField("Kategori", listOf<Kategori?>(null) + d.jenjang.kategori, kategori, { it?.name ?: "Semua kategori" }, { kategori = it })
                    }
                    Column {
                        FieldLabel("Khusus")
                        SingleChips(listOf<Gender?>(null, Gender.L, Gender.P), gender, { when (it) { null -> "Putra & putri"; Gender.L -> "Putra"; Gender.P -> "Putri" } }, { gender = it })
                    }
                    SwitchRow("Materi pilihan / extra", optional, { optional = it }, meta = "Tidak dihitung dalam persentase capaian.")
                }
                StickyBottom { PrimaryButton(if (e == null) "Tambah target" else "Simpan target", onClick = ::submit, loading = busy, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

data class MateriFormData(val existing: MateriItem?, val mapel: Map<Aspek, List<String>>)

class MateriFormVm(c: AppContainer, private val id: String?) : LoadVm<MateriFormData>(c) {
    init { load() }

    override suspend fun fetch(): MateriFormData {
        val existing = id?.let { mid -> (c.kurikulum.materi() + c.kurikulum.materi(archived = true)).firstOrNull { it.id == mid } ?: throw UserError("Materi tidak ditemukan.") }
        return MateriFormData(existing, c.kurikulum.mapelNames())
    }

    fun save(data: MateriWrite, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, onOk) { c.kurikulum.saveMateri(id, data) }

    fun setArchived(archived: Boolean, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, onOk) { c.kurikulum.setMateriArchived(id!!, archived) }
}

/** Pustaka materi: tambah / ubah / arsipkan (padanan /kurikulum/materi/baru & /kurikulum/materi/[id]). */
@Composable
fun MateriFormScreen(nav: NavHostController, id: String?) {
    val me = LocalMe.current
    val toast = LocalToast.current
    val editor = me.canEditCurriculum
    val model = vm(key = "materi-form-$id") { MateriFormVm(it, id) }
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()

    AppScreen(if (id == null) "Materi baru" else if (editor) "Ubah materi" else "Materi", onBack = { nav.popBackStack() }) { p ->
        LoadableContent(state, model::load, Modifier.padding(p)) { d ->
            val e = d.existing
            var aspek by remember { mutableStateOf(e?.aspek ?: Aspek.alim) }
            var mapel by remember { mutableStateOf(e?.mapel ?: "") }
            var title by remember { mutableStateOf(e?.title ?: "") }
            var body by remember { mutableStateOf(e?.body ?: "") }
            var arabic by remember { mutableStateOf(e?.arabic ?: "") }
            var translation by remember { mutableStateOf(e?.translation ?: "") }
            var source by remember { mutableStateOf(e?.source ?: "") }
            var assessment by remember { mutableStateOf(e?.assessment ?: AssessmentMode.generus) }
            var error by remember { mutableStateOf<String?>(null) }

            fun submit() {
                error = when {
                    mapel.trim().length < 2 -> "Isi mata pelajaran"
                    title.trim().length < 2 -> "Isi judul materi"
                    else -> null
                }
                if (error != null) return
                model.save(
                    MateriWrite(aspek, mapel.trim(), title.trim(), body.trim().ifEmpty { null }, arabic.trim().ifEmpty { null }, translation.trim().ifEmpty { null }, source.trim().ifEmpty { null }, assessment),
                    { error = it },
                ) { toast("Materi disimpan."); nav.popWithResult() }
            }

            Column(Modifier.imePadding()) {
                FormColumn(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (error != null) InfoBanner(error!!, tone = BannerTone.Error)
                    if (e != null) Text("Dipakai di ${e.usedIn} target." + if (e.archived) " Diarsipkan." else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectField("Aspek", ASPEK_ORDER, aspek, { it.label }, { aspek = it }, enabled = editor)
                    TextInput("Mata pelajaran", mapel, { mapel = it }, enabled = editor, supporting = d.mapel[aspek]?.take(6)?.joinToString(", ")?.let { "Contoh: $it" })
                    TextInput("Judul materi", title, { title = it }, enabled = editor)
                    TextInput("Uraian (opsional)", body, { body = it }, singleLine = false, minLines = 2, enabled = editor)
                    TextInput("Teks Arab (opsional)", arabic, { arabic = it }, singleLine = false, minLines = 2, enabled = editor)
                    if (arabic.isNotBlank()) Text(arabic, style = ArabicText, modifier = Modifier.fillMaxWidth())
                    TextInput("Terjemahan (opsional)", translation, { translation = it }, singleLine = false, enabled = editor)
                    TextInput("Sumber (opsional)", source, { source = it }, enabled = editor, placeholder = "mis. QS Ali 'Imran 103, HR Abu Dawud")
                    Column {
                        FieldLabel("Cara penilaian")
                        SingleChips(AssessmentMode.entries, assessment, { if (it == AssessmentMode.generus) "Dinilai per generus" else "Ditandai per kelompok" }, { if (editor) assessment = it })
                    }
                    if (editor && e != null) {
                        GhostButton(if (e.archived) "Kembalikan dari arsip" else "Arsipkan materi", onClick = {
                            model.setArchived(!e.archived, { toast(it) }) { toast(if (e.archived) "Materi dikembalikan." else "Materi diarsipkan."); nav.popWithResult() }
                        })
                    }
                }
                if (editor) StickyBottom { PrimaryButton("Simpan materi", onClick = ::submit, loading = busy, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}
