package id.generus.app.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import id.generus.app.data.AppJson
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer

/** Status koneksi internet perangkat. */
class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(check())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _online.value = true }
            override fun onLost(network: Network) { _online.value = check() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        })
    }

    private fun check(): Boolean =
        cm?.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

/**
 * Salinan JSON data layar terakhir (beranda, daftar & detail agenda) agar bisa
 * dibuka tanpa sinyal. Disimpan di cache aplikasi, dihapus saat keluar akun.
 */
class JsonCache(context: Context) {
    private val dir = File(context.cacheDir, "screens").apply { mkdirs() }

    private fun file(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")

    fun <T> put(key: String, serializer: KSerializer<T>, value: T) {
        runCatching { file(key).writeText(AppJson.encodeToString(serializer, value)) }
    }

    fun <T> get(key: String, serializer: KSerializer<T>): T? =
        runCatching { file(key).takeIf { it.exists() }?.readText()?.let { AppJson.decodeFromString(serializer, it) } }.getOrNull()

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
