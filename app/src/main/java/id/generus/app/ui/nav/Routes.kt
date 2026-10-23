package id.generus.app.ui.nav

import kotlinx.serialization.Serializable

// Semua tujuan navigasi (type-safe Navigation Compose). Padanan rute PWA di komentar.

// Tab tingkat atas
@Serializable data object Beranda // /beranda
@Serializable data object AgendaList // /agenda
@Serializable data object GenerusList // /generus
@Serializable data object CapaianHome // /capaian
@Serializable data object KurikulumHome // /kurikulum
@Serializable data object Laporan // /laporan
@Serializable data object Lainnya // /lainnya

// Agenda & presensi
@Serializable data class AgendaDetail(val id: String) // /agenda/[id]
@Serializable data class AgendaForm(val id: String? = null) // /agenda/baru, /agenda/[id]/ubah
@Serializable data class Scanner(val agendaId: String) // /pindai?agenda=

// Generus
@Serializable data class GenerusDetail(val id: String) // /generus/[id]
@Serializable data class GenerusForm(val id: String? = null) // /generus/baru, ubah di detail
@Serializable data class Kartu(val unitId: String? = null, val generusId: String? = null) // /generus/kartu
@Serializable data object Kenaikan // /generus/kenaikan
@Serializable data object ImportGenerus // /generus/import

// Capaian
@Serializable data class CapaianTarget(val targetId: String, val kelompokId: String) // /capaian/target/[id]
@Serializable data class CapaianKitab(val targetId: String, val kelompokId: String) // /capaian/kitab/[id]
@Serializable data class CapaianGenerus(val generusId: String, val periodId: String? = null) // /capaian/generus/[id]
@Serializable data class RekapCapaian(val unitId: String? = null, val periodId: String? = null, val jenjangId: String? = null) // /capaian/rekap

// Kurikulum
@Serializable data class PeriodDetail(val id: String) // /kurikulum/periode/[id]
@Serializable data class PeriodForm(val id: String? = null) // /kurikulum/periode/baru
@Serializable data class TargetForm(val periodId: String, val jenjangId: String, val targetId: String? = null)
@Serializable data class MateriForm(val id: String? = null) // /kurikulum/materi/baru, /kurikulum/materi/[id]

// Admin & akun
@Serializable data object AdminWilayah // /admin/wilayah
@Serializable data object AdminAkun // /admin/akun
@Serializable data class AkunDetail(val id: String) // /admin/akun/[id]
@Serializable data object AkunBaru // /admin/akun/baru
@Serializable data object GantiPassword // /akun/password
