package id.generus.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Penyimpanan di perangkat untuk presensi offline (padanan src/lib/offline/outbox.ts).
// Setiap ketukan status disimpan di antrian dulu lalu dikirim ke server; aman saat
// sinyal putus atau aplikasi ditutup. Kunci = agenda + generus, jadi ketukan terakhir
// menimpa yang lama dan pengiriman ulang idempoten (server melakukan upsert).

/** mode: set = checklist (menimpa di server), scan/search = pemindai (catatan pertama menang). */
@Entity(tableName = "outbox", primaryKeys = ["agendaId", "generusId"], indices = [Index("agendaId")])
data class OutboxItem(
    val agendaId: String,
    val generusId: String,
    val status: String,
    val mode: String,
    /** ms epoch; ketukan yang lebih baru dari yang terkirim tidak ikut terhapus. */
    val updatedAt: Long,
    val attempts: Int = 0,
    /** Ditolak server (mis. di luar akses / batas waktu lewat); tidak dicoba ulang otomatis. */
    val error: String? = null,
)

/** Peserta agenda yang diunduh pemindai. tokenHash = SHA-256 isi QR (bukan token). */
@Entity(tableName = "roster", primaryKeys = ["agendaId", "generusId"], indices = [Index("agendaId", "tokenHash")])
data class RosterItem(
    val agendaId: String,
    val generusId: String,
    val name: String,
    val gender: String,
    val kelompok: String,
    val desa: String,
    val tokenHash: String?,
    /** Status dari server saat diunduh (atau yang dicatat di perangkat ini). */
    val status: String?,
)

@Entity(tableName = "scan_agendas", primaryKeys = ["id"])
data class ScanAgenda(
    val id: String,
    val title: String,
    val unitName: String,
    val level: String,
    val startsAt: Long,
    val endsAt: Long,
    val lateAfter: Long,
    val lockedAt: Long?,
    val cancelledAt: Long?,
    val downloadedAt: Long,
)

@Dao
interface OutboxDao {
    @Upsert
    suspend fun put(items: List<OutboxItem>)

    @Query("SELECT * FROM outbox WHERE error IS NULL")
    suspend fun pending(): List<OutboxItem>

    @Query("SELECT * FROM outbox WHERE agendaId = :agendaId AND generusId = :generusId")
    suspend fun get(agendaId: String, generusId: String): OutboxItem?

    @Query("SELECT * FROM outbox")
    fun observeAll(): Flow<List<OutboxItem>>

    @Query("SELECT * FROM outbox WHERE agendaId = :agendaId")
    fun observeAgenda(agendaId: String): Flow<List<OutboxItem>>

    @Query("SELECT count(*) FROM outbox WHERE error IS NULL")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM outbox WHERE agendaId = :agendaId AND generusId = :generusId AND updatedAt = :updatedAt")
    suspend fun deleteIfUnchanged(agendaId: String, generusId: String, updatedAt: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1, error = :error WHERE agendaId = :agendaId AND generusId = :generusId AND updatedAt = :updatedAt")
    suspend fun rejectIfUnchanged(agendaId: String, generusId: String, updatedAt: Long, error: String)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE agendaId = :agendaId")
    suspend fun bumpAttempts(agendaId: String)

    /** Hapus item yang ditolak server (pengguna memilih membuangnya). */
    @Query("DELETE FROM outbox WHERE error IS NOT NULL AND (:agendaId IS NULL OR agendaId = :agendaId)")
    suspend fun discardRejected(agendaId: String?)

    /** Coba lagi item yang ditolak (mis. setelah admin memperpanjang batas waktu). */
    @Query("UPDATE outbox SET error = NULL, attempts = 0 WHERE error IS NOT NULL AND (:agendaId IS NULL OR agendaId = :agendaId)")
    suspend fun retryRejected(agendaId: String?)

    @Query("DELETE FROM outbox")
    suspend fun clear()
}

@Dao
interface RosterDao {
    @Query("SELECT * FROM scan_agendas WHERE id = :id")
    suspend fun agenda(id: String): ScanAgenda?

    @Query("SELECT * FROM scan_agendas WHERE id = :id")
    fun observeAgenda(id: String): Flow<ScanAgenda?>

    @Query("SELECT * FROM roster WHERE agendaId = :agendaId AND tokenHash = :hash LIMIT 1")
    suspend fun byHash(agendaId: String, hash: String): RosterItem?

    @Query("SELECT * FROM roster WHERE agendaId = :agendaId AND generusId = :generusId")
    suspend fun item(agendaId: String, generusId: String): RosterItem?

    @Query("SELECT * FROM roster WHERE agendaId = :agendaId ORDER BY name")
    fun observeRoster(agendaId: String): Flow<List<RosterItem>>

    @Query("UPDATE roster SET status = :status WHERE agendaId = :agendaId AND generusId = :generusId")
    suspend fun setStatus(agendaId: String, generusId: String, status: String)

    @Query("DELETE FROM roster WHERE agendaId = :agendaId")
    suspend fun deleteRoster(agendaId: String)

    @Upsert
    suspend fun putRoster(items: List<RosterItem>)

    @Upsert
    suspend fun putAgenda(agenda: ScanAgenda)

    /** Simpan daftar peserta hasil unduhan, menggantikan unduhan sebelumnya untuk agenda itu. */
    @Transaction
    suspend fun replace(agenda: ScanAgenda, items: List<RosterItem>) {
        deleteRoster(agenda.id)
        putRoster(items)
        putAgenda(agenda)
    }

    @Query("DELETE FROM roster")
    suspend fun clearRoster()

    @Query("DELETE FROM scan_agendas")
    suspend fun clearAgendas()
}

@Database(entities = [OutboxItem::class, RosterItem::class, ScanAgenda::class], version = 1)
abstract class OfflineDb : RoomDatabase() {
    abstract fun outbox(): OutboxDao
    abstract fun roster(): RosterDao

    companion object {
        fun create(context: Context): OfflineDb = Room.databaseBuilder(context, OfflineDb::class.java, "generus-offline.db").build()
    }
}
