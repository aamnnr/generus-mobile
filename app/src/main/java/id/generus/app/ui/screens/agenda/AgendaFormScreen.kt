package id.generus.app.ui.screens.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.UserError
import id.generus.app.core.localDate
import id.generus.app.core.localTime
import id.generus.app.core.segmentsOf
import id.generus.app.core.segmentsToTarget
import id.generus.app.core.targetToSegments
import id.generus.app.core.today
import id.generus.app.core.zoneLabel
import id.generus.app.core.zonedToInstant
import id.generus.app.data.model.AgendaDetail
import id.generus.app.data.model.AgendaUnit
import id.generus.app.data.model.AgendaWrite
import id.generus.app.data.model.Gender
import id.generus.app.data.model.Jenjang
import id.generus.app.data.model.label
import id.generus.app.ui.LoadVm
import id.generus.app.ui.popWithResult
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.BannerTone
import id.generus.app.ui.components.DateField
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.FieldLabel
import id.generus.app.ui.components.FormColumn
import id.generus.app.ui.components.InfoBanner
import id.generus.app.ui.components.LoadableContent
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.MultiChips
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.SelectField
import id.generus.app.ui.components.SingleChips
import id.generus.app.ui.components.StickyBottom
import id.generus.app.ui.components.TextInput
import id.generus.app.ui.components.TimeField
import id.generus.app.ui.nav.AgendaDetail as AgendaDetailRoute
import id.generus.app.ui.nav.AgendaList
import id.generus.app.ui.vm
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class AgendaFormData(val units: List<AgendaUnit>, val jenjang: List<Jenjang>, val existing: AgendaDetail?)

private const val MAX_REPEAT_WEEKS = 26

class AgendaFormVm(c: AppContainer, private val id: String?) : LoadVm<AgendaFormData>(c) {
    init { load() }

    override suspend fun fetch() = AgendaFormData(
        units = c.agenda.units(),
        jenjang = c.agenda.jenjang(),
        existing = id?.let { c.agenda.detail(it) ?: throw UserError("Agenda tidak ditemukan atau Anda tidak punya akses.") },
    )

    fun save(rows: List<AgendaWrite>, onError: (String) -> Unit, onOk: (String) -> Unit) = run(onError) {
        if (id == null) {
            onOk(c.agenda.create(rows))
        } else {
            if (!c.agenda.update(id, rows.first())) throw UserError("Anda tidak punya akses untuk mengubah agenda ini.")
            onOk(id)
        }
    }
}

/** Buat / ubah agenda (padanan /agenda/baru & /agenda/[id]/ubah). */
@Composable
fun AgendaFormScreen(nav: NavHostController, id: String?) {
    val model = vm(key = "agenda-form-$id") { AgendaFormVm(it, id) }
    val state by model.state.collectAsStateWithLifecycle()
    AppScreen(if (id == null) "Buat agenda" else "Ubah agenda", onBack = { nav.popBackStack() }) { p ->
        LoadableContent(state, model::load, Modifier.padding(p)) { data ->
            if (data.units.isEmpty()) {
                ErrorBox("Anda belum punya peran admin untuk membuat agenda.", null)
            } else {
                AgendaForm(data, model, onSaved = { savedId, weeks ->
                    if (id == null) {
                        nav.navigate(AgendaDetailRoute(savedId)) { popUpTo(AgendaList) }
                    } else {
                        nav.popWithResult()
                    }
                })
            }
        }
    }
}

