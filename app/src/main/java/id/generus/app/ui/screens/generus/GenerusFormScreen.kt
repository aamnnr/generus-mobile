package id.generus.app.ui.screens.generus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import id.generus.app.core.today
import id.generus.app.data.model.Gender
import id.generus.app.data.model.GenerusDetail
import id.generus.app.data.model.GenerusWrite
import id.generus.app.data.model.Jenjang
import id.generus.app.data.model.Kategori
import id.generus.app.data.model.ScopeUnit
import id.generus.app.data.model.label
import id.generus.app.ui.LoadVm
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.BannerTone
import id.generus.app.ui.components.DateField
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.FieldLabel
import id.generus.app.ui.components.FormColumn
import id.generus.app.ui.components.InfoBanner
import id.generus.app.ui.components.LoadableContent
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.SelectField
import id.generus.app.ui.components.SingleChips
import id.generus.app.ui.components.StickyBottom
import id.generus.app.ui.components.TextInput
import id.generus.app.ui.nav.GenerusDetail as GenerusDetailRoute
import id.generus.app.ui.nav.GenerusList
import id.generus.app.ui.popWithResult
import id.generus.app.ui.vm
import java.time.LocalDate

data class GenerusFormData(val jenjang: List<Jenjang>, val kelompok: List<ScopeUnit>, val existing: GenerusDetail?)

class GenerusFormVm(c: AppContainer, private val id: String?) : LoadVm<GenerusFormData>(c) {
    init { load() }

    override suspend fun fetch() = GenerusFormData(
        jenjang = c.agenda.jenjang(),
        kelompok = if (id == null) c.generus.scopeUnits("write") else emptyList(),
        existing = id?.let { c.generus.detail(it) ?: throw UserError("Generus tidak ditemukan atau di luar lingkup Anda.") },
    )

    fun save(kelompokId: String?, data: GenerusWrite, onError: (String) -> Unit, onOk: (String) -> Unit) = run(onError) {
        if (id == null) {
            onOk(c.generus.create(kelompokId ?: throw UserError("Pilih kelompok"), data))
        } else {
            if (!c.generus.update(id, data)) throw UserError("Anda tidak punya akses untuk mengubah generus ini.")
            onOk(id)
        }
    }
}

/** Tambah / ubah data diri generus (padanan /generus/baru & form di /generus/[id]). */
@Composable
fun GenerusFormScreen(nav: NavHostController, id: String?) {
    val model = vm(key = "generus-form-$id") { GenerusFormVm(it, id) }
    val state by model.state.collectAsStateWithLifecycle()
    AppScreen(if (id == null) "Tambah generus" else "Ubah data diri", onBack = { nav.popBackStack() }) { p ->
        LoadableContent(state, model::load, Modifier.padding(p)) { data ->
            if (id == null && data.kelompok.isEmpty()) {
                ErrorBox("Anda belum punya peran admin di kelompok mana pun.", null)
            } else {
                GenerusFormBody(data, model) { savedId ->
                    if (id == null) nav.navigate(GenerusDetailRoute(savedId)) { popUpTo(GenerusList) } else nav.popWithResult()
                }
            }
        }
    }
}

private val PHONE = Regex("^(\\+62|0)8\\d{7,12}$")

