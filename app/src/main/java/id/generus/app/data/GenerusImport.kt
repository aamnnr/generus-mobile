package id.generus.app.data

import id.generus.app.core.Xlsx
import id.generus.app.core.today
import id.generus.app.data.model.Gender
import id.generus.app.data.model.Jenjang
import id.generus.app.data.model.ScopeUnit
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Membaca & memvalidasi file template (Template_Data_Generus.xlsx).
// Padanan src/lib/import/generus-import.ts. Murni (tanpa jaringan) supaya bisa dites.

const val MAX_IMPORT_ROWS = 3000

data class ImportContext(
    /** Kelompok yang boleh ditulis user. */
    val kelompok: List<ScopeUnit>,
    val jenjang: List<Jenjang>,
    /** Kunci generus yang sudah ada: lihat [duplicateKey]. */
    val existingKeys: Set<String>,
)

data class GenerusInsert(
    val kelompokId: String,
    val name: String,
    val gender: Gender,
    val birthDate: LocalDate,
    val jenjangId: String,
    val kategoriId: String?,
    val school: String?,
    val guardianName: String?,
    val guardianPhone: String?,
    val notes: String?,
) {
    fun json(): JsonObject = buildJsonObject {
        put("kelompok_id", kelompokId)
        put("name", name)
        put("gender", gender.name)
        put("birth_date", birthDate.toString())
        put("jenjang_id", jenjangId)
        put("kategori_id", kategoriId)
        put("school", school)
        put("guardian_name", guardianName)
        put("guardian_phone", guardianPhone)
        put("notes", notes)
    }
}

enum class ImportStatus { ok, error, duplikat }

data class ImportRow(
    /** Nomor baris di Excel (1-based), untuk ditunjukkan ke admin. */
    val row: Int,
    val name: String,
    val kelompok: String,
    val jenjang: String,
    val status: ImportStatus,
    val message: String? = null,
    val data: GenerusInsert? = null,
)

data class ImportResult(val rows: List<ImportRow>, val fileError: String? = null)

private val COLUMNS: Map<String, List<String>> = linkedMapOf(
    "name" to listOf("nama lengkap", "nama"),
    "gender" to listOf("jenis kelamin", "l/p"),
    "birthDate" to listOf("tanggal lahir", "tgl lahir"),
    "kelompok" to listOf("kelompok"),
    "jenjang" to listOf("jenjang"),
    "kategori" to listOf("kategori"),
    "school" to listOf("sekolah / kelas", "sekolah/kelas", "sekolah"),
    "guardianName" to listOf("nama orang tua/wali", "orang tua/wali", "nama wali"),
    "guardianPhone" to listOf("no. hp orang tua/wali", "no hp orang tua/wali", "no. hp", "no hp"),
    "notes" to listOf("catatan"),
)
private val REQUIRED = listOf("name", "gender", "birthDate", "kelompok", "jenjang")

