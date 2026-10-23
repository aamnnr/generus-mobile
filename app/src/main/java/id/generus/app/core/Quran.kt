package id.generus.app.core

/**
 * Posisi Al-Qur'an untuk lintasan kitab berunit `ayat`: disimpan sebagai nomor
 * ayat urut mushaf (1 = Al-Fatihah 1, 6236 = An-Nas 6), ditampilkan surat:ayat.
 * Padanan src/lib/quran.ts.
 */
object Quran {
    data class Surah(val name: String, val ayat: Int)

    val SURAH: List<Surah> = listOf(
        "Al-Fatihah" to 7, "Al-Baqarah" to 286, "Ali 'Imran" to 200, "An-Nisa'" to 176, "Al-Ma'idah" to 120,
        "Al-An'am" to 165, "Al-A'raf" to 206, "Al-Anfal" to 75, "At-Taubah" to 129, "Yunus" to 109,
        "Hud" to 123, "Yusuf" to 111, "Ar-Ra'd" to 43, "Ibrahim" to 52, "Al-Hijr" to 99,
        "An-Nahl" to 128, "Al-Isra'" to 111, "Al-Kahf" to 110, "Maryam" to 98, "Taha" to 135,
        "Al-Anbiya'" to 112, "Al-Hajj" to 78, "Al-Mu'minun" to 118, "An-Nur" to 64, "Al-Furqan" to 77,
        "Asy-Syu'ara'" to 227, "An-Naml" to 93, "Al-Qasas" to 88, "Al-'Ankabut" to 69, "Ar-Rum" to 60,
        "Luqman" to 34, "As-Sajdah" to 30, "Al-Ahzab" to 73, "Saba'" to 54, "Fatir" to 45,
        "Yasin" to 83, "As-Saffat" to 182, "Sad" to 88, "Az-Zumar" to 75, "Gafir" to 85,
        "Fussilat" to 54, "Asy-Syura" to 53, "Az-Zukhruf" to 89, "Ad-Dukhan" to 59, "Al-Jasiyah" to 37,
        "Al-Ahqaf" to 35, "Muhammad" to 38, "Al-Fath" to 29, "Al-Hujurat" to 18, "Qaf" to 45,
        "Az-Zariyat" to 60, "At-Tur" to 49, "An-Najm" to 62, "Al-Qamar" to 55, "Ar-Rahman" to 78,
        "Al-Waqi'ah" to 96, "Al-Hadid" to 29, "Al-Mujadilah" to 22, "Al-Hasyr" to 24, "Al-Mumtahanah" to 13,
        "As-Saff" to 14, "Al-Jumu'ah" to 11, "Al-Munafiqun" to 11, "At-Tagabun" to 18, "At-Talaq" to 12,
        "At-Tahrim" to 12, "Al-Mulk" to 30, "Al-Qalam" to 52, "Al-Haqqah" to 52, "Al-Ma'arij" to 44,
        "Nuh" to 28, "Al-Jinn" to 28, "Al-Muzzammil" to 20, "Al-Muddassir" to 56, "Al-Qiyamah" to 40,
        "Al-Insan" to 31, "Al-Mursalat" to 50, "An-Naba'" to 40, "An-Nazi'at" to 46, "'Abasa" to 42,
        "At-Takwir" to 29, "Al-Infitar" to 19, "Al-Mutaffifin" to 36, "Al-Insyiqaq" to 25, "Al-Buruj" to 22,
        "At-Tariq" to 17, "Al-A'la" to 19, "Al-Gasyiyah" to 26, "Al-Fajr" to 30, "Al-Balad" to 20,
        "Asy-Syams" to 15, "Al-Lail" to 21, "Ad-Duha" to 11, "Asy-Syarh" to 8, "At-Tin" to 8,
        "Al-'Alaq" to 19, "Al-Qadr" to 5, "Al-Bayyinah" to 8, "Az-Zalzalah" to 8, "Al-'Adiyat" to 11,
        "Al-Qari'ah" to 11, "At-Takasur" to 8, "Al-'Asr" to 3, "Al-Humazah" to 9, "Al-Fil" to 5,
        "Quraisy" to 4, "Al-Ma'un" to 7, "Al-Kausar" to 3, "Al-Kafirun" to 6, "An-Nasr" to 3,
        "Al-Lahab" to 5, "Al-Ikhlas" to 4, "Al-Falaq" to 5, "An-Nas" to 6,
    ).map { (name, ayat) -> Surah(name, ayat) }

