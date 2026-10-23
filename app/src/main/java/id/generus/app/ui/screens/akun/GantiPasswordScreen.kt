package id.generus.app.ui.screens.akun

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.generus.app.core.userMessage
import id.generus.app.ui.appContainer
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.BannerTone
import id.generus.app.ui.components.FormColumn
import id.generus.app.ui.components.GhostButton
import id.generus.app.ui.components.InfoBanner
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.TextInput
import kotlinx.coroutines.launch

const val MIN_PASSWORD_LENGTH = 8

/**
 * Ganti password (padanan /akun/password). `forced` = akun dengan password
 * sementara yang wajib diganti sebelum memakai aplikasi.
 */
@Composable
fun GantiPasswordScreen(onDone: () -> Unit, onBack: (() -> Unit)? = null, forced: Boolean = false, onSignOut: (() -> Unit)? = null) {
    val c = appContainer()
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        error = when {
            password.length < MIN_PASSWORD_LENGTH -> "Password minimal $MIN_PASSWORD_LENGTH karakter"
            password != confirm -> "Konfirmasi password tidak sama"
            else -> null
        }
        if (error != null || busy) return
        busy = true
        scope.launch {
            try {
                c.auth.changePassword(password)
                c.session.refresh()
                toast("Password berhasil diganti.")
                onDone()
            } catch (t: Throwable) {
                error = t.userMessage("Gagal mengganti password. Coba lagi.")
            } finally {
                busy = false
            }
        }
    }

    AppScreen("Ganti password", onBack = onBack) { p ->
        FormColumn(Modifier.padding(p).imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (forced) {
                InfoBanner("Akun Anda memakai password sementara. Buat password baru sebelum melanjutkan.", tone = BannerTone.Warning)
            }
            if (error != null) InfoBanner(error!!, tone = BannerTone.Error)
            TextInput("Password baru", password, { password = it }, password = true, supporting = "Minimal $MIN_PASSWORD_LENGTH karakter, kombinasi huruf dan angka.")
            TextInput("Ulangi password baru", confirm, { confirm = it }, password = true)
            PrimaryButton("Simpan password", onClick = ::submit, loading = busy, modifier = Modifier.fillMaxWidth())
            if (onSignOut != null) GhostButton("Keluar", onClick = onSignOut, modifier = Modifier.fillMaxWidth())
            Text(
                "Setelah diganti, gunakan password baru untuk masuk berikutnya.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