@Composable
private fun AgendaForm(data: AgendaFormData, model: AgendaFormVm, onSaved: (String, Int) -> Unit) {
    val toast = LocalToast.current
    val busy by model.busy.collectAsStateWithLifecycle()
    val e = data.existing
    val segments = remember(data.jenjang) { segmentsOf(data.jenjang) }

    var unit by remember { mutableStateOf(e?.let { ex -> data.units.firstOrNull { it.id == ex.unitId } } ?: data.units.singleOrNull()) }
    var title by remember { mutableStateOf(e?.title ?: "") }
    var location by remember { mutableStateOf(e?.location ?: "") }
    var date by remember { mutableStateOf<LocalDate?>(e?.startsAt?.localDate() ?: today()) }
    var start by remember { mutableStateOf<LocalTime?>(e?.startsAt?.localTime() ?: LocalTime.of(19, 30)) }
    var end by remember { mutableStateOf<LocalTime?>(e?.endsAt?.localTime() ?: LocalTime.of(21, 0)) }
    var lateMinutes by remember { mutableStateOf(e?.let { Duration.between(it.startsAt, it.lateAfter).toMinutes().toString() } ?: "15") }
    var delegateDays by remember { mutableStateOf(e?.let { Duration.between(it.endsAt, it.delegateUntil).toDays().toString() } ?: "2") }
    var gender by remember { mutableStateOf(e?.targetGender) }
    var selected by remember { mutableStateOf(e?.let { targetToSegments(it.targetJenjangIds, it.targetKategoriIds, data.jenjang) } ?: segments.map { it.key }.toSet()) }
    var weeks by remember { mutableStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val late = lateMinutes.trim().toIntOrNull()
        val days = delegateDays.trim().toIntOrNull()
        error = when {
            unit == null -> "Pilih penyelenggara"
            title.trim().length < 3 -> "Judul minimal 3 huruf"
            title.trim().length > 120 -> "Judul maksimal 120 huruf"
            date == null -> "Tanggal tidak valid"
            start == null || end == null -> "Jam tidak valid"
            !end!!.isAfter(start) -> "Jam selesai harus setelah jam mulai"
            late == null || late !in 0..240 -> "Batas terlambat 0–240 menit"
            days == null || days !in 0..14 -> "Batas pelengkapan 0–14 hari"
            else -> null
        }
        if (error != null) return
        val startsAt = zonedToInstant(date!!, start!!)
        val endsAt = zonedToInstant(date!!, end!!)
        val lateAfter = minOf(startsAt.plusSeconds(late!! * 60L), endsAt)
        val (jenjangIds, kategoriIds) = segmentsToTarget(selected, data.jenjang)
        val base = AgendaWrite(
            unitId = unit!!.id,
            title = title.trim(),
            location = location.trim().ifEmpty { null },
            startsAt = startsAt,
            endsAt = endsAt,
            lateAfter = lateAfter,
            delegateUntil = endsAt.plus(Duration.ofDays(days!!.toLong())),
            targetJenjangIds = jenjangIds,
            targetKategoriIds = kategoriIds,
            targetGender = gender,
        )
        val recurrence = if (e == null && weeks > 1) UUID.randomUUID().toString() else null
        val rows = if (e != null) listOf(base) else (0 until weeks).map { i ->
            val shift = Duration.ofDays(7L * i)
            base.copy(
                startsAt = base.startsAt.plus(shift),
                endsAt = base.endsAt.plus(shift),
                lateAfter = base.lateAfter.plus(shift),
                delegateUntil = base.delegateUntil.plus(shift),
                recurrenceId = recurrence,
            )
        }
        model.save(rows, { error = it }) { savedId ->
            toast(if (e != null) "Agenda diubah." else if (weeks > 1) "$weeks agenda mingguan dibuat." else "Agenda dibuat.")
            onSaved(savedId, weeks)
        }
    }

    Column(Modifier.imePadding()) {
        FormColumn(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (error != null) InfoBanner(error!!, tone = BannerTone.Error)
            SelectField(
                "Penyelenggara",
                data.units,
                unit,
                { "${it.level.label} ${it.name}" + (it.parentName?.let { p -> " · $p" } ?: "") },
                { unit = it },
                group = { it.level.label },
            )
            TextInput("Judul", title, { title = it }, placeholder = "mis. Pengajian rutin remaja")
            TextInput("Lokasi (opsional)", location, { location = it }, placeholder = "mis. Masjid Al-Ikhlas")
            DateField("Tanggal", date, { date = it })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("Mulai (${zoneLabel()})", start, { start = it }, Modifier.weight(1f))
                TimeField("Selesai", end, { end = it }, Modifier.weight(1f))
            }
            TextInput("Terlambat bila datang lewat (menit)", lateMinutes, { lateMinutes = it.filter(Char::isDigit) }, keyboardType = KeyboardType.Number)
            TextInput(
                "Batas pelengkapan presensi (hari setelah selesai)",
                delegateDays,
                { delegateDays = it.filter(Char::isDigit) },
                keyboardType = KeyboardType.Number,
                supporting = "Admin kelompok boleh melengkapi presensi anggotanya sampai batas ini; setelahnya terkunci otomatis.",
            )
            Column {
                FieldLabel("Sasaran jenjang")
                MultiChips(segments, segments.filter { it.key in selected }.toSet(), { it.label }, { s -> selected = if (s.key in selected) selected - s.key else selected + s.key })
                Text(
                    "Tidak memilih apa pun = semua jenjang.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Column {
                FieldLabel("Jenis kelamin")
                SingleChips(listOf<Gender?>(null, Gender.L, Gender.P), gender, { it?.label ?: "Semua" }, { gender = it })
            }
            if (e == null) {
                SelectField(
                    "Ulangi setiap pekan",
                    (1..MAX_REPEAT_WEEKS).toList(),
                    weeks,
                    { if (it == 1) "Tidak diulang" else "$it pekan" },
                    { weeks = it },
                )
            }
        }
        StickyBottom {
            PrimaryButton(if (e == null) "Simpan agenda" else "Simpan perubahan", onClick = ::submit, loading = busy, modifier = Modifier.fillMaxWidth())
        }
    }
}
