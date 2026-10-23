package id.generus.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.generus.app.GenerusApp
import id.generus.app.core.Qr
import id.generus.app.core.isNetwork
import id.generus.app.core.userMessage
import id.generus.app.data.local.OfflineDb
import id.generus.app.data.local.OutboxItem
import id.generus.app.data.local.RosterItem
import id.generus.app.data.local.ScanAgenda
import id.generus.app.data.model.AttendanceStatus
import io.github.jan.supabase.exceptions.RestException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Satu perubahan presensi. mode: set (checklist) | scan | search. */
data class AttendanceEntry(val agendaId: String, val generusId: String, val status: AttendanceStatus, val mode: String = "set")

data class FlushResult(val sent: Int = 0, val rejected: Int = 0, val offline: Boolean = false)

sealed interface ScanResult {
    data class Ok(val name: String, val kelompok: String, val status: AttendanceStatus) : ScanResult
    data class Duplicate(val name: String, val kelompok: String, val status: AttendanceStatus) : ScanResult
    data object Unknown : ScanResult
    data object Invalid : ScanResult
    data class Closed(val reason: String) : ScanResult
}

/**
 * Antrian presensi + roster pemindai di perangkat, dan pengirimannya ke server.
 * Padanan src/lib/offline/outbox.ts, src/lib/offline/scan.ts, dan outbox-sync.tsx.
 * Pengiriman dipicu: setelah mengetuk status, saat jaringan kembali, berkala saat
 * aplikasi terbuka, dan oleh WorkManager saat aplikasi ditutup (Android bisa
 * menyinkronkan di latar belakang, tidak seperti PWA di iOS).
 */
