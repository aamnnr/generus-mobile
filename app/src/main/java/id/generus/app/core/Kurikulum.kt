package id.generus.app.core

import id.generus.app.data.model.Aspek
import id.generus.app.data.model.CapaianStatus
import id.generus.app.data.model.KitabUnit
import kotlin.math.roundToInt

// Label & format kurikulum. Padanan src/lib/kurikulum-format.ts.

val ASPEK_ORDER = listOf(Aspek.alim, Aspek.faqih, Aspek.akhlaq, Aspek.mandiri)

val Aspek.label: String
    get() = when (this) {
        Aspek.alim -> "Alim"
        Aspek.faqih -> "Faqih"
        Aspek.akhlaq -> "Akhlaqul Karimah"
        Aspek.mandiri -> "Mandiri"
    }

val KitabUnit.label: String
    get() = when (this) {
        KitabUnit.ayat -> "Ayat Al-Qur'an"
        KitabUnit.halaman -> "Halaman"
        KitabUnit.bab -> "Bab"
    }

val CapaianStatus.label: String
    get() = when (this) {
        CapaianStatus.belum -> "Belum"
        CapaianStatus.proses -> "Proses"
        CapaianStatus.tuntas -> "Tuntas"
    }

/** Posisi kitab untuk ditampilkan: "Al-Baqarah 253", "hal 26", "bab 3". */
fun formatPosition(unit: KitabUnit, position: Int): String = when (unit) {
    KitabUnit.ayat -> Quran.format(position)
    KitabUnit.halaman -> "hal $position"
    KitabUnit.bab -> "bab $position"
}

/** Rentang target, mis. "Al-Baqarah 253 s.d. Ali 'Imran 91" atau "hal 26–38". */
fun formatRange(unit: KitabUnit, from: Int, to: Int): String {
    if (unit == KitabUnit.ayat) return if (from == to) Quran.format(from) else "${Quran.format(from)} s.d. ${Quran.format(to)}"
    val label = if (unit == KitabUnit.halaman) "hal" else "bab"
    return if (from == to) "$label $from" else "$label $from–$to"
}

/** Juz yang dicakup rentang ayat, mis. "Juz 3" atau "Juz 3–4". */
fun juzLabel(from: Int, to: Int): String {
    val a = Quran.juzOf(from)
    val b = Quran.juzOf(to)
    return if (a == b) "Juz $a" else "Juz $a–$b"
}

/** Baca isian posisi: ayat ("Al-Baqarah 253" / "2:253") atau angka halaman/bab. */
fun parsePosition(unit: KitabUnit, text: String, total: Int): Int? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val n = if (unit == KitabUnit.ayat) Quran.parse(t) else t.toIntOrNull()
    return n?.takeIf { it in 1..total }
}

/** Nilai awal isian posisi (kebalikan parsePosition). */
fun positionInput(unit: KitabUnit, position: Int?): String = when {
    position == null -> ""
    unit == KitabUnit.ayat -> Quran.format(position)
    else -> position.toString()
}

fun positionHint(unit: KitabUnit, total: Int): String =
    if (unit == KitabUnit.ayat) "mis. Al-Baqarah 253 atau 2:253" else "angka 1–$total"

/** Status target kitab dari posisi terakhir. */
fun kitabStatus(position: Int?, from: Int, to: Int): CapaianStatus = when {
    position == null -> CapaianStatus.belum
    position >= to -> CapaianStatus.tuntas
    position >= from -> CapaianStatus.proses
    else -> CapaianStatus.belum
}

/** Persentase bulat; 0 bila tidak ada sasaran. */
fun percent(part: Int, total: Int): Int = if (total > 0) (part * 100.0 / total).roundToInt() else 0
