package id.generus.app.data

import id.generus.app.data.model.CapaianStatus
import id.generus.app.data.model.GenerusCapaian
import id.generus.app.data.model.KelompokSummary
import id.generus.app.data.model.Period
import id.generus.app.data.model.RekapTarget
import id.generus.app.data.model.RekapUnit
import id.generus.app.data.model.TargetRosterEntry
import id.generus.app.data.model.UnitNode
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.time.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class AchievementChange(val targetId: String, val generusId: String, val status: CapaianStatus)

/** Periode terbit yang sedang berjalan; bila tidak ada, periode terbit terakhir yang sudah dimulai. */
fun pickActivePeriod(periods: List<Period>, today: LocalDate): Period? {
    val published = periods.filter { it.publishedAt != null }
    return published.firstOrNull { it.startsOn <= today && today <= it.endsOn }
        ?: published.firstOrNull { it.startsOn <= today }
        ?: published.lastOrNull()
}

/** Capaian, penyampaian & progres kitab, rekap (padanan src/lib/capaian.ts + capaian/actions.ts). */
class CapaianRepository(private val client: SupabaseClient) {

    suspend fun kelompokSummary(periodId: String, jenjangId: String, kelompokId: String): KelompokSummary = client.rpc(
        "mobile_kelompok_summary",
        buildJsonObject { put("p_period", periodId); put("p_jenjang", jenjangId); put("p_kelompok", kelompokId) },
    )

    suspend fun targetRoster(targetId: String, kelompokId: String): List<TargetRosterEntry> =
        client.rpc("mobile_target_roster", buildJsonObject { put("p_target", targetId); put("p_kelompok", kelompokId) })

    suspend fun generusCapaian(generusId: String, periodId: String): GenerusCapaian =
        client.rpc("mobile_generus_capaian", buildJsonObject { put("p_generus", generusId); put("p_period", periodId) })

    /** "Belum" menghapus baris capaian. */
    suspend fun saveAchievements(changes: List<AchievementChange>): Int = client.rpc(
        "mobile_save_achievements",
        buildJsonObject {
            put("p_entries", buildJsonArray {
                changes.forEach { c ->
                    add(buildJsonObject { put("target_id", c.targetId); put("generus_id", c.generusId); put("status", c.status.name) })
                }
            })
        },
    )

    /** Tandai/batalkan "sudah disampaikan" untuk target per kelompok. */
    suspend fun setDelivery(targetId: String, kelompokId: String, delivered: Boolean, deliveredOn: LocalDate, note: String?) {
        val t = client.from("target_deliveries")
        if (!delivered) {
            t.delete { filter { eq("target_id", targetId); eq("kelompok_id", kelompokId) } }
            return
        }
        t.upsert(
            buildJsonObject {
                put("target_id", targetId)
                put("kelompok_id", kelompokId)
                put("delivered_on", deliveredOn.toString())
                put("note", note?.ifBlank { null })
            },
        ) { onConflict = "target_id,kelompok_id" }
    }

    /** Catat posisi kitab yang sama untuk beberapa generus (posisi sudah diurai). */
    suspend fun saveKitabProgress(kitabId: String, generusIds: List<String>, position: Int, recordedOn: LocalDate): Int = client.rpc(
        "mobile_save_kitab_progress",
        buildJsonObject {
            put("p_kitab", kitabId)
            putJsonArray("p_generus") { generusIds.forEach { add(JsonPrimitive(it)) } }
            put("p_position", position)
            put("p_recorded_on", recordedOn.toString())
        },
    )

    suspend fun rekapUnits(periodId: String, jenjangId: String?, unitIds: List<String>): List<RekapUnit> {
        if (unitIds.isEmpty()) return emptyList()
        return client.rpc(
            "mobile_rekap_units",
            buildJsonObject {
                put("p_period", periodId)
                put("p_jenjang", jenjangId)
                putJsonArray("p_units") { unitIds.forEach { add(JsonPrimitive(it)) } }
            },
        )
    }

    suspend fun rekapTargets(periodId: String, jenjangId: String, unitId: String): Map<String, RekapTarget> =
        client.rpc<List<RekapTarget>>(
            "mobile_rekap_targets",
            buildJsonObject { put("p_period", periodId); put("p_jenjang", jenjangId); put("p_unit", unitId) },
        ).associateBy { it.targetId }

    /** Unit beserta leluhurnya (breadcrumb) dan anak langsungnya. */
    suspend fun unitWithChildren(unitId: String): List<UnitNode> =
        client.rpc("mobile_unit_with_children", buildJsonObject { put("p_unit", unitId) })
}
