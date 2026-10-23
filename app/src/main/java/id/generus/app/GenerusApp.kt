package id.generus.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import id.generus.app.data.AdminRepository
import id.generus.app.data.AgendaRepository
import id.generus.app.data.AuthRepository
import id.generus.app.data.CapaianRepository
import id.generus.app.data.GenerusRepository
import id.generus.app.data.KurikulumRepository
import id.generus.app.data.LaporanRepository
import id.generus.app.data.PresensiStore
import id.generus.app.data.SessionManager
import id.generus.app.data.createSupabase
import id.generus.app.data.local.JsonCache
import id.generus.app.data.local.NetworkMonitor
import id.generus.app.data.local.OfflineDb
import id.generus.app.data.local.ThemePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Satu instans untuk seluruh aplikasi (tanpa framework DI). */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val supabase = createSupabase()
    val auth = AuthRepository(supabase)
    val agenda = AgendaRepository(supabase)
    val generus = GenerusRepository(supabase)
    val kurikulum = KurikulumRepository(supabase)
    val capaian = CapaianRepository(supabase)
    val laporan = LaporanRepository(supabase)
    val admin = AdminRepository(supabase)
    val db = OfflineDb.create(context)
    val network = NetworkMonitor(context)
    val cache = JsonCache(context)
    val theme = ThemePrefs(context)
    val presensi = PresensiStore(context, db, agenda, scope)
    val session = SessionManager(context, auth, scope)
}

class GenerusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        watchForegroundSync()
    }

    /**
     * Selama aplikasi terbuka: kirim antrian presensi saat jaringan kembali dan
     * tiap 20 detik bila masih ada antrian (padanan OutboxSync di PWA).
     */
    private fun watchForegroundSync() {
        val presensi = container.presensi
        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = presensi.requestSync()
        })
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            private var loop: Job? = null
            override fun onStart(owner: LifecycleOwner) {
                loop = container.scope.launch {
                    presensi.requestSync()
                    while (isActive) {
                        delay(20_000)
                        if (presensi.pendingCount.first() > 0) runCatching { presensi.flush() }
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                loop?.cancel()
                // Sisa antrian dikirim WorkManager walau aplikasi ditutup.
                container.scope.launch { if (presensi.pendingCount.first() > 0) presensi.scheduleBackground() }
            }
        })
    }
}