class PresensiStore(
    private val context: Context,
    private val db: OfflineDb,
    private val agendaRepo: AgendaRepository,
    private val scope: CoroutineScope,
) {
    private val outbox = db.outbox()
    private val roster = db.roster()
    private val mutex = Mutex()
    private var pendingJob: Job? = null

    private val _synced = MutableSharedFlow<FlushResult>(extraBufferCapacity = 4)

    /** Dipancarkan setelah ada data yang terkirim, agar layar memuat ulang data server. */
    val synced: SharedFlow<FlushResult> = _synced

    val pendingCount: Flow<Int> = outbox.observePendingCount()

    fun observeOutbox(agendaId: String): Flow<List<OutboxItem>> = outbox.observeAgenda(agendaId)
    fun observeAllOutbox(): Flow<List<OutboxItem>> = outbox.observeAll()

    suspend fun enqueue(entries: List<AttendanceEntry>) {
        val now = System.currentTimeMillis()
        outbox.put(entries.map { OutboxItem(it.agendaId, it.generusId, it.status.name, it.mode, now) })
        requestSync()
    }

    /** Minta pengiriman segera. Digabung bila beruntun. */
    fun requestSync(delayMs: Long = 800) {
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(delayMs)
            val res = runCatching { flush() }.getOrNull()
            if (res?.offline == true) scheduleBackground()
        }
    }

    /** Antrekan pengiriman lewat WorkManager: berjalan saat ada jaringan, walau aplikasi ditutup. */
    fun scheduleBackground() {
        val req = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("outbox-sync", ExistingWorkPolicy.KEEP, req)
    }

    /** Kirim semua item yang menunggu, per agenda. Hanya satu flush berjalan pada satu waktu. */
    suspend fun flush(): FlushResult = mutex.withLock {
        val pending = outbox.pending()
        if (pending.isEmpty()) return@withLock FlushResult()
        var sent = 0
        var rejected = 0
        for ((agendaId, items) in pending.groupBy { it.agendaId }) {
            for (batch in items.chunked(BATCH)) {
                val entries = buildJsonArray {
                    batch.forEach { b -> add(buildJsonObject { put("generus_id", b.generusId); put("status", b.status); put("mode", b.mode) }) }
                }
                val error: String? = try {
                    agendaRepo.saveAttendance(agendaId, entries)
                    null
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // Jaringan putus / sesi perlu diperbarui: biarkan di antrian.
                    if (e.isNetwork() || (e is RestException && e.statusCode == 401)) {
                        outbox.bumpAttempts(agendaId)
                        val result = FlushResult(sent, rejected, offline = true)
                        if (sent > 0) _synced.tryEmit(result)
                        return@withLock result
                    }
                    e.userMessage("Gagal menyimpan presensi. Sebagian peserta mungkin di luar akses Anda atau batas waktu sudah lewat.")
                }
                for (b in batch) {
                    // Ada ketukan baru setelah batch ini dibuat? Biarkan untuk putaran berikut.
                    if (error == null) outbox.deleteIfUnchanged(b.agendaId, b.generusId, b.updatedAt)
                    else outbox.rejectIfUnchanged(b.agendaId, b.generusId, b.updatedAt, error)
                }
                if (error == null) sent += batch.size else rejected += batch.size
            }
        }
        FlushResult(sent, rejected).also { if (it.sent > 0 || it.rejected > 0) _synced.tryEmit(it) }
    }

    suspend fun discardRejected(agendaId: String? = null) = outbox.discardRejected(agendaId)

    suspend fun retryRejected(agendaId: String? = null) {
        outbox.retryRejected(agendaId)
        requestSync(0)
    }

    // -----------------------------------------------------------------------
    // Pemindai (sepenuhnya di perangkat: cocokkan hash QR dengan roster unduhan)
    // -----------------------------------------------------------------------

    fun observeScanAgenda(agendaId: String): Flow<ScanAgenda?> = roster.observeAgenda(agendaId)
    fun observeRoster(agendaId: String): Flow<List<RosterItem>> = roster.observeRoster(agendaId)

    /** Unduh daftar peserta agar pemindai bisa dipakai offline. Melempar galat bila gagal. */
    suspend fun downloadRoster(agendaId: String): ScanAgenda {
        val r = agendaRepo.roster(agendaId)
        val a = r.agenda
        val agenda = ScanAgenda(
            id = a.id,
            title = a.title,
            unitName = a.unitName,
            level = a.level.name,
            startsAt = a.startsAt.toEpochMilli(),
            endsAt = a.endsAt.toEpochMilli(),
            lateAfter = a.lateAfter.toEpochMilli(),
            lockedAt = a.lockedAt?.toEpochMilli(),
            cancelledAt = a.cancelledAt?.toEpochMilli(),
            downloadedAt = System.currentTimeMillis(),
        )
        roster.replace(agenda, r.rows.map { RosterItem(agendaId, it.generusId, it.name, it.gender.name, it.kelompok, it.desa, it.tokenHash, it.status?.name) })
        return agenda
    }

    /** Status terbaru seorang peserta: antrian lokal menang atas data unduhan. */
    suspend fun currentStatus(item: RosterItem): AttendanceStatus? =
        (outbox.get(item.agendaId, item.generusId)?.status ?: item.status)?.let { AttendanceStatus.valueOf(it) }

    private suspend fun agendaOpen(agendaId: String): Result<Instant> {
        val a = roster.agenda(agendaId) ?: return Result.failure(IllegalStateException("Daftar peserta belum diunduh. Buka pemindai saat online sekali."))
        if (a.cancelledAt != null) return Result.failure(IllegalStateException("Agenda dibatalkan."))
        if (a.lockedAt != null) return Result.failure(IllegalStateException("Presensi agenda sudah dikunci."))
        return Result.success(Instant.ofEpochMilli(a.lateAfter))
    }

    private suspend fun record(item: RosterItem, mode: String, now: Instant, lateAfter: Instant): ScanResult {
        currentStatus(item)?.let { return ScanResult.Duplicate(item.name, item.kelompok, it) }
        val status = if (now.isAfter(lateAfter)) AttendanceStatus.terlambat else AttendanceStatus.hadir
        enqueue(listOf(AttendanceEntry(item.agendaId, item.generusId, status, mode)))
        // Catat juga di roster lokal: setelah antrian terkirim & dihapus, scan ulang tetap terdeteksi duplikat.
        roster.setStatus(item.agendaId, item.generusId, status.name)
        return ScanResult.Ok(item.name, item.kelompok, status)
    }

    /** Proses satu hasil scan kamera / ketikan kode. */
    suspend fun processScan(agendaId: String, raw: String, now: Instant = Instant.now()): ScanResult {
        val lateAfter = agendaOpen(agendaId).getOrElse { return ScanResult.Closed(it.message ?: "Pemindai tidak tersedia.") }
        val payload = Qr.normalize(raw) ?: return ScanResult.Invalid
        val item = roster.byHash(agendaId, Qr.sha256Hex(payload)) ?: return ScanResult.Unknown
        return record(item, "scan", now, lateAfter)
    }

    /** Cadangan tanpa kartu: catat lewat pencarian nama. */
    suspend fun recordBySearch(agendaId: String, generusId: String, now: Instant = Instant.now()): ScanResult {
        val lateAfter = agendaOpen(agendaId).getOrElse { return ScanResult.Closed(it.message ?: "Pemindai tidak tersedia.") }
        val item = roster.item(agendaId, generusId) ?: return ScanResult.Unknown
        return record(item, "search", now, lateAfter)
    }

    /** Hapus semua data presensi di perangkat (saat keluar akun). */
    suspend fun clearAll() {
        outbox.clear()
        roster.clearRoster()
        roster.clearAgendas()
    }

    private companion object {
        const val BATCH = 500
    }
}

/** Pengiriman antrian presensi di latar belakang. */
class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = (applicationContext as GenerusApp).container.presensi
        val res = runCatching { store.flush() }.getOrElse { return Result.retry() }
        return if (res.offline) Result.retry() else Result.success()
    }
}
