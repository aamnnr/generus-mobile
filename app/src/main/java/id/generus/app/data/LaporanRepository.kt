package id.generus.app.data

import id.generus.app.core.APP_ZONE
import id.generus.app.core.range
import id.generus.app.data.model.KehadiranUnit
import id.generus.app.data.model.KelompokRef
import id.generus.app.data.model.LaporanGenerusRow
import id.generus.app.data.model.PeringatanAlpa
import id.generus.app.data.model.TrenRow
import io.github.jan.supabase.SupabaseClient
import java.time.Instant
import java.time.YearMonth
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class TrenPoint(val month: YearMonth, val total: Int, val rate: Int?)

/** Laporan bulanan (padanan src/lib/laporan.ts). Agregat lewat fungsi app.kehadiran_* / app.laporan_generus. */
class LaporanRepository(private val client: SupabaseClient) {

    suspend fun kehadiranUnits(from: Instant, to: Instant, unitIds: List<String>): List<KehadiranUnit> {
        if (unitIds.isEmpty()) return emptyList()
        return client.rpc(
            "mobile_kehadiran_units",
            buildJsonObject {
                put("p_from", from.toString())
                put("p_to", to.toString())
                putJsonArray("p_units") { unitIds.forEach { add(JsonPrimitive(it)) } }
            },
        )
    }

    suspend fun tren(unitId: String, months: List<YearMonth>): List<TrenPoint> {
        val sorted = months.sorted()
        val from = sorted.first().range().first
        val to = sorted.last().range().second
        val rows = client.rpc<List<TrenRow>>(
            "mobile_kehadiran_tren",
            buildJsonObject {
                put("p_unit", unitId)
                put("p_from", from.toString())
                put("p_to", to.toString())
                put("p_tz", APP_ZONE.id)
            },
        )
        val by = rows.associateBy { YearMonth.from(it.bulan) }
        return sorted.map { m ->
            val r = by[m]
            TrenPoint(m, r?.total ?: 0, r?.takeIf { it.total > 0 }?.let { (it.hadir * 100.0 / it.total).roundToInt() })
        }
    }

    suspend fun laporanGenerus(from: Instant, to: Instant, unitId: String, periodId: String?): List<LaporanGenerusRow> = client.rpc(
        "mobile_laporan_generus",
        buildJsonObject {
            put("p_from", from.toString())
            put("p_to", to.toString())
            put("p_unit", unitId)
            put("p_period", periodId)
        },
    )

    suspend fun peringatanAlpa(unitId: String): List<PeringatanAlpa> =
        client.rpc("mobile_peringatan_alpa", buildJsonObject { put("p_unit", unitId) })

    suspend fun kelompokUnder(unitId: String): List<KelompokRef> =
        client.rpc("mobile_kelompok_under", buildJsonObject { put("p_unit", unitId) })
}
