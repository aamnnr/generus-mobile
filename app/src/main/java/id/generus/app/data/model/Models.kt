@file:Suppress("EnumEntryName")

package id.generus.app.data.model

import kotlinx.serialization.Serializable

// Model data dari fungsi RPC public.mobile_* (drizzle/0014_mobile_api.sql di proyek
// PWA) dan tabel PostgREST. Nama properti camelCase dipetakan ke snake_case oleh
// JsonNamingStrategy.SnakeCase (lihat data/Supabase.kt). Nilai enum = nilai di database.

// ---------------------------------------------------------------------------
// Enum
// ---------------------------------------------------------------------------

@Serializable enum class AppRole { superadmin, kurikulum, admin, kyai }
@Serializable enum class UnitLevel { daerah, desa, kelompok }
@Serializable enum class Gender { L, P }
@Serializable enum class GenerusStatus { aktif, pindah_sambung, menikah }
@Serializable enum class AttendanceStatus { hadir, terlambat, izin, sakit, alpa }
@Serializable enum class Aspek { alim, faqih, akhlaq, mandiri }
@Serializable enum class KitabUnit { ayat, halaman, bab }
@Serializable enum class AssessmentMode { generus, kelompok }
@Serializable enum class CapaianStatus { belum, proses, tuntas }

// ---------------------------------------------------------------------------
// Akun
// ---------------------------------------------------------------------------

@Serializable
data class UserRole(
    val id: String = "",
    val role: AppRole,
    val unitId: String,
    val unitName: String,
    val level: UnitLevel,
)

@Serializable
data class Me(
    val id: String,
    val name: String,
    val email: String? = null,
    val active: Boolean = true,
    val mustChangePassword: Boolean = false,
    val roles: List<UserRole> = emptyList(),
)

@Serializable
data class Account(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val active: Boolean = true,
    val mustChangePassword: Boolean = false,
    val roles: List<UserRole> = emptyList(),
)

@Serializable
data class ProfileOption(val id: String, val name: String, val email: String? = null)

// ---------------------------------------------------------------------------
// Wilayah & jenjang
// ---------------------------------------------------------------------------

@Serializable
data class Kategori(val id: String, val name: String, val sortOrder: Int = 0)

@Serializable
data class Jenjang(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val jenjangKategori: List<Kategori> = emptyList(),
) {
    val kategori: List<Kategori> get() = jenjangKategori.sortedBy { it.sortOrder }
}

/** Kelompok/desa dalam lingkup user (mobile_scope_units). */
@Serializable
data class ScopeUnit(val id: String, val name: String, val level: UnitLevel, val desaName: String)

@Serializable
data class AgendaUnit(val id: String, val name: String, val level: UnitLevel, val parentName: String? = null)

@Serializable
data class WilayahUnit(val id: String, val name: String, val level: UnitLevel, val parentId: String? = null, val generusAktif: Int = 0)

@Serializable
data class UnitNode(val id: String, val name: String, val level: UnitLevel, val parentId: String? = null, val rel: String)

@Serializable
data class KelompokRef(val id: String, val name: String)

// ---------------------------------------------------------------------------
// Beranda
// ---------------------------------------------------------------------------

@Serializable
data class HeroAgenda(
    val id: String,
    val title: String,
    val location: String? = null,
    val startsAt: IsoInstant,
    val endsAt: IsoInstant,
    val unitName: String,
    val level: UnitLevel,
    val targets: Int,
    val present: Int,
    val canManage: Boolean,
    val isScanner: Boolean,
)

@Serializable
data class MonthSummary(val agendas: Int = 0, val locked: Int = 0, val present: Int = 0, val total: Int = 0) {
    /** (hadir + terlambat) / total presensi agenda terkunci, 0–1; null bila belum ada. */
    val rate: Double? get() = if (total > 0) present.toDouble() / total else null
}

@Serializable
data class GenerusCounts(val total: Int = 0, val praRemaja: Int = 0, val remaja: Int = 0)

@Serializable
data class ScanTask(val id: String, val title: String, val startsAt: IsoInstant, val unitName: String)