fun norm(v: Any?): String = (v?.let { if (it is Double && it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    .lowercase().replace(Regex("\\s+"), " ").trim()

private fun normHeader(v: Any?) = norm(v).replace(Regex("\\s*\\*$"), "")

/** Kunci deteksi duplikat: kelompok + nama + tanggal lahir. */
fun duplicateKey(kelompokId: String, name: String, birthDate: LocalDate) = "$kelompokId|${norm(name)}|$birthDate"

/** Normalisasi No. HP; "" bila kosong, null bila tidak valid. */
fun normalizePhone(v: Any?): String? {
    if (v == null || (v is String && v.isBlank())) return ""
    var s = when (v) {
        is Double -> v.toLong().toString().let { if (it.startsWith("8")) "0$it" else it } // Excel membuang angka 0 di depan
        else -> v.toString()
    }.replace(Regex("[\\s\\-().]"), "")
    if (s.startsWith("62")) s = "+$s"
    return if (Regex("^(\\+62|0)8\\d{7,12}$").matches(s)) s else null
}

/** Tanggal dari sel Excel (serial number, teks dd/mm/yyyy atau yyyy-mm-dd). */
fun parseDate(v: Any?): LocalDate? {
    if (v is Double) return runCatching { Xlsx.excelSerialToDate(v) }.getOrNull()
    val s = v?.toString()?.trim() ?: return null
    Regex("^(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{4})$").find(s)?.let { m ->
        return runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
    }
    Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(s)?.let { m ->
        return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
    }
    return null
}

private fun parseGender(v: Any?): Gender? = when (norm(v).replace(Regex("[^a-z]"), "")) {
    "l", "lk", "laki", "lakilaki" -> Gender.L
    "p", "pr", "perempuan", "wanita" -> Gender.P
    else -> null
}

private const val AMBIGU = "ambigu"

private fun kelompokIndex(kelompok: List<ScopeUnit>): (Any?) -> String? {
    fun strip(s: String) = norm(s).replace(Regex("^(desa|kelompok)\\s+"), "")
    val full = HashMap<String, String>()
    val byName = HashMap<String, MutableList<String>>()
    for (k in kelompok) {
        full["${strip(k.desaName)}/${strip(k.name)}"] = k.id
        byName.getOrPut(strip(k.name)) { mutableListOf() } += k.id
    }
    return { input ->
        val s = norm(input)
        if (s.contains("/")) {
            val (desa, kel) = s.split("/", limit = 2).map(::strip)
            full["$desa/$kel"]
        } else {
            byName[strip(s)]?.let { if (it.size == 1) it.first() else AMBIGU }
        }
    }
}

private fun text(v: Any?, max: Int): String? {
    val s = (v?.let { if (it is Double && it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
        .replace(Regex("\\s+"), " ").trim()
    return s.takeIf { it.isNotEmpty() }?.take(max)
}

/** Ambil baris mentah sheet "Generus" (atau sheet pertama yang punya kolom "Nama Lengkap"). */
fun generusSheet(wb: Xlsx.Workbook): List<List<Any?>>? =
    wb.sheets.entries.firstOrNull { norm(it.key) == "generus" }?.value
        ?: wb.sheets.values.firstOrNull { rows -> rows.take(5).any { r -> r.any { normHeader(it) == "nama lengkap" } } }

/** Validasi baris-baris sheet Generus. `sheetRows` termasuk baris judul. */
fun validateRows(sheetRows: List<List<Any?>>, ctx: ImportContext): ImportResult {
    val headerIdx = sheetRows.indexOfFirst { r -> r.any { normHeader(it) == "nama lengkap" } }
    if (headerIdx < 0) return ImportResult(emptyList(), "Kolom \"Nama Lengkap\" tidak ditemukan. Gunakan template resmi.")
    val header = sheetRows[headerIdx].map(::normHeader)
    val col = COLUMNS.mapValues { (_, names) -> header.indexOfFirst { it in names } }
    val missing = REQUIRED.filter { col[it]!! < 0 }
    if (missing.isNotEmpty()) return ImportResult(emptyList(), "Kolom wajib tidak ditemukan: ${missing.joinToString(", ")}.")

    val findKelompok = kelompokIndex(ctx.kelompok)
    val seen = HashSet<String>()
    val rows = mutableListOf<ImportRow>()
    val todayDate = today()

    for (i in headerIdx + 1 until sheetRows.size) {
        val r = sheetRows[i]
        fun get(k: String): Any? = col[k]!!.takeIf { it >= 0 }?.let { r.getOrNull(it) }
        val nameRaw = text(get("name"), 120)
        // Lewati baris kosong dan baris contoh dari template.
        if (nameRaw == null && text(get("kelompok"), 200) == null) continue
        if (norm(r.getOrNull(0)) == "contoh" || norm(nameRaw).startsWith("contoh:")) continue

        val base = ImportRow(
            row = i + 1,
            name = nameRaw ?: "",
            kelompok = text(get("kelompok"), 200) ?: "",
            jenjang = listOfNotNull(text(get("jenjang"), 60), text(get("kategori"), 60)).joinToString(" "),
            status = ImportStatus.error,
        )
        fun fail(message: String) = base.copy(message = message)

        if (rows.size >= MAX_IMPORT_ROWS) return ImportResult(rows, "Maksimal $MAX_IMPORT_ROWS baris per file. Pecah file menjadi beberapa bagian.")
        if (nameRaw == null || nameRaw.length < 2) { rows += fail("Nama wajib diisi"); continue }
        val gender = parseGender(get("gender")) ?: run { rows += fail("Jenis kelamin harus L atau P"); null } ?: continue
        val birthDate = parseDate(get("birthDate")) ?: run { rows += fail("Tanggal lahir tidak valid (contoh 14/03/2011)"); null } ?: continue
        if (birthDate < LocalDate.of(1980, 1, 1) || birthDate > todayDate) { rows += fail("Tanggal lahir di luar rentang wajar"); continue }
        val kelompokId = findKelompok(get("kelompok"))
        if (kelompokId == AMBIGU) { rows += fail("Nama kelompok ada di beberapa desa; tulis \"Desa / Kelompok\""); continue }
        if (kelompokId == null) { rows += fail("Kelompok tidak ditemukan atau di luar lingkup Anda"); continue }
        val jenjangKey = norm(get("jenjang")).replace(Regex("[^a-z]"), "")
        val j = ctx.jenjang.firstOrNull { norm(it.name).replace(Regex("[^a-z]"), "") == jenjangKey }
            ?: run { rows += fail("Jenjang harus Pra Remaja atau Remaja"); null } ?: continue
        val katRaw = norm(get("kategori"))
        var kategoriId: String? = null
        if (j.kategori.isNotEmpty()) {
            val k = j.kategori.firstOrNull { norm(it.name) == katRaw }
            if (k == null) { rows += fail("Kategori wajib untuk ${j.name}: ${j.kategori.joinToString(" atau ") { it.name }}"); continue }
            kategoriId = k.id
        } else if (katRaw.isNotEmpty()) {
            rows += fail("${j.name} tidak memakai kategori; kosongkan kolom Kategori"); continue
        }
        val phone = normalizePhone(get("guardianPhone")) ?: run { rows += fail("No. HP tidak valid (contoh 081234567890)"); null } ?: continue

        val key = duplicateKey(kelompokId, nameRaw, birthDate)
        if (key in ctx.existingKeys) { rows += base.copy(status = ImportStatus.duplikat, message = "Sudah terdaftar di kelompok ini; dilewati"); continue }
        if (!seen.add(key)) { rows += base.copy(status = ImportStatus.duplikat, message = "Baris ganda di file ini; dilewati"); continue }

        rows += base.copy(
            status = ImportStatus.ok,
            data = GenerusInsert(
                kelompokId = kelompokId,
                name = nameRaw,
                gender = gender,
                birthDate = birthDate,
                jenjangId = j.id,
                kategoriId = kategoriId,
                school = text(get("school"), 120),
                guardianName = text(get("guardianName"), 120),
                guardianPhone = phone.ifEmpty { null },
                notes = text(get("notes"), 500),
            ),
        )
    }
    return ImportResult(rows)
}
