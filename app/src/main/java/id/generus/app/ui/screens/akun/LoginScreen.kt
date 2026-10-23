package id.generus.app.ui.screens.akun

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.generus.app.R
import id.generus.app.core.userMessage
import id.generus.app.ui.appContainer
import id.generus.app.ui.components.AppCard
import id.generus.app.ui.components.BannerTone
import id.generus.app.ui.components.InfoBanner
import id.generus.app.ui.components.MosqueSilhouette
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.TextInput
import id.generus.app.ui.theme.GenerusTheme
import kotlinx.coroutines.launch

/** Login (DESIGN.md §8.5): latar krem, siluet masjid, logo + nama, form di kartu. */
@Composable
fun LoginScreen(notice: String?) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        val e = email.trim()
        error = when {
            !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(e) -> "Email tidak valid"
            password.isEmpty() -> "Password wajib diisi"
            else -> null
        }
        if (error != null || busy) return
        busy = true
        scope.launch {
            try {
                c.auth.signIn(e, password)
                // Status sesi berubah → SessionManager memuat profil & berpindah layar.
            } catch (t: Throwable) {
                error = t.userMessage("Email atau password salah.")
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MosqueSilhouette(
            GenerusTheme.extra.brass.copy(alpha = 0.18f),
            Modifier.align(Alignment.TopCenter).padding(top = 48.dp).width(320.dp).height(192.dp),
        )
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(12.dp))
            Text("Generus", style = MaterialTheme.typography.headlineLarge)
            Text("Presensi · Materi · Kitab", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                Text("Masuk", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Gunakan akun yang dibuat superadmin. Tidak ada pendaftaran mandiri.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (notice != null) InfoBanner(notice, tone = BannerTone.Warning)
                if (error != null) InfoBanner(error!!, tone = BannerTone.Error, icon = Icons.Rounded.ErrorOutline)
                TextInput("Email", email, { email = it }, keyboardType = KeyboardType.Email)
                TextInput("Password", password, { password = it }, password = true)
                PrimaryButton("Masuk", onClick = ::submit, loading = busy, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Lupa password? Hubungi superadmin untuk direset.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
