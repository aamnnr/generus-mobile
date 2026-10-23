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
    private val juli = LocalDate.of(2026, 7, 10)

    @Test fun academicYear() {
        assertEquals(LocalDate.of(2026, 7, 1), academicYearStart(juli))
        assertEquals(LocalDate.of(2025, 7, 1), academicYearStart(LocalDate.of(2026, 3, 1)))
    }

    @Test fun suggestions() {
        assertTrue(isSuggested(Transition.PraKePelajar, LocalDate.of(2011, 6, 30), null, juli))
        assertFalse(isSuggested(Transition.PraKePelajar, LocalDate.of(2012, 1, 1), null, juli))
        assertTrue(isSuggested(Transition.PraKePelajar, LocalDate.of(2012, 1, 1), "SMP 2 kelas 9", juli))
        assertTrue(isSuggested(Transition.PelajarKeMandiri, LocalDate.of(2008, 1, 1), "Lulus SMA", juli))
        assertTrue(isSuggested(Transition.PelajarKeMandiri, LocalDate.of(2005, 1, 1), "kuliah semester 8", juli))
        assertFalse(isSuggested(Transition.PelajarKeMandiri, LocalDate.of(2008, 1, 1), "kuliah semester 3", juli))
    }
}
