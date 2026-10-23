package id.generus.app.data

import id.generus.app.core.APP_ZONE
import id.generus.app.data.model.AgendaDetail
import id.generus.app.data.model.AgendaPage
import id.generus.app.data.model.AgendaUnit
import id.generus.app.data.model.AgendaWrite
import id.generus.app.data.model.Beranda
import id.generus.app.data.model.GenerusAttendance
import id.generus.app.data.model.Jenjang
import id.generus.app.data.model.ProfileOption
import id.generus.app.data.model.RosterDownload
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class IdRow(val id: String)

/** Agenda & presensi (padanan src/lib/agenda.ts + src/app/(app)/agenda/actions.ts). */
class AgendaRepository(private val client: SupabaseClient) {

    suspend fun beranda(): Beranda = client.rpc("mobile_beranda", buildJsonObject { put("p_tz", APP_ZONE.id) })

    suspend fun list(range: String, page: Int): AgendaPage =
        client.rpc("mobile_agendas", buildJsonObject { put("p_range", range); put("p_page", page) })

    /** Kunci agenda yang sudah lewat batas (idempoten; pengaman bila pg_cron belum aktif). */
    suspend fun lockDue() = client.rpcUnit("mobile_lock_due_agendas")

    suspend fun detail(id: String): AgendaDetail? = client.rpc("mobile_agenda", buildJsonObject { put("p_id", id) })

    suspend fun units(): List<AgendaUnit> = client.rpc("mobile_agenda_units")

    suspend fun jenjang(): List<Jenjang> =
        client.from("jenjang")
            .select(Columns.raw("id,name,sort_order,jenjang_kategori(id,name,sort_order)")) { order("sort_order", Order.ASCENDING) }
            .decodeList()

    /** Simpan satu atau beberapa agenda (berulang mingguan); mengembalikan id agenda pertama. */
    suspend fun create(rows: List<AgendaWrite>): String =
        client.from("agendas").insert(rows) { select(Columns.list("id")) }.decodeList<IdRow>()
            .firstOrNull()?.id ?: error("Agenda tidak tersimpan")

    /** Mengembalikan false bila tidak ada baris yang berubah (di luar akses). */
    suspend fun update(id: String, row: AgendaWrite): Boolean =
        client.from("agendas").update(row) {
            select(Columns.list("id"))
            filter { eq("id", id) }
        }.decodeList<IdRow>().isNotEmpty()

    suspend fun cancel(id: String, series: Boolean): Int =
        client.rpc("mobile_cancel_agenda", buildJsonObject { put("p_id", id); put("p_series", series) })

    suspend fun lock(id: String) = client.rpcUnit("mobile_lock_agenda", buildJsonObject { put("p_id", id) })

    /** Kirim antrian presensi. Entri: {generus_id, status, mode}. */
    suspend fun saveAttendance(agendaId: String, entries: JsonArray): Int =
        client.rpc("mobile_save_attendance", buildJsonObject { put("p_agenda", agendaId); put("p_entries", entries) })

    suspend fun roster(agendaId: String): RosterDownload =
        client.rpc("mobile_agenda_roster", buildJsonObject { put("p_agenda", agendaId) })

    /** Akses petugas scan berlaku sampai batas pelengkapan presensi agenda. */
    suspend fun addScanner(agendaId: String, userId: String, grantedBy: String?, expiresAt: Instant) {
        client.from("agenda_scanners").upsert(
            buildJsonObject {
                put("agenda_id", agendaId)
                put("user_id", userId)
                put("expires_at", expiresAt.toString())
                put("granted_by", grantedBy)
            },
        ) { onConflict = "agenda_id,user_id" }
    }

    suspend fun removeScanner(agendaId: String, userId: String) {
        client.from("agenda_scanners").delete { filter { eq("agenda_id", agendaId); eq("user_id", userId) } }
    }

    /** Akun yang bisa dijadikan petugas: akun aktif yang terlihat oleh pengelola. */
    suspend fun scannerCandidates(selfId: String?): List<ProfileOption> =
        client.from("profiles").select(Columns.list("id", "name", "email")) {
            filter {
                eq("active", true)
                if (selfId != null) neq("id", selfId)
            }
            order("name", Order.ASCENDING)
        }.decodeList()

    suspend fun generusAttendance(generusId: String): GenerusAttendance =
        client.rpc("mobile_generus_attendance", buildJsonObject { put("p_generus", generusId) })
}
