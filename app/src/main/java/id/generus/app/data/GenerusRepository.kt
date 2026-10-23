package id.generus.app.data

import id.generus.app.data.model.GenerusDetail
import id.generus.app.data.model.GenerusPage
import id.generus.app.data.model.GenerusStatus
import id.generus.app.data.model.GenerusWrite
import id.generus.app.data.model.KartuRow
import id.generus.app.data.model.PromotionPage
import id.generus.app.data.model.ScopeUnit
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import id.generus.app.data.model.IsoDate
import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Filter jenjang gabungan jenjang + kategori (padanan JENJANG_FILTERS). */
enum class JenjangFilter(val label: String, val jenjang: String, val kategori: String?) {
    PraRemaja("Pra Remaja", "Pra Remaja", null),
    Remaja("Remaja (semua)", "Remaja", null),
    RemajaPelajar("Remaja Pelajar", "Remaja", "Pelajar"),
    RemajaMandiri("Remaja Mandiri", "Remaja", "Mandiri"),
}

data class GenerusFilters(
    val q: String = "",
    val unitId: String? = null,
    val jenjang: JenjangFilter? = null,
    /** null = semua status. */
    val status: GenerusStatus? = GenerusStatus.aktif,
)

const val GENERUS_PAGE_SIZE = 50

@Serializable
data class GenerusKey(val id: String, val kelompokId: String, val name: String, val birthDate: IsoDate)

/** Data generus (padanan src/lib/generus.ts + src/app/(app)/generus/actions.ts). */
class GenerusRepository(private val client: SupabaseClient) {

    suspend fun list(f: GenerusFilters, page: Int): GenerusPage = client.rpc(
        "mobile_generus_list",
        buildJsonObject {
            put("p_q", f.q.trim().ifEmpty { null })
            put("p_unit", f.unitId)
            put("p_jenjang", f.jenjang?.jenjang)
            put("p_kategori", f.jenjang?.kategori)
            put("p_status", f.status?.name ?: "semua")
            put("p_page", page)
            put("p_page_size", GENERUS_PAGE_SIZE)
        },
    )

    /** read = filter daftar; write = kelompok tujuan generus baru; mutate = tujuan mutasi. */
    suspend fun scopeUnits(scope: String): List<ScopeUnit> = client.rpc("mobile_scope_units", buildJsonObject { put("p_scope", scope) })

    suspend fun detail(id: String): GenerusDetail? = client.rpc("mobile_generus_detail", buildJsonObject { put("p_id", id) })

    private fun GenerusWrite.json(): JsonObject = AppJson.encodeToJsonElement(this).jsonObject

    suspend fun create(kelompokId: String, data: GenerusWrite): String {
        val row = JsonObject(data.json() + ("kelompok_id" to JsonPrimitive(kelompokId)))
        return client.from("generus").insert(row) { select(Columns.list("id")) }.decodeList<IdRow>().first().id
    }

    /** False bila tidak ada baris yang berubah (di luar akses). */
    suspend fun update(id: String, data: GenerusWrite): Boolean = updateRaw(id, data.json())

    suspend fun changeStatus(id: String, status: GenerusStatus, date: LocalDate, note: String?): Boolean = updateRaw(
        id,
        buildJsonObject {
            put("status", status.name)
            put("status_date", date.toString())
            put("status_note", note)
        },
    )

    /** Trigger hanya mengizinkan admin desa/daerah yang lingkupnya mencakup asal & tujuan. */
    suspend fun mutate(id: String, kelompokId: String): Boolean = updateRaw(id, buildJsonObject { put("kelompok_id", kelompokId) })

    private suspend fun updateRaw(id: String, values: JsonObject): Boolean =
        client.from("generus").update(values) {
            select(Columns.list("id"))
            filter { eq("id", id) }
        }.decodeList<IdRow>().isNotEmpty()

    suspend fun delete(id: String): Boolean =
        client.from("generus").delete {
            select(Columns.list("id"))
            filter { eq("id", id) }
        }.decodeList<IdRow>().isNotEmpty()

    /** Terbitkan kartu baru: token QR diganti sehingga kartu lama tidak berlaku. */
    suspend fun reissueCard(id: String) = client.rpcUnit("mobile_reissue_card", buildJsonObject { put("p_id", id) })

    suspend fun kartu(unitId: String?, ids: List<String>?): List<KartuRow> = client.rpc(
        "mobile_kartu",
        buildJsonObject {
            put("p_unit", unitId)
            if (ids != null) putJsonArray("p_ids") { ids.forEach { add(JsonPrimitive(it)) } } else put("p_ids", null as String?)
        },
    )

    suspend fun promotionCandidates(jenjang: String, kategori: String?, unitId: String): PromotionPage = client.rpc(
        "mobile_promotion_candidates",
        buildJsonObject { put("p_jenjang", jenjang); put("p_kategori", kategori); put("p_unit", unitId) },
    )

    suspend fun promote(fromJenjang: String, fromKategori: String?, toJenjang: String, toKategori: String?, ids: List<String>): Int = client.rpc(
        "mobile_promote",
        buildJsonObject {
            put("p_from_jenjang", fromJenjang)
            put("p_from_kategori", fromKategori)
            put("p_to_jenjang", toJenjang)
            put("p_to_kategori", toKategori)
            putJsonArray("p_ids") { ids.forEach { add(JsonPrimitive(it)) } }
        },
    )

    /**
     * Kunci (kelompok|nama|tgl lahir) semua generus yang terlihat, untuk deteksi duplikat
     * import. Diambil per 1.000 baris karena batas baris PostgREST.
     */
    suspend fun existingKeys(): Set<String> {
        val out = HashSet<String>()
        var from = 0L
        while (true) {
            val page = client.from("generus").select(Columns.list("id", "kelompok_id", "name", "birth_date")) {
                order("id", Order.ASCENDING)
                range(from, from + KEY_PAGE - 1)
            }.decodeList<GenerusKey>()
            page.forEach { out += duplicateKey(it.kelompokId, it.name, it.birthDate) }
            if (page.size < KEY_PAGE) return out
            from += KEY_PAGE
        }
    }

    /**
     * Import massal dalam satu permintaan (satu pernyataan INSERT = satu transaksi):
     * semua tersimpan atau tidak sama sekali. RLS tetap memastikan lingkup tiap baris.
     */
    suspend fun insertMany(rows: List<JsonObject>): Int {
        if (rows.isEmpty()) return 0
        client.from("generus").insert(rows)
        return rows.size
    }

    private companion object {
        const val KEY_PAGE = 1000L
    }
}
