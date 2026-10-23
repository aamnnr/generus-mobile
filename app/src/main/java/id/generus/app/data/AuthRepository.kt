package id.generus.app.data

import id.generus.app.data.model.Me
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.StateFlow

/** Login, sesi, dan profil user (padanan src/app/actions/auth.ts + src/lib/auth.ts). */
class AuthRepository(private val client: SupabaseClient) {
    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    val userId: String? get() = client.auth.currentUserOrNull()?.id

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email.trim().lowercase()
            this.password = password
        }
    }

    suspend fun signOut() {
        runCatching { client.auth.signOut() }
        // Bila server tak terjangkau, sesi lokal tetap dihapus.
        client.auth.clearSession()
    }

    /** Profil + peran; null bila profil tidak terlihat (akun nonaktif). */
    suspend fun me(): Me? = client.rpc<Me?>("mobile_me")

    suspend fun changePassword(newPassword: String) {
        client.auth.updateUser { password = newPassword }
        val id = userId ?: return
        client.from("profiles").update({ set("must_change_password", false) }) { filter { eq("id", id) } }
    }
}
