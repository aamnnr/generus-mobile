# Generus Mobile — aplikasi Android (Kotlin)

Versi Android native dari PWA **Generus** (`../generus`, https://github.com/aamnnr/generus): presensi
pengajian (checklist & scan QR offline), data generus, kartu QR, capaian target & progres kitab,
kurikulum, laporan, serta admin wilayah & akun. Bahasa UI: Indonesia. Desain mengikuti
`generus/docs/DESIGN.md` (hijau tua + krem, Plus Jakarta Sans / Lora / Amiri).

## Arsitektur

```
Android (Compose)  ──HTTPS──▶  Supabase project yang sama dengan PWA
  ├─ Supabase Auth (email + password; sesi tersimpan di perangkat)
  ├─ PostgREST: tabel (tulis sederhana) + RPC public.mobile_* (query gabungan)
  └─ Edge Function admin-akun (buat akun / reset password / blokir — butuh service role)
```

- **Tidak ada backend baru.** Semua aturan akses tetap ditegakkan RLS & trigger yang sama dengan PWA.
  Query gabungan yang di PWA ada di `src/lib/*.ts` disediakan sebagai fungsi RPC
  `public.mobile_*` (SECURITY INVOKER) di migrasi PWA `drizzle/0014_mobile_api.sql`, dites di
  `tests/db/mobile-api.test.ts`.
- **Offline-first presensi** (`data/PresensiStore.kt`, Room): ketukan checklist & hasil scan masuk
  antrian di perangkat, dikirim saat online, tiap 20 detik saat aplikasi terbuka, dan oleh
  **WorkManager saat aplikasi ditutup** (kelebihan dibanding PWA di iOS). Roster pemindai berisi
  hash SHA-256 isi QR, bukan token. Beranda, daftar & detail agenda juga disalin ke perangkat.
- **Pemindai**: CameraX + ML Kit (on-device), bunyi & getar, cari nama sebagai cadangan.
- **Kartu QR**: digambar di perangkat (54 × 85,6 mm, potret) → PDF A4 9 kartu/lembar untuk
  dicetak atau dibagikan. **Import Excel** & **ekspor laporan Excel** juga di perangkat.

| Folder | Isi |
|---|---|
| `core/` | Waktu (WIB), Al-Qur'an (surat:ayat), QR, format kurikulum, aturan kenaikan, XLSX baca/tulis, kartu ID |
| `data/` | Repository Supabase per modul, model, sesi, antrian presensi, validasi import |
| `data/local/` | Room (antrian & roster), cache JSON, pemantau jaringan |
| `ui/components/` | Komponen sistem desain (kartu, hero, chip, badge status, form, sheet) |
| `ui/screens/*` | Layar per modul (padanan rute PWA tertulis di `ui/nav/Routes.kt`) |

## Menjalankan

Butuh JDK 17+ dan Android SDK (platform 36). Proyek memakai Gradle wrapper.

1. Salin `secrets.properties.example` → `secrets.properties`, isi URL & anon/publishable key
   server **uji** dan **resmi** (Supabase → Project Settings → API). Jangan isi service role key.
2. Pastikan migrasi `0014_mobile_api` sudah diterapkan ke database server yang dipakai (lihat
   bagian berikut), lalu:

```bash
./gradlew assembleUjiDebug
```

APK: `app/build/outputs/apk/uji/debug/app-uji-debug.apk`. Flavor:

| Flavor | Server | Modul kurikulum/capaian | Application ID |
|---|---|---|---|
| `uji` | server uji | aktif | `id.generus.app.uji` |
| `resmi` | server resmi | mengikuti `RESMI_FEATURE_KURIKULUM` (bawaan mati, Tahap 1) | `id.generus.app` |

Keduanya bisa terpasang berdampingan. `./gradlew assembleResmiRelease` membuat APK rilis
(R8); tanpa keystore rilis, APK ditandatangani kunci debug — siapkan keystore sendiri sebelum
dibagikan luas / Play Store.

Tes logika murni (Al-Qur'an, kenaikan, sasaran agenda, QR, validasi import):

```bash
./gradlew testUjiDebugUnitTest
```

## Menyiapkan server (sekali per project Supabase)

Di folder PWA (`../generus`):

```bash
pnpm test                 # termasuk tests/db/mobile-api.test.ts
pnpm db:migrate           # server uji  (atau: pnpm resmi:migrate untuk server resmi)
supabase functions deploy admin-akun --project-ref <ref>   # menu Akun di aplikasi
```

Tanpa Edge Function, semua fitur jalan kecuali buat akun, reset password, dan
aktif/nonaktifkan akun (tetap bisa dari PWA).

## Pemetaan fitur PWA → Android

Beranda, Agenda (daftar, buat/ubah berulang mingguan, batal, kunci, petugas scan), Presensi
(checklist H/T/I/S/A offline, hadirkan semua, delegasi admin kelompok), Pemindai QR, Generus
(daftar & filter, detail, tambah/ubah, status menikah/pindah sambung, mutasi, hapus, terbitkan
ulang kartu), Kartu QR (PDF), Kenaikan jenjang massal, Import Excel, Capaian (per kelompok,
checklist per materi, tanda disampaikan, progres kitab massal, per generus), Rekap capaian lintas
unit, Kurikulum (periode, salin periode, terbitkan, target per jenjang & urutan, pustaka materi
dengan teks Arab, kitab), Laporan (bulan, drill-down, tren, peringatan alpa 3×, per generus,
ekspor Excel), Admin wilayah & akun, Ganti password (termasuk wajib ganti saat login pertama).