@Serializable
data class Beranda(
    val hero: HeroAgenda? = null,
    val month: MonthSummary = MonthSummary(),
    val alpaStreak: Int = 0,
    val counts: GenerusCounts = GenerusCounts(),
    val scanTasks: List<ScanTask> = emptyList(),
)

// ---------------------------------------------------------------------------
// Agenda & presensi
// ---------------------------------------------------------------------------

@Serializable
data class AgendaListItem(
    val id: String,
    val title: String,
    val location: String? = null,
    val startsAt: IsoInstant,
    val endsAt: IsoInstant,
    val unitName: String,
    val level: UnitLevel,
    val lockedAt: IsoInstant? = null,
    val cancelledAt: IsoInstant? = null,
)

@Serializable
data class AgendaPage(val rows: List<AgendaListItem> = emptyList(), val hasMore: Boolean = false)

@Serializable
data class RosterRow(
    val generusId: String,
    val name: String,
    val gender: Gender,
    val kelompok: String,
    val desa: String,
    val status: AttendanceStatus? = null,
    val method: String? = null,
    val recordedAt: IsoInstant? = null,
    val canWrite: Boolean = false,
)

@Serializable
data class ScannerRow(val userId: String, val name: String, val email: String? = null, val expiresAt: IsoInstant)

@Serializable
data class AgendaDetail(
    val id: String,
    val title: String,
    val location: String? = null,
    val startsAt: IsoInstant,
    val endsAt: IsoInstant,
    val lateAfter: IsoInstant,
    val delegateUntil: IsoInstant,
    val lockedAt: IsoInstant? = null,
    val cancelledAt: IsoInstant? = null,
    val unitId: String,
    val unitName: String,
    val level: UnitLevel,
    val targetJenjangIds: List<String> = emptyList(),
    val targetKategoriIds: List<String> = emptyList(),
    val targetGender: Gender? = null,
    val recurrenceId: String? = null,
    val canManage: Boolean = false,
    val isScanner: Boolean = false,
    val roster: List<RosterRow> = emptyList(),
    val scanners: List<ScannerRow> = emptyList(),
)

/** Baris untuk insert/update tabel agendas lewat PostgREST. */
@Serializable
data class AgendaWrite(
    val unitId: String,
    val title: String,
    val location: String?,
    val startsAt: IsoInstant,
    val endsAt: IsoInstant,
    val lateAfter: IsoInstant,
    val delegateUntil: IsoInstant,
    val targetJenjangIds: List<String>,
    val targetKategoriIds: List<String>,
    val targetGender: Gender?,
    val recurrenceId: String? = null,
)

@Serializable
data class ScanAgendaDto(
    val id: String,
    val title: String,
    val unitName: String,
    val level: UnitLevel,
    val startsAt: IsoInstant,
    val endsAt: IsoInstant,
    val lateAfter: IsoInstant,
    val lockedAt: IsoInstant? = null,
    val cancelledAt: IsoInstant? = null,
)

@Serializable
data class ScanRosterDto(
    val generusId: String,
    val name: String,
    val gender: Gender,
    val kelompok: String,
    val desa: String,
    val tokenHash: String? = null,
    val status: AttendanceStatus? = null,
)

@Serializable
data class RosterDownload(val agenda: ScanAgendaDto, val rows: List<ScanRosterDto> = emptyList())

@Serializable
data class AttendanceHistoryItem(
    val agendaId: String,
    val title: String,
    val startsAt: IsoInstant,
    val unitName: String,
    val status: AttendanceStatus,
)

@Serializable
data class AttendanceStats(val total: Int = 0, val hadir: Int = 0, val alpa: Int = 0)

@Serializable
data class GenerusAttendance(val rows: List<AttendanceHistoryItem> = emptyList(), val stats: AttendanceStats = AttendanceStats())

// ---------------------------------------------------------------------------
// Generus
// ---------------------------------------------------------------------------

@Serializable
data class GenerusListItem(
    val id: String,
    val name: String,
    val gender: Gender,
    val birthDate: IsoDate,
    val jenjang: String,
    val kategori: String? = null,
    val kelompok: String,
    val desa: String,
    val status: GenerusStatus,
)

