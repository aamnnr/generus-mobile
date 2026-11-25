package id.generus.app.data

// Penahan scan berulang & penggabung riwayat untuk pemindai kamera (padanan
// src/lib/scan-gate.ts di PWA).
//
// Kamera membaca QR yang sama beberapa kali per detik selama kartu masih di depan
// lensa. Satu kode hanya diproses sekali selama kode itu TERUS terlihat; kode yang
// sama baru diterima lagi setelah kartu disingkirkan beberapa saat. Jedanya dihitung
// dari terakhir kali kode itu TERLIHAT (bergeser), bukan dari scan pertama — kartu
// yang ditahan 10 detik tetap dihitung sekali.

const val SAME_CODE_RELEASE_MS = 1500L

class ScanGate(private val releaseMs: Long = SAME_CODE_RELEASE_MS) {
    private val lastSeen = HashMap<String, Long>()

    /** True bila kode ini perlu diproses; false bila masih pengulangan kartu yang sama. */
    @Synchronized
    fun accept(code: String, now: Long = System.currentTimeMillis()): Boolean {
        val prev = lastSeen.put(code, now)
        // Bersihkan kode lama agar tidak tumbuh selama acara panjang.
        if (lastSeen.size > 200) lastSeen.entries.removeAll { now - it.value > releaseMs }
        return prev == null || now - prev >= releaseMs
    }
}

/**
 * Satu baris riwayat. `value` = yang ditampilkan di riwayat (bisa hasil pertama, lihat
 * `keep` di [pushLog]); `last` = hasil paling baru (panel umpan balik); `firstAt`/`at` =
 * waktu pertama & terakhir muncul (ms); `count` = berapa kali kunci ini muncul.
 */
data class LogEntry<T>(val key: String, val value: T, val last: T, val firstAt: Long, val at: Long, val count: Int)

/**
 * Tambahkan hasil ke riwayat. Hasil dengan kunci yang sama (orang yang sama, atau
 * pesan galat yang sama) tidak menambah baris: baris lamanya naik ke atas dengan
 * hitungan bertambah. `keep(prev, next)` = true mempertahankan nilai tampilan lama
 * (mis. "Hadir" tetap tampil walau scan berikutnya "sudah tercatat").
 */
fun <T> pushLog(
    log: List<LogEntry<T>>,
    key: String,
    value: T,
    at: Long,
    limit: Int = 50,
    keep: ((prev: T, next: T) -> Boolean)? = null,
): List<LogEntry<T>> {
    val old = log.firstOrNull { it.key == key }
    val entry = LogEntry(
        key = key,
        value = if (old != null && keep?.invoke(old.value, value) == true) old.value else value,
        last = value,
        firstAt = old?.firstAt ?: at,
        at = at,
        count = (old?.count ?: 0) + 1,
    )
    return (listOf(entry) + log.filter { it !== old }).take(limit)
}
