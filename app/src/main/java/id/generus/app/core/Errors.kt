package id.generus.app.core

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Kesalahan isian yang pesannya aman ditampilkan apa adanya. */
class UserError(message: String) : Exception(message)

/** Kesalahan karena jaringan (boleh dicoba ulang otomatis). */
fun Throwable.isNetwork(): Boolean = this is HttpRequestException || this is IOException || cause?.let { it is IOException } == true

/**
 * Pesan yang aman ditampilkan. Pesan dari trigger/fungsi kita (RAISE EXCEPTION,
 * kode P0001) sudah berbahasa Indonesia; selain itu pakai pesan umum.
 * Padanan src/lib/db-error.ts.
 */
fun Throwable.userMessage(fallback: String = "Terjadi kesalahan. Coba lagi."): String = when (this) {
    is CancellationException -> throw this
    is UserError -> message ?: fallback
    is PostgrestRestException -> when (code) {
        "P0001" -> error.ifBlank { fallback }
        "23505" -> "Data yang sama sudah ada."
        "23503" -> "Data masih dipakai data lain sehingga tidak bisa dihapus."
        "42501" -> "Anda tidak punya akses untuk tindakan ini."
        "PGRST202" -> "Server belum diperbarui untuk aplikasi ini. Hubungi pengelola sistem."
        else -> fallback
    }
    is AuthRestException -> when (errorCode) {
        AuthErrorCode.InvalidCredentials -> "Email atau password salah."
        AuthErrorCode.UserBanned -> "Akun Anda dinonaktifkan. Hubungi superadmin."
        AuthErrorCode.SamePassword -> "Password baru harus berbeda dari password lama."
        AuthErrorCode.WeakPassword -> "Password terlalu lemah. Gunakan kombinasi huruf dan angka."
        else -> fallback
    }
    else -> if (isNetwork()) "Tidak ada koneksi ke server. Periksa sinyal lalu coba lagi." else fallback
}