@Serializable
data class GenerusPage(val rows: List<GenerusListItem> = emptyList(), val total: Int = 0)

@Serializable
data class MutationItem(val at: IsoInstant, val from: String, val to: String)

@Serializable
data class StatusItem(val at: IsoInstant, val to: GenerusStatus, val date: IsoDate, val note: String? = null)

@Serializable
data class GenerusDetail(
    val id: String,
    val name: String,
    val gender: Gender,
    val birthDate: IsoDate,
    val jenjangId: String,
    val jenjang: String,
    val kategoriId: String? = null,
    val kategori: String? = null,
    val kelompokId: String,
    val kelompok: String,
    val desa: String,
    val daerah: String? = null,
    val school: String? = null,
    val guardianName: String? = null,
    val guardianPhone: String? = null,
    val notes: String? = null,
    val status: GenerusStatus,
    val statusDate: IsoDate? = null,
    val statusNote: String? = null,
    val qrActive: Boolean = true,
    val qrToken: String? = null,
    val canWrite: Boolean = false,
    val mutations: List<MutationItem> = emptyList(),
    val statuses: List<StatusItem> = emptyList(),
)

/** Kolom generus yang diisi dari form tambah/ubah. */
@Serializable
data class GenerusWrite(
    val name: String,
    val gender: Gender,
    val birthDate: IsoDate,
    val jenjangId: String,
    val kategoriId: String?,
    val school: String?,
    val guardianName: String?,
    val guardianPhone: String?,
    val notes: String?,
)

@Serializable
data class KartuRow(
    val id: String,
    val name: String,
    val token: String,
    val gender: Gender,
    val kelompok: String,
    val desa: String,
    val daerah: String? = null,
    val jenjang: String,
    val kategori: String? = null,
)

@Serializable
data class PromotionCandidate(
    val id: String,
    val name: String,
    val gender: Gender,
    val birthDate: IsoDate,
    val school: String? = null,
    val kelompok: String,
)

@Serializable
data class PromotionPage(val rows: List<PromotionCandidate> = emptyList(), val truncated: Boolean = false)

// ---------------------------------------------------------------------------
// Kurikulum
// ---------------------------------------------------------------------------

@Serializable
data class Period(
    val id: String,
    val name: String,
    val semester: String,
    val startsOn: IsoDate,
    val endsOn: IsoDate,
    val publishedAt: IsoInstant? = null,
    val targetCount: Int = 0,
)

@Serializable
data class TargetMateri(
    val id: String,
    val title: String,
    val body: String? = null,
    val arabic: String? = null,
    val translation: String? = null,
    val source: String? = null,
    val assessment: AssessmentMode = AssessmentMode.generus,
)

@Serializable
data class TargetKitab(val id: String, val title: String, val unit: KitabUnit, val total: Int, val from: Int, val to: Int)

@Serializable
data class Target(
    val id: String,
    val periodId: String,
    val jenjangId: String,
    val kategoriId: String? = null,
    val kategoriName: String? = null,
    val gender: Gender? = null,
    val sortOrder: Int = 0,
    val aspek: Aspek,
    val mapel: String,
    val note: String? = null,
    val strategy: String? = null,
    val optional: Boolean = false,
    val materi: TargetMateri? = null,
    val kitab: TargetKitab? = null,
)

/** Kolom period_targets untuk insert/update lewat PostgREST. */
@Serializable
data class TargetWrite(
    val periodId: String,
    val jenjangId: String,
    val kategoriId: String?,
    val gender: Gender?,
    val aspek: Aspek,
    val mapel: String,
    val materiId: String?,
    val kitabId: String?,
    val rangeFrom: Int?,
    val rangeTo: Int?,
    val note: String?,
    val strategy: String?,
    val optional: Boolean,
    val sortOrder: Int? = null,
)

@Serializable
data class MateriItem(
    val id: String,
    val aspek: Aspek,
    val mapel: String,
    val title: String,
    val body: String? = null,
    val arabic: String? = null,
    val translation: String? = null,
    val source: String? = null,
    val assessment: AssessmentMode = AssessmentMode.generus,
    val archived: Boolean = false,
    val usedIn: Int = 0,
)

