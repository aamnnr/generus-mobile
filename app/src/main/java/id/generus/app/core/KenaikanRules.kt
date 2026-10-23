package id.generus.app.core

import java.time.LocalDate
import java.time.Period

// Aturan kenaikan jenjang/kategori (PRD F-04): sistem memberi saran, admin yang
// memutuskan. Padanan src/lib/kenaikan-rules.ts.

data class JenjangRef(val jenjang: String, val kategori: String?)

enum class Transition(val label: String, val hint: String, val from: JenjangRef, val to: JenjangRef) {
    PraKePelajar("Pra Remaja → Remaja Pelajar", "Lulus SLTP dan melanjutkan sekolah.", JenjangRef("Pra Remaja", null), JenjangRef("Remaja", "Pelajar")),
    PraKeMandiri("Pra Remaja → Remaja Mandiri", "Lulus SLTP dan tidak melanjutkan sekolah.", JenjangRef("Pra Remaja", null), JenjangRef("Remaja", "Mandiri")),
    PelajarKeMandiri("Remaja Pelajar → Remaja Mandiri", "Sudah selesai sekolah/kuliah.", JenjangRef("Remaja", "Pelajar"), JenjangRef("Remaja", "Mandiri")),
}

/** 1 Juli tahun ajaran berjalan: Juli–Desember → tahun ini, Januari–Juni → tahun lalu. */
fun academicYearStart(today: LocalDate): LocalDate = LocalDate.of(if (today.monthValue >= 7) today.year else today.year - 1, 7, 1)

/**
 * Apakah generus disarankan naik untuk transisi ini.
 * - Pra Remaja → Remaja: usia ≥ 15 pada awal tahun ajaran, atau tercatat kelas 9.
 * - Pelajar → Mandiri: usia ≥ 23, atau tercatat lulus/kuliah semester akhir.
 */
fun isSuggested(t: Transition, birthDate: LocalDate, school: String?, today: LocalDate): Boolean {
    val age = Period.between(birthDate, academicYearStart(today)).years
    val s = (school ?: "").lowercase()
    return if (t == Transition.PelajarKeMandiri) {
        age >= 23 || Regex("\\blulus\\b").containsMatchIn(s) || Regex("semester\\s*(8|9|1[0-4])\\b").containsMatchIn(s)
    } else {
        age >= 15 || Regex("kelas\\s*9\\b").containsMatchIn(s) || Regex("\\bix\\b").containsMatchIn(s)
    }
}
