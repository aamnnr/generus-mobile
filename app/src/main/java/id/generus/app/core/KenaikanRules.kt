package id.generus.app.core

import java.time.LocalDate
import java.time.Period

// Aturan kenaikan jenjang/kategori (PRD F-04): sistem memberi saran, admin yang
// memutuskan. Padanan src/lib/kenaikan-rules.ts.

data class JenjangRef(val jenjang: String, val kategori: String?)

enum class Transition(val label: String, val hint: String, val from: JenjangRef, val to: JenjangRef) {
    BalitaKePaud("Balita → Caberawit PAUD/TK", "Mulai masuk PAUD/TK/KB.", JenjangRef("Balita", null), JenjangRef("Caberawit", "PAUD/TK")),
    PaudKeSd("Caberawit PAUD/TK → Caberawit SD", "Lulus TK dan masuk SD/MI.", JenjangRef("Caberawit", "PAUD/TK"), JenjangRef("Caberawit", "SD")),
    SdKePra("Caberawit SD → Pra Remaja", "Lulus SD/MI (kelas 6) dan masuk SLTP.", JenjangRef("Caberawit", "SD"), JenjangRef("Pra Remaja", null)),
    PraKePelajar("Pra Remaja → Remaja Pelajar", "Lulus SLTP dan melanjutkan sekolah.", JenjangRef("Pra Remaja", null), JenjangRef("Remaja", "Pelajar")),
    PraKeMandiri("Pra Remaja → Remaja Mandiri", "Lulus SLTP dan tidak melanjutkan sekolah.", JenjangRef("Pra Remaja", null), JenjangRef("Remaja", "Mandiri")),
    PelajarKeMandiri("Remaja Pelajar → Remaja Mandiri", "Sudah selesai sekolah/kuliah.", JenjangRef("Remaja", "Pelajar"), JenjangRef("Remaja", "Mandiri")),
}

/** "Remaja Pelajar", atau "Pra Remaja" untuk jenjang tanpa kategori. */
fun segmentLabel(x: JenjangRef): String = if (x.kategori != null) "${x.jenjang} ${x.kategori}" else x.jenjang

/** 1 Juli tahun ajaran berjalan: Juli–Desember → tahun ini, Januari–Juni → tahun lalu. */
fun academicYearStart(today: LocalDate): LocalDate = LocalDate.of(if (today.monthValue >= 7) today.year else today.year - 1, 7, 1)

/** Awal tahun ajaran (Juli–Agustus): waktu pengingat kenaikan jenjang. */
fun isPromotionSeason(today: LocalDate): Boolean = today.monthValue == 7 || today.monthValue == 8

/** Usia (tahun) pada tanggal tertentu. */
fun ageAt(birthDate: LocalDate, on: LocalDate): Int = Period.between(birthDate, on).years

/** reason: alasan singkat untuk admin, mis. "usia 15 per 1 Juli". */
data class Suggestion(val suggested: Boolean, val reason: String)

private val ROMAN = mapOf(
    "i" to 1, "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5, "vi" to 6,
    "vii" to 7, "viii" to 8, "ix" to 9, "x" to 10, "xi" to 11, "xii" to 12,
)
private val GRADE_WITH_WORD = Regex("""\b(?:kelas|kls|kl)\.?\s*(viii|vii|vi|iv|v|iii|ii|ix|i|xii|xi|x|\d{1,2})\b""", RegexOption.IGNORE_CASE)
private val GRADE_BARE = Regex("""\b(VIII|VII|IX|XII|XI|X|1[0-2]|[1-9])\s*[A-H]\b""")
private val UPPER_SCHOOL = Regex(
    """\b(sma|smk|sman|smkn|stm|slta|madrasah aliyah|man|kuliah|universitas|univ|politeknik|poltek|institut|stikes|uin|iain)\b""",
    RegexOption.IGNORE_CASE,
)
private val NOT_UPPER = Regex("""\bsmp\b|\bmts\b""", RegexOption.IGNORE_CASE)
private val LOWER_SECONDARY = Regex("""\b(smp|smpn|smpit|mts|mtsn|sltp)\b""", RegexOption.IGNORE_CASE)
private val ELEMENTARY = Regex("""\b(sd|sdn|sdit|mi|min)\b""", RegexOption.IGNORE_CASE)
private val PRESCHOOL = Regex("""\b(paud|tk|tka|kb|ra|playgroup)\b""", RegexOption.IGNORE_CASE)
private val LULUS = Regex("""\blulus\b""", RegexOption.IGNORE_CASE)
private val SEMESTER_AKHIR = Regex("""semester\s*(8|9|1[0-4])\b""", RegexOption.IGNORE_CASE)

/**
 * Kelas yang tercatat di kolom sekolah, bila ada: "kelas 9", "kls 8", "Kelas IX",
 * "kelas 3", "9A", "IX B". Tanpa kata "kelas", angka/romawi hanya dikenali bila
 * diikuti huruf rombel (A–H) agar "SMP N 9" / "SDN 2" tidak dianggap kelas.
 */