    const val TOTAL_AYAT = 6236

    /** offset[i] = jumlah ayat sebelum surat ke-(i+1). */
    private val OFFSET: IntArray = IntArray(SURAH.size).also { o ->
        for (i in 1 until SURAH.size) o[i] = o[i - 1] + SURAH[i - 1].ayat
    }

    /** Awal tiap juz (mushaf Madinah), sebagai surat ke-, ayat. */
    private val JUZ_START = listOf(
        1 to 1, 2 to 142, 2 to 253, 3 to 93, 4 to 24, 4 to 148, 5 to 82, 6 to 111, 7 to 88, 8 to 41,
        9 to 93, 11 to 6, 12 to 53, 15 to 1, 17 to 1, 18 to 75, 21 to 1, 23 to 1, 25 to 21, 27 to 56,
        29 to 46, 33 to 31, 36 to 28, 39 to 32, 41 to 47, 46 to 1, 51 to 31, 58 to 1, 67 to 1, 78 to 1,
    )

    /** surat:ayat → nomor ayat urut (1–6236). Null bila di luar batas. */
    fun toIndex(surah: Int, ayat: Int): Int? {
        val s = SURAH.getOrNull(surah - 1) ?: return null
        if (ayat < 1 || ayat > s.ayat) return null
        return OFFSET[surah - 1] + ayat
    }

    /** Nomor ayat urut → (surat, ayat). */
    fun fromIndex(index: Int): Pair<Int, Int> {
        require(index in 1..TOTAL_AYAT) { "Nomor ayat harus 1–$TOTAL_AYAT" }
        var lo = 0
        var hi = SURAH.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) shr 1
            if (OFFSET[mid] < index) lo = mid else hi = mid - 1
        }
        return (lo + 1) to (index - OFFSET[lo])
    }

    /** Juz (1–30) tempat ayat ini berada. */
    fun juzOf(index: Int): Int {
        var juz = 1
        for ((i, start) in JUZ_START.withIndex()) {
            if (toIndex(start.first, start.second)!! <= index) juz = i + 1 else break
        }
        return juz
    }

    /** mis. 256 → "Al-Baqarah 249". */
    fun format(index: Int): String {
        val (surah, ayat) = fromIndex(index)
        return "${SURAH[surah - 1].name} $ayat"
    }

    private fun normalize(s: String): String = s.lowercase()
        .replace(Regex("^(surat|surah|qs\\.?)\\s+"), "")
        .replace(Regex("[^a-z]"), "")
        .replace('o', 'a')
        .replace('e', 'i')

    private val BY_NAME: Map<String, Int> = SURAH.withIndex().associate { (i, s) -> normalize(s.name) to i + 1 }

    /**
     * Baca posisi dari teks: "2:253", "2 253", "Al-Baqarah 253", "Ali Imron 91".
     * Ejaan nama dilonggarkan (tanda baca, o→a, e→i). Null bila tidak dikenali.
     */
    fun parse(text: String): Int? {
        val t = text.trim()
        val numeric = Regex("^(\\d{1,3})\\s*[: ]\\s*(\\d{1,3})$").find(t)
        val named = Regex("^(.*?)[\\s:]+(\\d{1,3})$").find(t)
        val (surah, ayat) = when {
            numeric != null -> numeric.groupValues[1].toInt() to numeric.groupValues[2].toInt()
            named != null -> (BY_NAME[normalize(named.groupValues[1])] ?: return null) to named.groupValues[2].toInt()
            else -> return null
        }
        return toIndex(surah, ayat)
    }
}
