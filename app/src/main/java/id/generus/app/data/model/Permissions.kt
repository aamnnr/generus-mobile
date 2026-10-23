package id.generus.app.data.model

import id.generus.app.BuildConfig

// Izin di UI (padanan src/lib/auth.ts & src/components/shell/nav.ts). Hanya untuk
// menampilkan/menyembunyikan tombol; penegakan sebenarnya ada di RLS & trigger.

val AppRole.label: String
    get() = when (this) {
        AppRole.superadmin -> "Superadmin"
        AppRole.kurikulum -> "Tim Kurikulum"
        AppRole.admin -> "Admin"
        AppRole.kyai -> "Kyai"
    }

val UnitLevel.label: String
    get() = when (this) {
        UnitLevel.daerah -> "Daerah"
        UnitLevel.desa -> "Desa"
        UnitLevel.kelompok -> "Kelompok"
    }

val UnitLevel.rank: Int get() = ordinal

val GenerusStatus.label: String
    get() = when (this) {
        GenerusStatus.aktif -> "Aktif"
        GenerusStatus.pindah_sambung -> "Pindah Sambung"
        GenerusStatus.menikah -> "Menikah"
    }

val AttendanceStatus.label: String
    get() = when (this) {
        AttendanceStatus.hadir -> "Hadir"
        AttendanceStatus.terlambat -> "Terlambat"
        AttendanceStatus.izin -> "Izin"
        AttendanceStatus.sakit -> "Sakit"
        AttendanceStatus.alpa -> "Alpa"
    }

/** Huruf status di checklist: H/T/I/S/A. */
val AttendanceStatus.letter: String get() = label.take(1)

val Gender.label: String get() = if (this == Gender.L) "Laki-laki" else "Perempuan"

fun UserRole.label(): String =
    if (role == AppRole.admin || role == AppRole.kyai) "${role.label} ${level.label}" else role.label

fun jenjangLabel(jenjang: String, kategori: String?): String = if (kategori != null) "$jenjang $kategori" else jenjang

val Me.isSuperadmin: Boolean get() = roles.any { it.role == AppRole.superadmin }

/** Boleh melihat data generus, agenda, presensi, laporan (semua peran kecuali Tim Kurikulum). */
val Me.canReadGenerus: Boolean get() = roles.any { it.role != AppRole.kurikulum }

/** Boleh menambah/mengubah generus & agenda di lingkupnya. */
val Me.canWriteGenerus: Boolean get() = roles.any { it.role == AppRole.admin || it.role == AppRole.superadmin }

/** Boleh memindah generus antar kelompok (admin desa/daerah & superadmin). */
val Me.canMutateGenerus: Boolean
    get() = roles.any { it.role == AppRole.superadmin || (it.role == AppRole.admin && it.level != UnitLevel.kelompok) }

/** Penyusun kurikulum: Tim Kurikulum & superadmin. */
val Me.canEditCurriculum: Boolean get() = roles.any { it.role == AppRole.kurikulum || it.role == AppRole.superadmin }

/** Modul kurikulum/capaian aktif di server ini (FEATURE_KURIKULUM). */
val kurikulumEnabled: Boolean get() = BuildConfig.FEATURE_KURIKULUM

/** Unit tertinggi tempat user boleh melihat rekap capaian; null bila hanya berperan di kelompok. */
fun Me.rekapRootUnit(): UserRole? = roles.minByOrNull { it.level.rank }?.takeIf { it.level != UnitLevel.kelompok }

/** Unit tertinggi tempat user boleh melihat laporan presensi (bukan peran kurikulum). */
fun Me.laporanRootUnit(): UserRole? = roles.filter { it.role != AppRole.kurikulum }.minByOrNull { it.level.rank }

fun initials(name: String): String =
    name.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
