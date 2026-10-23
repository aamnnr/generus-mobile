package id.generus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import id.generus.app.data.local.ThemeMode
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.generus.app.data.SessionState
import id.generus.app.ui.appContainer
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.GhostButton
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.ProvideToaster
import id.generus.app.ui.nav.MainNav
import id.generus.app.ui.screens.akun.GantiPasswordScreen
import id.generus.app.ui.screens.akun.LoginScreen
import id.generus.app.ui.theme.GenerusTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by appContainer().theme.mode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.Terang -> false
                ThemeMode.Gelap -> true
                ThemeMode.Sistem -> isSystemInDarkTheme()
            }
            // Ikon status bar & navigasi mengikuti tema aplikasi, bukan tema sistem.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            GenerusTheme(dark = dark) {
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                ProvideToaster(snackbar, scope) {
                    // Surface menyediakan warna teks bawaan (onBackground) untuk seluruh aplikasi.
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                        Box(Modifier.fillMaxSize()) {
                            Root()
                            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Akar aplikasi: pilih layar dari status sesi (padanan proxy.ts + getCurrentUser di PWA). */
@Composable
private fun Root() {
    val c = appContainer()
    val state by c.session.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val signOut: () -> Unit = {
        scope.launch {
            c.auth.signOut()
            c.presensi.clearAll()
            c.cache.clear()
            c.session.onSignedOut()
        }
    }
    when (val s = state) {
        SessionState.Loading -> Splash()
        SessionState.NotConfigured -> Centered {
            EmptyState(
                "Server belum diatur",
                body = "Aplikasi ini dibangun tanpa alamat server Supabase. Isi secrets.properties (lihat secrets.properties.example) lalu build ulang.",
            )
        }
        is SessionState.SignedOut -> LoginScreen(s.notice)
        is SessionState.MustChangePassword -> GantiPasswordScreen(onDone = {}, forced = true, onSignOut = signOut)
        is SessionState.SignedIn -> MainNav(s.me)
        is SessionState.Failed -> Centered {
            EmptyState("Belum bisa masuk", body = s.message, action = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PrimaryButton("Coba lagi", onClick = { c.session.refresh() })
                    GhostButton("Keluar", onClick = signOut)
                }
            })
        }
    }
}

@Composable
private fun Splash() = Centered {
    Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(96.dp))
    Text("Generus", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
