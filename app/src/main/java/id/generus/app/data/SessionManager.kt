package id.generus.app.data

import android.content.Context
import id.generus.app.core.isNetwork
import id.generus.app.data.model.Me
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    /** secrets.properties belum diisi saat build. */
    data object NotConfigured : SessionState
    data class SignedOut(val notice: String? = null) : SessionState
    data class MustChangePassword(val me: Me) : SessionState
    /** offline = profil dari cache karena server tak terjangkau. */
    data class SignedIn(val me: Me, val offline: Boolean = false) : SessionState
    data class Failed(val message: String) : SessionState
}

/**
 * Sesi aplikasi: status Supabase Auth + profil & peran (padanan getCurrentUser).
 * Profil terakhir disimpan di perangkat agar aplikasi (terutama pemindai) tetap
 * bisa dibuka tanpa sinyal.
 */
class SessionManager(context: Context, private val auth: AuthRepository, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<SessionState>(if (isConfigured) SessionState.Loading else SessionState.NotConfigured)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val me: Me? get() = when (val s = _state.value) {
        is SessionState.SignedIn -> s.me
        is SessionState.MustChangePassword -> s.me
        else -> null
    }

    init {
        if (isConfigured) {
            scope.launch {
                auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Initializing -> _state.value = SessionState.Loading
                        is SessionStatus.NotAuthenticated -> {
                            if (_state.value !is SessionState.SignedOut) _state.value = SessionState.SignedOut()
                        }
                        is SessionStatus.Authenticated -> if (me?.id != auth.userId) refresh()
                        is SessionStatus.RefreshFailure -> {
                            // Token tak bisa diperbarui (biasanya offline): pakai profil cache bila ada.
                            val cached = cached()
                            _state.value = if (cached != null) SessionState.SignedIn(cached, offline = true)
                            else SessionState.Failed("Tidak bisa terhubung ke server. Periksa sinyal lalu coba lagi.")
                        }
                    }
                }
            }
        }
    }

    /** Muat ulang profil & peran dari server. */
    fun refresh() {
        scope.launch {
            val uid = auth.userId
            try {
                val me = auth.me()
                if (me == null || !me.active) {
                    auth.signOut()
                    clearCache()
                    _state.value = SessionState.SignedOut("Akun Anda nonaktif. Hubungi superadmin.")
                    return@launch
                }
                prefs.edit().putString("me", AppJson.encodeToString(Me.serializer(), me)).apply()
                _state.value = if (me.mustChangePassword) SessionState.MustChangePassword(me) else SessionState.SignedIn(me)
            } catch (e: Throwable) {
                val cached = cached()?.takeIf { it.id == uid }
                _state.value = when {
                    cached != null && e.isNetwork() -> SessionState.SignedIn(cached, offline = true)
                    e.isNetwork() -> SessionState.Failed("Tidak bisa terhubung ke server. Periksa sinyal lalu coba lagi.")
                    else -> SessionState.Failed("Gagal memuat profil akun. Coba lagi.")
                }
            }
        }
    }

    fun onSignedOut(notice: String? = null) {
        clearCache()
        _state.value = SessionState.SignedOut(notice)
    }

    private fun cached(): Me? = prefs.getString("me", null)?.let { runCatching { AppJson.decodeFromString(Me.serializer(), it) }.getOrNull() }

    private fun clearCache() = prefs.edit().remove("me").apply()
}
