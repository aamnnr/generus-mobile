package id.generus.app.core

import id.generus.app.BuildConfig

/**
 * Merek tampilan, dipilih saat build (`-Pbrand=kliik|generus`, bawaan kliik). Padanan
 * src/lib/brand.ts di PWA: KLIIK JM (navy + cyan) atau desain lama Generus (hijau + kuningan).
 * Tidak ada data yang berubah; hanya warna, logo, ikon, dan kartu QR.
 */
object Brand {
    val isKliik: Boolean = BuildConfig.BRAND != "generus"

    val name: String = if (isKliik) "KLIIK JM" else "Generus"

    val fullName: String = if (isKliik) "KLIIK JM — Klaten Timur II Kontrol Jurnal Mengaji" else "Pantau Generus"
}
