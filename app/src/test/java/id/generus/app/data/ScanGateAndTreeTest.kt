package id.generus.app.data

import id.generus.app.data.model.UnitLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanGateTest {
    @Test fun sameCodeHeldInFrontOfCameraCountsOnce() {
        val g = ScanGate()
        assertTrue(g.accept("A", 0))
        // Kartu ditahan: terlihat terus tiap 200 ms selama 10 detik → tidak diproses lagi.
        for (t in 200L..10_000L step 200) assertFalse(g.accept("A", t))
        // Disingkirkan ≥ 1,5 detik lalu discan lagi → diterima.
        assertTrue(g.accept("A", 11_600))
    }

    @Test fun differentCodesAreIndependent() {
        val g = ScanGate()
        assertTrue(g.accept("A", 0))
        assertTrue(g.accept("B", 100))
        assertFalse(g.accept("A", 300))
    }

    @Test fun logMergesSameKeyAndMovesItToTop() {
        var log = emptyList<LogEntry<String>>()
        log = pushLog(log, "p:Budi", "ok", 1)
        log = pushLog(log, "p:Sari", "ok", 2)
        log = pushLog(log, "p:Budi", "dup", 3)
        assertEquals(listOf("p:Budi", "p:Sari"), log.map { it.key })
        assertEquals(2, log.first().count)
        assertEquals("dup", log.first().value)
        assertEquals(5, (1..20).fold(emptyList<LogEntry<Int>>()) { l, i -> pushLog(l, "k$i", i, i.toLong(), limit = 5) }.size)
    }

    @Test fun keepPreservesFirstValueWhileLastIsNewest() {
        val keep = { prev: String, _: String -> prev == "Hadir" }
        var log = pushLog(emptyList(), "a", "Hadir", 1, keep = keep)
        log = pushLog(log, "a", "Sudah tercatat", 5, keep = keep)
        assertEquals(LogEntry("a", "Hadir", "Sudah tercatat", 1, 5, 2), log.first())
    }
}

class UnitTreeTest {
    private val all = listOf(
        UnitRef("d", "Klaten", UnitLevel.daerah),
        UnitRef("ds1", "Cawas", UnitLevel.desa, "d"),
        UnitRef("ds2", "Pedan", UnitLevel.desa, "d"),
        UnitRef("k1", "Barepan", UnitLevel.kelompok, "ds1"),
        UnitRef("k2", "Tlobong", UnitLevel.kelompok, "ds2"),
    )

    @Test fun selectableWithAncestorsOnly() {
        val tree = buildUnitTree(all, listOf("k1"))
        assertEquals(listOf("d", "ds1", "k1"), tree.map { it.id })
        assertEquals(listOf(false, false, true), tree.map { it.selectable })
    }

    @Test fun emptySelectionGivesEmptyTree() {
        assertTrue(buildUnitTree(all, emptyList()).isEmpty())
    }
}
