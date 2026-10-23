package id.generus.app.data

import id.generus.app.data.model.Gender
import id.generus.app.data.model.Jenjang
import id.generus.app.data.model.Kategori
import id.generus.app.data.model.ScopeUnit
import id.generus.app.data.model.UnitLevel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Padanan tests untuk src/lib/import/generus-import.ts di PWA.
class GenerusImportTest {
    private val ctx = ImportContext(
        kelompok = listOf(
            ScopeUnit("k1", "Sukamaju 1", UnitLevel.kelompok, "Sukamaju"),
            ScopeUnit("k2", "Barat", UnitLevel.kelompok, "Sukamaju"),
            ScopeUnit("k3", "Barat", UnitLevel.kelompok, "Cibeureum"),
        ),
        jenjang = listOf(
            Jenjang("pra", "Pra Remaja", 1),
            Jenjang("rem", "Remaja", 2, listOf(Kategori("pel", "Pelajar"), Kategori("man", "Mandiri"))),
        ),
        existingKeys = setOf(duplicateKey("k1", "Sudah Ada", LocalDate.of(2010, 1, 1))),
    )

    private val header = listOf<Any?>("No", "Nama Lengkap *", "Jenis Kelamin *", "Tanggal Lahir *", "Kelompok *", "Jenjang *", "Kategori", "No. HP Orang Tua/Wali")

    private fun run(vararg rows: List<Any?>) = validateRows(listOf(listOf<Any?>("Judul")) + listOf(header) + rows.toList(), ctx)

    @Test fun validRowWithExcelSerialAndPhone() {
        // 40179 = 2010-01-01 di Excel; nomor HP kehilangan angka 0 di depan.
        val r = run(listOf(1.0, "Ahmad", "L", 40179.0, "Sukamaju 1", "Pra Remaja", null, 81234567890.0)).rows.single()
        assertEquals(ImportStatus.ok, r.status)
        assertEquals(LocalDate.of(2010, 1, 1), r.data!!.birthDate)
        assertEquals(Gender.L, r.data!!.gender)
        assertEquals("081234567890", r.data!!.guardianPhone)
        assertNull(r.data!!.kategoriId)
    }

    @Test fun errorsAndDuplicates() {
        val res = run(
            listOf(1.0, "Budi", "X", "14/03/2011", "Sukamaju 1", "Pra Remaja"),
            listOf(2.0, "Citra", "P", "14/03/2011", "Barat", "Pra Remaja"),
            listOf(3.0, "Dewi", "P", "14/03/2011", "Cibeureum / Barat", "Remaja", "Pelajar"),
            listOf(4.0, "Eka", "P", "14/03/2011", "Sukamaju 1", "Remaja"),
            listOf(5.0, "Sudah Ada", "L", "2010-01-01", "Sukamaju 1", "Pra Remaja"),
            listOf("contoh", "Contoh: Nama", "L", "01/01/2010", "Sukamaju 1", "Pra Remaja"),
            listOf(null, null, null, null, null, null),
        )
        assertNull(res.fileError)
        assertEquals(listOf(ImportStatus.error, ImportStatus.error, ImportStatus.ok, ImportStatus.error, ImportStatus.duplikat), res.rows.map { it.status })
        assertEquals("Jenis kelamin harus L atau P", res.rows[0].message)
        assertEquals("Nama kelompok ada di beberapa desa; tulis \"Desa / Kelompok\"", res.rows[1].message)
        assertEquals("k3", res.rows[2].data!!.kelompokId)
        assertEquals("Kategori wajib untuk Remaja: Pelajar atau Mandiri", res.rows[3].message)
    }

    @Test fun missingHeader() {
        assertEquals("Kolom \"Nama Lengkap\" tidak ditemukan. Gunakan template resmi.", validateRows(listOf(listOf("a", "b")), ctx).fileError)
    }
}