fun gradeFromSchool(school: String?): Int? {
    if (school.isNullOrEmpty()) return null
    val raw = (GRADE_WITH_WORD.find(school)?.groupValues?.get(1) ?: GRADE_BARE.find(school)?.groupValues?.get(1))?.lowercase() ?: return null
    val n = ROMAN[raw] ?: raw.toIntOrNull() ?: return null
    return n.takeIf { it in 1..12 }
}

/** Kolom sekolah menunjukkan jenjang SLTA ke atas (SMA/SMK/MA, kuliah). */
fun isUpperSchool(school: String?): Boolean =
    !school.isNullOrEmpty() && UPPER_SCHOOL.containsMatchIn(school) && !NOT_UPPER.containsMatchIn(school)

/** Kolom sekolah menunjukkan SLTP (SMP/MTs). */
fun isLowerSecondary(school: String?): Boolean = !school.isNullOrEmpty() && LOWER_SECONDARY.containsMatchIn(school)

/** Kolom sekolah menunjukkan SD/MI. */
fun isElementary(school: String?): Boolean = !school.isNullOrEmpty() && ELEMENTARY.containsMatchIn(school)

/** Kolom sekolah menunjukkan PAUD/TK/KB/RA. */
fun isPreschool(school: String?): Boolean = !school.isNullOrEmpty() && PRESCHOOL.containsMatchIn(school)

/**
 * Saran naik dari jenjang sekolah yang berakhir di kelas `last` (6 untuk SD, 9 untuk SLTP):
 * sudah di jenjang sekolah berikutnya → ya; kelas di bawah `last` → tidak; kelas `last` →
 * hanya di awal tahun ajaran (berarti baru lulus); tanpa keterangan → usia.
 */
private fun byGrade(grade: Int?, next: Boolean, last: Int, age: Int, minAge: Int, today: LocalDate): Suggestion {
    if (next || (grade != null && grade > last)) {
        return Suggestion(true, if (grade != null && grade > last) "tercatat kelas $grade" else "sekolah tercatat jenjang berikutnya")
    }
    if (grade != null && grade < last) return Suggestion(false, "tercatat kelas $grade")
    if (grade == last) {
        return if (isPromotionSeason(today)) Suggestion(true, "tercatat kelas $last, awal tahun ajaran") else Suggestion(false, "masih kelas $last (naik Juli)")
    }
    return Suggestion(age >= minAge, "usia $age per 1 Juli")
}

/**
 * Saran kenaikan untuk satu generus. Sistem hanya menyarankan; admin yang memutuskan.
 *
 * Pra Remaja → Remaja (Pelajar/Mandiri): tercatat SLTA/kuliah atau kelas 10–12 → ya;
 * kelas 7–8 → tidak; kelas 9 → hanya Juli–Agustus; tanpa keterangan → usia ≥ 15.
 * Caberawit SD → Pra Remaja: sama, dengan kelas 6 sebagai kelas akhir (SMP/MTs atau
 * kelas 7+ → ya; kelas 1–5 → tidak); tanpa keterangan → usia ≥ 12.
 * Caberawit PAUD/TK → SD: tercatat SD/MI atau kelas → ya; masih PAUD/TK di luar
 * Juli–Agustus → tidak; tanpa keterangan → usia ≥ 6.
 * Balita → PAUD/TK: tercatat PAUD/TK/KB/RA → ya; tanpa keterangan → usia ≥ 4.
 * Pelajar → Mandiri: tercatat lulus / semester akhir, atau usia ≥ 23; masih SMA (kelas
 * 10–12) tidak disarankan.
 */
fun suggestPromotion(t: Transition, birthDate: LocalDate, school: String?, today: LocalDate): Suggestion {
    val age = ageAt(birthDate, academicYearStart(today))
    val s = school ?: ""
    val grade = gradeFromSchool(s)
    val ageText = "usia $age per 1 Juli"

    return when (t) {
        Transition.BalitaKePaud ->
            if (isPreschool(s) || isElementary(s)) Suggestion(true, "tercatat sekolah") else Suggestion(age >= 4, ageText)
        Transition.PaudKeSd -> when {
            isElementary(s) || grade != null -> Suggestion(true, if (grade != null) "tercatat kelas $grade" else "tercatat SD/MI")
            isPreschool(s) && !isPromotionSeason(today) -> Suggestion(false, "masih PAUD/TK (naik Juli)")
            else -> Suggestion(age >= 6, ageText)
        }
        Transition.SdKePra -> byGrade(grade, isLowerSecondary(s) || isUpperSchool(s), 6, age, 12, today)
        Transition.PelajarKeMandiri -> when {
            LULUS.containsMatchIn(s) -> Suggestion(true, "tercatat lulus")
            SEMESTER_AKHIR.containsMatchIn(s) -> Suggestion(true, "kuliah semester akhir")
            grade != null && grade >= 10 -> Suggestion(false, "masih kelas $grade")
            else -> Suggestion(age >= 23, ageText)
        }
        Transition.PraKePelajar, Transition.PraKeMandiri -> byGrade(grade, isUpperSchool(s), 9, age, 15, today)
    }
}