@Serializable
data class MateriWrite(
    val aspek: Aspek,
    val mapel: String,
    val title: String,
    val body: String?,
    val arabic: String?,
    val translation: String?,
    val source: String?,
    val assessment: AssessmentMode,
)

@Serializable
data class KitabItem(
    val id: String,
    val title: String,
    val unit: KitabUnit,
    val total: Int,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val usedIn: Int = 0,
    val maxTo: Int = 0,
)

@Serializable
data class KitabWrite(val title: String, val unit: KitabUnit, val total: Int, val sortOrder: Int)

@Serializable
data class MapelName(val aspek: Aspek, val mapel: String)

@Serializable
data class PeriodWrite(val name: String, val semester: String, val startsOn: IsoDate, val endsOn: IsoDate)

// ---------------------------------------------------------------------------
// Capaian
// ---------------------------------------------------------------------------

@Serializable
data class TargetCount(val id: String, val total: Int = 0, val tuntas: Int = 0, val proses: Int = 0)

@Serializable
data class Delivery(val targetId: String, val deliveredOn: IsoDate, val note: String? = null)

@Serializable
data class KelompokSummary(val counts: List<TargetCount> = emptyList(), val deliveries: List<Delivery> = emptyList())

@Serializable
data class TargetRosterEntry(
    val generusId: String,
    val name: String,
    val gender: Gender,
    val kategori: String? = null,
    val aktif: Boolean = true,
    val status: CapaianStatus = CapaianStatus.belum,
    val note: String? = null,
    val position: Int? = null,
    val recordedOn: IsoDate? = null,
)

@Serializable
data class GenerusCapaianRow(
    val targetId: String,
    val status: CapaianStatus = CapaianStatus.belum,
    val note: String? = null,
    val position: Int? = null,
    val recordedOn: IsoDate? = null,
    val deliveredOn: IsoDate? = null,
)

@Serializable
data class GenerusCapaian(val jenjangId: String? = null, val kelompokId: String? = null, val rows: List<GenerusCapaianRow> = emptyList())

@Serializable
data class RekapUnit(
    val unitId: String,
    val generus: Int = 0,
    val slots: Int = 0,
    val tuntas: Int = 0,
    val proses: Int = 0,
    val kelompok: Int = 0,
    val delivered: Int = 0,
    val deliverable: Int = 0,
)

@Serializable
data class RekapTarget(
    val targetId: String,
    val slots: Int = 0,
    val tuntas: Int = 0,
    val proses: Int = 0,
    val delivered: Int = 0,
    val kelompok: Int = 0,
)

// ---------------------------------------------------------------------------
// Laporan
// ---------------------------------------------------------------------------

@Serializable
data class KehadiranUnit(
    val unitId: String,
    val generus: Int = 0,
    val agendas: Int = 0,
    val total: Int = 0,
    val hadir: Int = 0,
    val terlambat: Int = 0,
    val izin: Int = 0,
    val sakit: Int = 0,
    val alpa: Int = 0,
) {
    /** Persentase hadir (termasuk terlambat) dari presensi agenda terkunci; null bila belum ada. */
    val rate: Int? get() = if (total > 0) Math.round((hadir + terlambat) * 100.0 / total).toInt() else null
}

@Serializable
data class TrenRow(val bulan: IsoDate, val total: Int = 0, val hadir: Int = 0)

@Serializable
data class LaporanGenerusRow(
    val generusId: String,
    val name: String,
    val gender: Gender,
    val kelompok: String,
    val desa: String,
    val jenjang: String,
    val kategori: String? = null,
    val status: GenerusStatus,
    val total: Int = 0,
    val hadir: Int = 0,
    val terlambat: Int = 0,
    val izin: Int = 0,
    val sakit: Int = 0,
    val alpa: Int = 0,
    val capSlots: Int = 0,
    val capTuntas: Int = 0,
    val alpaStreak: Boolean = false,
)

@Serializable
data class PeringatanAlpa(val generusId: String, val name: String, val kelompok: String, val kelompokId: String, val lastAt: IsoInstant)