@Composable
private fun GenerusFormBody(data: GenerusFormData, model: GenerusFormVm, onSaved: (String) -> Unit) {
    val toast = LocalToast.current
    val busy by model.busy.collectAsStateWithLifecycle()
    val e = data.existing
    var kelompok by remember { mutableStateOf(data.kelompok.singleOrNull()) }
    var name by remember { mutableStateOf(e?.name ?: "") }
    var gender by remember { mutableStateOf(e?.gender) }
    var birth by remember { mutableStateOf(e?.birthDate) }
    var jenjang by remember { mutableStateOf(e?.let { ex -> data.jenjang.firstOrNull { it.id == ex.jenjangId } }) }
    var kategori by remember { mutableStateOf<Kategori?>(e?.kategoriId?.let { k -> data.jenjang.flatMap { it.kategori }.firstOrNull { it.id == k } }) }
    var school by remember { mutableStateOf(e?.school ?: "") }
    var guardian by remember { mutableStateOf(e?.guardianName ?: "") }
    var phone by remember { mutableStateOf(e?.guardianPhone ?: "") }
    var notes by remember { mutableStateOf(e?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val cleanPhone = phone.replace(Regex("[\\s-]"), "")
        error = when {
            e == null && kelompok == null -> "Pilih kelompok"
            name.trim().length < 2 -> "Nama minimal 2 huruf"
            name.trim().length > 120 -> "Nama maksimal 120 huruf"
            gender == null -> "Pilih jenis kelamin"
            birth == null -> "Isi tanggal lahir"
            birth!! < LocalDate.of(1980, 1, 1) -> "Tanggal lahir terlalu lama"
            birth!! > today() -> "Tanggal lahir tidak boleh di masa depan"
            jenjang == null -> "Pilih jenjang"
            jenjang!!.kategori.isNotEmpty() && kategori == null -> "Pilih kategori ${jenjang!!.name}"
            cleanPhone.isNotEmpty() && !PHONE.matches(cleanPhone) -> "No. HP tidak valid (contoh 081234567890)"
            notes.length > 500 -> "Catatan maksimal 500 karakter"
            else -> null
        }
        if (error != null) return
        val write = GenerusWrite(
            name = name.trim(),
            gender = gender!!,
            birthDate = birth!!,
            jenjangId = jenjang!!.id,
            kategoriId = kategori?.takeIf { jenjang!!.kategori.isNotEmpty() }?.id,
            school = school.trim().ifEmpty { null },
            guardianName = guardian.trim().ifEmpty { null },
            guardianPhone = cleanPhone.ifEmpty { null },
            notes = notes.trim().ifEmpty { null },
        )
        model.save(kelompok?.id, write, { error = it }) { savedId ->
            toast(if (e == null) "Generus ditambahkan." else "Data disimpan.")
            onSaved(savedId)
        }
    }

    Column(Modifier.imePadding()) {
        FormColumn(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (error != null) InfoBanner(error!!, tone = BannerTone.Error)
            if (e == null) {
                SelectField("Kelompok", data.kelompok, kelompok, { "Kelompok ${it.name}" }, { kelompok = it }, group = { "Desa ${it.desaName}" })
            }
            TextInput("Nama lengkap", name, { name = it })
            Column {
                FieldLabel("Jenis kelamin")
                SingleChips(Gender.entries, gender, { it.label }, { gender = it })
            }
            DateField("Tanggal lahir", birth, { birth = it })
            SelectField("Jenjang", data.jenjang, jenjang, { it.name }, { jenjang = it; if (it.kategori.none { k -> k.id == kategori?.id }) kategori = null })
            if (jenjang?.kategori?.isNotEmpty() == true) {
                Column {
                    FieldLabel("Kategori")
                    SingleChips(jenjang!!.kategori, kategori, { it.name }, { kategori = it })
                }
            }
            TextInput("Sekolah / kelas (opsional)", school, { school = it }, placeholder = "mis. SMP 2 kelas 9")
            TextInput("Nama orang tua/wali (opsional)", guardian, { guardian = it })
            TextInput("No. HP orang tua/wali (opsional)", phone, { phone = it }, keyboardType = KeyboardType.Phone, placeholder = "081234567890")
            TextInput("Catatan (opsional)", notes, { notes = it }, singleLine = false, minLines = 2)
        }
        StickyBottom {
            PrimaryButton(if (e == null) "Simpan generus" else "Simpan perubahan", onClick = ::submit, loading = busy, modifier = Modifier.fillMaxWidth())
        }
    }
}
