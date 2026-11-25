package id.generus.app.core

import id.generus.app.data.model.CapaianStatus
import id.generus.app.data.model.Jenjang
import id.generus.app.data.model.Kategori
import id.generus.app.data.model.KitabUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Logika yang di-port dari PWA (src/lib/*.ts); hasil harus sama dengan tes di sana.

class QuranTest {
    @Test fun indexRoundTrip() {
        assertEquals(1, Quran.toIndex(1, 1))
        assertEquals(Quran.TOTAL_AYAT, Quran.toIndex(114, 6))
        assertEquals(2 to 253, Quran.fromIndex(Quran.toIndex(2, 253)!!))
        assertNull(Quran.toIndex(1, 8))
        assertNull(Quran.toIndex(115, 1))
    }

    @Test fun parseLooseSpelling() {
        assertEquals(Quran.toIndex(2, 253), Quran.parse("Al-Baqarah 253"))
        assertEquals(Quran.toIndex(2, 253), Quran.parse("2:253"))
        assertEquals(Quran.toIndex(2, 253), Quran.parse("2 253"))
        assertEquals(Quran.toIndex(3, 91), Quran.parse("Ali Imron 91"))
        assertEquals(Quran.toIndex(36, 1), Quran.parse("surat yasin 1"))
        assertNull(Quran.parse("Al-Baqarah 300"))
        assertNull(Quran.parse("tidak ada"))
    }

    @Test fun juz() {
        assertEquals(1, Quran.juzOf(1))
        assertEquals(3, Quran.juzOf(Quran.toIndex(2, 253)!!))
        assertEquals(30, Quran.juzOf(Quran.TOTAL_AYAT))
        assertEquals("Juz 3–4", juzLabel(Quran.toIndex(2, 253)!!, Quran.toIndex(3, 93)!!))
    }

    @Test fun kitabFormatAndStatus() {
        assertEquals("Al-Baqarah 253 s.d. Ali 'Imran 91", formatRange(KitabUnit.ayat, Quran.toIndex(2, 253)!!, Quran.toIndex(3, 91)!!))
        assertEquals("hal 26–38", formatRange(KitabUnit.halaman, 26, 38))
        assertEquals(30, parsePosition(KitabUnit.halaman, "30", 100))
        assertNull(parsePosition(KitabUnit.halaman, "101", 100))
        assertEquals(CapaianStatus.belum, kitabStatus(null, 10, 20))
        assertEquals(CapaianStatus.belum, kitabStatus(5, 10, 20))
        assertEquals(CapaianStatus.proses, kitabStatus(10, 10, 20))
        assertEquals(CapaianStatus.tuntas, kitabStatus(20, 10, 20))
    }
}

class QrTest {
    @Test fun normalize() {
        assertEquals("GNR1:0123456789ABCDEF", Qr.normalize(" gnr1:0123 4567 89ab cdef "))
        assertNull(Qr.normalize("GNR1:XYZ"))
        assertNull(Qr.normalize("https://contoh.id"))
    }

    @Test fun sha256MatchesDatabase() {
        // encode(sha256(convert_to('abc', 'UTF8')), 'hex')
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Qr.sha256Hex("abc"))
    }
}

class AgendaTargetTest {
    private val pra = Jenjang("pra", "Pra Remaja", 1)
    private val remaja = Jenjang("rem", "Remaja", 2, listOf(Kategori("pel", "Pelajar", 1), Kategori("man", "Mandiri", 2)))
    private val all = listOf(pra, remaja)

    @Test fun allOrNoneMeansNoFilter() {
        val keys = segmentsOf(all).map { it.key }.toSet()
        assertEquals(emptyList<String>() to emptyList<String>(), segmentsToTarget(keys, all))
        assertEquals(emptyList<String>() to emptyList<String>(), segmentsToTarget(emptySet(), all))
    }

    @Test fun partialKategori() {
        assertEquals(listOf("rem") to listOf("man"), segmentsToTarget(setOf("rem:man"), all))
        assertEquals(listOf("rem") to emptyList<String>(), segmentsToTarget(setOf("rem:man", "rem:pel"), all))
        assertEquals(listOf("pra", "rem") to listOf("pel"), segmentsToTarget(setOf("pra", "rem:pel"), all))
    }

    @Test fun roundTripLabel() {
        val (j, k) = segmentsToTarget(setOf("pra", "rem:pel"), all)
        assertEquals(setOf("pra", "rem:pel"), targetToSegments(j, k, all))
        assertEquals("Pra Remaja, Remaja Pelajar", targetLabel(j, k, null, all))
        assertEquals("Semua jenjang", targetLabel(emptyList(), emptyList(), null, all))
    }
}

class KenaikanTest {
    private val juli = LocalDate.of(2026, 7, 15)
    private val nov = LocalDate.of(2026, 11, 9)
    private fun d(s: String) = LocalDate.parse(s)
    private fun sug(t: Transition, birth: String, school: String?, on: LocalDate = juli) = suggestPromotion(t, d(birth), school, on)
    private fun pra(school: String?, birth: String = "2011-03-01", on: LocalDate = juli) = sug(Transition.PraKePelajar, birth, school, on)

    @Test fun calendar() {
        assertEquals(LocalDate.of(2026, 7, 1), academicYearStart(juli))
        assertEquals(LocalDate.of(2026, 7, 1), academicYearStart(LocalDate.of(2027, 3, 1)))
        assertTrue(isPromotionSeason(juli))
        assertTrue(isPromotionSeason(LocalDate.of(2026, 8, 31)))
        assertFalse(isPromotionSeason(LocalDate.of(2026, 9, 1)))
        assertEquals(15, ageAt(d("2011-07-01"), LocalDate.of(2026, 7, 1)))
        assertEquals(14, ageAt(d("2011-07-02"), LocalDate.of(2026, 7, 1)))
    }

    @Test fun suggested() {
        assertTrue(sug(Transition.PraKePelajar, "2011-06-30", null).suggested)
        assertFalse(sug(Transition.PraKePelajar, "2012-01-01", "SMP 3 kelas 8").suggested)
        assertTrue(sug(Transition.PraKeMandiri, "2012-01-01", "SMPN 1 Kelas 9").suggested)
        assertTrue(sug(Transition.PelajarKeMandiri, "2003-01-01", null).suggested)
        assertFalse(sug(Transition.PelajarKeMandiri, "2007-01-01", "SMA 2 kelas 12").suggested)
        assertTrue(sug(Transition.PelajarKeMandiri, "2004-05-05", "Kuliah semester 8").suggested)
        assertFalse(sug(Transition.PelajarKeMandiri, "2005-05-05", "Kuliah semester 3").suggested)
        assertEquals(Suggestion(false, "masih kelas 12"), sug(Transition.PelajarKeMandiri, "2002-01-01", "SMK kelas 12"))
    }

    @Test fun gradeFromSchoolField() {
        assertEquals(8, gradeFromSchool("SMPN 2 kelas 8"))
        assertEquals(9, gradeFromSchool("SMP 1 Trucuk kls 9"))
        assertEquals(7, gradeFromSchool("SMP N 3 Kelas VII"))
        assertEquals(9, gradeFromSchool("SMPN 1 Cawas 9A"))
        assertEquals(9, gradeFromSchool("SMP Muh IX B"))
        assertEquals(null, gradeFromSchool("SMP N 9 Klaten"))
        assertEquals(null, gradeFromSchool("SMP N 2 Cawas"))
        assertEquals(null, gradeFromSchool(null))
    }

    @Test fun caberawitDanBalita() {
        val nov14 = LocalDate.of(2026, 11, 14)
        val jul10 = LocalDate.of(2026, 7, 10)
        fun s(t: Transition, birth: String, school: String?, on: LocalDate = nov14) = suggestPromotion(t, d(birth), school, on)
        assertEquals(3, gradeFromSchool("SDN 2 Cawas kelas 3"))
        assertEquals(6, gradeFromSchool("Kelas VI"))
        assertEquals(6, gradeFromSchool("6A"))
        assertEquals(null, gradeFromSchool("SDN 2 Cawas"))
        assertFalse(s(Transition.SdKePra, "2013-01-01", "SDN 1 kelas 5").suggested)
        assertFalse(s(Transition.SdKePra, "2014-01-01", "SDN 1 kelas 6").suggested)
        assertTrue(s(Transition.SdKePra, "2014-01-01", "SDN 1 kelas 6", jul10).suggested)
        assertTrue(s(Transition.SdKePra, "2015-01-01", "SMPN 3 Klaten").suggested)
        assertEquals(Suggestion(true, "usia 12 per 1 Juli"), s(Transition.SdKePra, "2014-03-01", null))
        assertFalse(s(Transition.SdKePra, "2015-03-01", null).suggested)
        assertTrue(s(Transition.PaudKeSd, "2020-01-01", "MI Muhammadiyah").suggested)
        assertFalse(s(Transition.PaudKeSd, "2020-01-01", "TK Aisyiyah").suggested)
        assertTrue(s(Transition.PaudKeSd, "2020-01-01", null).suggested)
        assertFalse(s(Transition.PaudKeSd, "2021-01-01", null).suggested)
        assertTrue(s(Transition.BalitaKePaud, "2024-01-01", "PAUD Melati").suggested)
        assertTrue(s(Transition.BalitaKePaud, "2022-03-01", null).suggested)
        assertFalse(s(Transition.BalitaKePaud, "2023-03-01", null).suggested)
        assertEquals("Pra Remaja", segmentLabel(JenjangRef("Pra Remaja", null)))
        assertEquals("Caberawit SD", segmentLabel(JenjangRef("Caberawit", "SD")))
    }

    @Test fun praRemajaOnlyAfterGrade9() {
        assertEquals(Suggestion(false, "tercatat kelas 7"), pra("SMPN 1 kelas 7"))
        assertFalse(pra("SMPN 1 kelas 8").suggested)
        assertTrue(pra("SMPN 1 kelas 9", "2012-01-01", juli).suggested)
        assertEquals(Suggestion(false, "masih kelas 9 (naik Juli)"), pra("SMPN 1 kelas 9", "2011-03-01", nov))
        assertTrue(pra("SMA N 1 Cawas", "2012-01-01").suggested)
        assertTrue(pra("SMK Muh 2 Klaten", "2012-01-01").suggested)
        assertTrue(pra("SMA/K N 1 Pedan", "2012-01-01").suggested)
        assertTrue(pra("kelas 10", "2012-01-01").suggested)
        assertFalse(pra("MTs N 1 Klaten", "2012-01-01").suggested)
        assertEquals(Suggestion(true, "usia 15 per 1 Juli"), pra("SMP N 2 Cawas", "2011-06-30"))
        assertFalse(pra(null, "2011-07-02").suggested)
    }
}
