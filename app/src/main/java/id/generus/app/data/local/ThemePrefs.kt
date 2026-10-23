package id.generus.app.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { Sistem("Ikuti sistem"), Terang("Terang"), Gelap("Gelap") }

/** Pilihan tampilan terang/gelap, disimpan di perangkat. */
class ThemePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("tampilan", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(
        prefs.getString("tema", null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.Sistem,
    )
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun set(mode: ThemeMode) {
        prefs.edit().putString("tema", mode.name).apply()
        _mode.value = mode
    }
}
