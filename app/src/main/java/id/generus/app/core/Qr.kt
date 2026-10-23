package id.generus.app.core

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.security.MessageDigest

// Isi QR kartu generus. Harus sama dengan app.qr_payload() di database dan src/lib/qr.ts.
object Qr {
    const val PREFIX = "GNR1:"
    private val PATTERN = Regex("^GNR1:[0-9A-F]{16}$")

    fun payload(token: String): String = "$PREFIX$token"

    /** Normalisasi hasil scan / ketikan (scanner keyboard bisa menambah spasi atau huruf kecil). */
    fun normalize(raw: String): String? {
        val s = raw.trim().uppercase().replace(Regex("\\s+"), "")
        return if (PATTERN.matches(s)) s else null
    }

    /** SHA-256 hex, sama dengan token_hash dari app.agenda_roster(). */
    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Gambar QR hitam di atas putih (kartu harus tetap terbaca saat dicetak hitam-putih). */
    fun bitmap(content: String, size: Int = 512, margin: Int = 1): Bitmap {
        val matrix = QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to margin),
        )
        val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
    }
}
