package id.generus.app.data

import id.generus.app.data.model.TreeUnit
import id.generus.app.data.model.UnitLevel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable

@Serializable
data class UnitRef(val id: String, val name: String, val level: UnitLevel, val parentId: String? = null)

/** Semua unit (nama unit boleh dibaca semua akun), urut nama. */
suspend fun SupabaseClient.allUnits(): List<UnitRef> =
    from("units").select(Columns.list("id", "name", "level", "parent_id")) { order("name", Order.ASCENDING) }.decodeList()

/**
 * Pohon unit untuk pemilih wilayah: unit yang boleh dipilih beserta semua leluhurnya
 * (daerah → desa) agar tampil berjenjang. Leluhur yang tidak boleh dipilih hanya
 * menjadi judul cabang. Padanan buildUnitTree di PWA (src/lib/unit-tree.ts).
 */
fun buildUnitTree(all: List<UnitRef>, selectableIds: Collection<String>): List<TreeUnit> {
    val pick = selectableIds.toSet()
    if (pick.isEmpty()) return emptyList()
    val byId = all.associateBy { it.id }
    val keep = HashSet<String>()
    for (id in pick) {
        var u = byId[id]
        while (u != null && keep.add(u.id)) u = u.parentId?.let(byId::get)
    }
    return all.filter { it.id in keep }.map { TreeUnit(it.id, it.name, it.level, it.parentId, it.id in pick) }
}

/** Daftar unit untuk pemilih wilayah, disimpan sebentar di memori agar setiap form tidak mengunduh ulang. */
class UnitDirectory(private val client: SupabaseClient) {
    private var cache: List<UnitRef>? = null
    private var loadedAt = 0L

    suspend fun all(): List<UnitRef> {
        val fresh = cache?.takeIf { System.currentTimeMillis() - loadedAt < 5 * 60_000 }
        if (fresh != null) return fresh
        return client.allUnits().also { cache = it; loadedAt = System.currentTimeMillis() }
    }

    /** Lupakan cache (setelah wilayah ditambah/diubah/dihapus). */
    fun invalidate() { cache = null }

    suspend fun tree(selectableIds: Collection<String>): List<id.generus.app.data.model.TreeUnit> = buildUnitTree(all(), selectableIds)
}
