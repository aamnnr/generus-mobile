package id.generus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import id.generus.app.AppContainer
import id.generus.app.GenerusApp
import id.generus.app.core.isNetwork
import id.generus.app.core.userMessage
import id.generus.app.ui.components.Loadable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as GenerusApp).container

/** ViewModel dengan akses ke AppContainer. Pakai `vm { MyVm(it, arg) }` di composable. */
@Composable
inline fun <reified V : ViewModel> vm(key: String? = null, crossinline create: (AppContainer) -> V): V {
    val c = appContainer()
    return viewModel(key = key) { create(c) }
}

/**
 * Jalankan `action` saat layar berikutnya mengirim hasil lewat
 * `nav.previousBackStackEntry?.savedStateHandle?.set(key, true)` sebelum kembali
 * (mis. form ubah → detail dimuat ulang).
 */
@Composable
fun OnResult(nav: androidx.navigation.NavHostController, key: String = "saved", action: () -> Unit) {
    // Entri milik layar ini sendiri (bukan entri teratas yang bisa berubah saat transisi).
    val entry = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current as? androidx.navigation.NavBackStackEntry
        ?: nav.currentBackStackEntry ?: return
    androidx.compose.runtime.LaunchedEffect(entry) {
        entry.savedStateHandle.getStateFlow(key, false).collect { done ->
            if (done) {
                entry.savedStateHandle[key] = false
                action()
            }
        }
    }
}

/** Kirim hasil ke layar sebelumnya lalu kembali (pasangan [OnResult]). */
fun androidx.navigation.NavHostController.popWithResult(key: String = "saved") {
    previousBackStackEntry?.savedStateHandle?.set(key, true)
    popBackStack()
}

/** Data layar disalin ke perangkat agar tetap bisa dibuka tanpa sinyal. */
class CacheSpec<T>(val key: String, val serializer: KSerializer<T>)

/**
 * Pola dasar ViewModel layar: satu `state` data yang bisa dimuat ulang, plus
 * `busy`/`run` untuk aksi simpan dengan pesan error ramah. Dengan `cache`, data
 * terakhir ditampilkan dari perangkat bila server tak terjangkau (`stale` = true).
 */
abstract class LoadVm<T>(protected val c: AppContainer, private val cache: CacheSpec<T>? = null) : ViewModel() {
    private val _state = MutableStateFlow(Loadable<T>())
    val state: StateFlow<Loadable<T>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** True bila data yang tampil berasal dari salinan di perangkat (offline). */
    private val _stale = MutableStateFlow(false)
    val stale: StateFlow<Boolean> = _stale.asStateFlow()

    private var loadJob: Job? = null

    protected abstract suspend fun fetch(): T

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val data = fetch()
                cache?.let { c.cache.put(it.key, it.serializer, data) }
                _stale.value = false
                _state.value = Loadable(data, loading = false)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val cached = if (e.isNetwork() && _state.value.data == null) cache?.let { c.cache.get(it.key, it.serializer) } else null
                if (cached != null) {
                    _stale.value = true
                    _state.value = Loadable(cached, loading = false)
                } else {
                    _state.update { it.copy(loading = false, error = e.userMessage("Gagal memuat data. Tarik ke bawah untuk mencoba lagi.")) }
                }
            }
        }
    }

    protected fun setData(transform: (T) -> T) = _state.update { s -> s.data?.let { s.copy(data = transform(it)) } ?: s }

    /**
     * Jalankan aksi (simpan/hapus). `onError` menerima pesan ramah; `onOk` dipanggil
     * setelah berhasil. Aksi kedua diabaikan selama yang pertama berjalan.
     */
    fun run(onError: (String) -> Unit, onOk: () -> Unit = {}, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
                onOk()
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                onError(e.userMessage())
            } finally {
                _busy.value = false
            }
        }
    }
}
