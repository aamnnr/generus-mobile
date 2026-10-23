package id.generus.app.data

import id.generus.app.core.ASPEK_ORDER
import id.generus.app.data.model.Aspek
import id.generus.app.data.model.KitabItem
import id.generus.app.data.model.KitabWrite
import id.generus.app.data.model.MapelName
import id.generus.app.data.model.MateriItem
import id.generus.app.data.model.MateriWrite
import id.generus.app.data.model.Period
import id.generus.app.data.model.PeriodWrite
import id.generus.app.data.model.Target
import id.generus.app.data.model.TargetWrite
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@kotlinx.serialization.Serializable
data class TargetRef(val periodId: String, val jenjangId: String)

/** Kurikulum: periode, target, pustaka materi, kitab (padanan src/lib/kurikulum.ts + kurikulum/actions.ts). */
class KurikulumRepository(private val client: SupabaseClient) {

    /** Periode terbaru dulu. Draf hanya terlihat oleh penyusun (RLS). */
    suspend fun periods(): List<Period> = client.rpc("mobile_periods")

    suspend fun createPeriod(name: String, semester: String, startsOn: LocalDate, endsOn: LocalDate, copyFrom: String?): String =
        client.rpc(
            "mobile_create_period",
            buildJsonObject {
                put("p_name", name)
                put("p_semester", semester)
                put("p_starts_on", startsOn.toString())
                put("p_ends_on", endsOn.toString())
                put("p_copy_from", copyFrom)
            },
        )

    suspend fun updatePeriod(id: String, data: PeriodWrite) {
        client.from("curriculum_periods").update(data) { filter { eq("id", id) } }
    }

    suspend fun setPublished(id: String, publish: Boolean) {
        client.from("curriculum_periods").update({ set("published_at", if (publish) Instant.now().toString() else null) }) {
            filter { eq("id", id) }
        }
    }

    suspend fun deletePeriod(id: String) {
        client.from("curriculum_periods").delete { filter { eq("id", id) } }
    }

    /** Target satu periode untuk satu jenjang, urut aspek → urutan. */
    suspend fun targets(periodId: String, jenjangId: String): List<Target> =
        client.rpc<List<Target>>("mobile_targets", buildJsonObject { put("p_period", periodId); put("p_jenjang", jenjangId) })
            .sortedWith(compareBy<Target> { ASPEK_ORDER.indexOf(it.aspek) }.thenBy { it.sortOrder })

    /** Satu target beserta periodenya; null bila tidak terlihat. */
    suspend fun target(id: String): Target? {
        val ref = client.from("period_targets").select(Columns.list("period_id", "jenjang_id")) { filter { eq("id", id) } }
            .decodeList<TargetRef>().firstOrNull() ?: return null
        return targets(ref.periodId, ref.jenjangId).firstOrNull { it.id == id }
    }

    suspend fun insertTarget(row: TargetWrite) {
        client.from("period_targets").insert(row)
    }

    suspend fun updateTarget(id: String, row: TargetWrite) {
        client.from("period_targets").update(row) { filter { eq("id", id) } }
    }

    suspend fun deleteTarget(id: String) {
        client.from("period_targets").delete { filter { eq("id", id) } }
    }

    suspend fun moveTarget(id: String, up: Boolean) =
        client.rpcUnit("mobile_move_target", buildJsonObject { put("p_id", id); put("p_up", up) })

    suspend fun materi(q: String? = null, aspek: Aspek? = null, archived: Boolean = false): List<MateriItem> =
        client.rpc<List<MateriItem>>(
            "mobile_materi",
            buildJsonObject { put("p_q", q?.ifBlank { null }); put("p_aspek", aspek?.name); put("p_archived", archived) },
        ).sortedBy { ASPEK_ORDER.indexOf(it.aspek) }

    /** Simpan materi baru atau ubah; mengembalikan id. */
    suspend fun saveMateri(id: String?, data: MateriWrite): String =
        if (id == null) {
            client.from("materi").insert(data) { select(Columns.list("id")) }.decodeList<IdRow>().first().id
        } else {
            client.from("materi").update(data) { filter { eq("id", id) } }
            id
        }

    suspend fun setMateriArchived(id: String, archived: Boolean) {
        client.from("materi").update({ set("archived", archived) }) { filter { eq("id", id) } }
    }

    suspend fun kitab(includeArchived: Boolean = false): List<KitabItem> =
        client.rpc("mobile_kitab", buildJsonObject { put("p_include_archived", includeArchived) })

    suspend fun saveKitab(id: String?, data: KitabWrite) {
        if (id == null) client.from("kitab").insert(data) else client.from("kitab").update(data) { filter { eq("id", id) } }
    }

    suspend fun setKitabArchived(id: String, archived: Boolean) {
        client.from("kitab").update({ set("archived", archived) }) { filter { eq("id", id) } }
    }

    /** Nama mapel yang pernah dipakai per aspek (saran isian). */
    suspend fun mapelNames(): Map<Aspek, List<String>> =
        client.rpc<List<MapelName>>("mobile_mapel_names").groupBy({ it.aspek }, { it.mapel })
}
