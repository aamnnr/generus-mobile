package id.generus.app.data

import id.generus.app.core.UserError
import id.generus.app.data.model.Account
import id.generus.app.data.model.AppRole
import id.generus.app.data.model.UnitLevel
import id.generus.app.data.model.WilayahUnit
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class AdminAkunResult(
    val ok: Boolean = false,
    val error: String? = null,
    val userId: String? = null,
    val tempPassword: String? = null,
)

/**
 * Wilayah & akun (superadmin). Operasi Supabase Auth admin (buat akun, reset
 * password, blokir) memerlukan service role key, jadi lewat Edge Function
 * `admin-akun` (supabase/functions/admin-akun di proyek PWA), bukan dari aplikasi.
 */
class AdminRepository(private val client: SupabaseClient) {

    suspend fun wilayah(): List<WilayahUnit> = client.rpc("mobile_wilayah")

    /** Trigger units_before_write memastikan desa di bawah daerah & kelompok di bawah desa. */
    suspend fun addUnit(parentId: String, level: UnitLevel, name: String) {
        client.from("units").insert(buildJsonObject { put("parent_id", parentId); put("level", level.name); put("name", name) })
    }

    suspend fun renameUnit(id: String, name: String) {
        client.from("units").update({ set("name", name) }) { filter { eq("id", id) } }
    }

    /** Ditolak database (FK restrict) bila masih punya kelompok atau generus. */
    suspend fun deleteUnit(id: String) {
        client.from("units").delete { filter { eq("id", id) } }
    }

    suspend fun accounts(q: String?): List<Account> =
        client.rpc("mobile_accounts", buildJsonObject { put("p_q", q?.ifBlank { null }); put("p_id", null as String?) })

    suspend fun account(id: String): Account? =
        client.rpc<List<Account>>("mobile_accounts", buildJsonObject { put("p_q", null as String?); put("p_id", id) }).firstOrNull()

    suspend fun updateProfile(id: String, name: String, phone: String?) {
        client.from("profiles").update({ set("name", name); set("phone", phone) }) { filter { eq("id", id) } }
    }

    suspend fun addRole(userId: String, unitId: String, role: AppRole) {
        client.from("user_roles").insert(buildJsonObject { put("user_id", userId); put("unit_id", unitId); put("role", role.name) })
    }

    suspend fun removeRole(roleId: String, userId: String) {
        client.from("user_roles").delete { filter { eq("id", roleId); eq("user_id", userId) } }
    }

    private suspend fun adminAkun(body: JsonObject): AdminAkunResult {
        val res = client.functions.invoke("admin-akun", body)
        val parsed = runCatching { AppJson.decodeFromString<AdminAkunResult>(res.bodyAsText()) }.getOrNull()
        if (parsed == null || !parsed.ok) throw UserError(parsed?.error ?: "Gagal menghubungi layanan akun. Pastikan Edge Function admin-akun sudah dipasang.")
        return parsed
    }

    /** Membuat akun dengan password sementara (wajib diganti saat login pertama). */
    suspend fun createAccount(name: String, email: String, phone: String?, role: AppRole, unitId: String): AdminAkunResult = adminAkun(
        buildJsonObject {
            put("action", "create")
            put("name", name)
            put("email", email)
            put("phone", phone)
            put("role", role.name)
            put("unit_id", unitId)
        },
    )

    suspend fun resetPassword(id: String): AdminAkunResult = adminAkun(buildJsonObject { put("action", "reset_password"); put("user_id", id) })

    /** Nonaktif = profiles.active false (RLS) dan diblokir di Supabase Auth. */
    suspend fun setActive(id: String, active: Boolean): AdminAkunResult =
        adminAkun(buildJsonObject { put("action", "set_active"); put("user_id", id); put("active", active) })
}
