package id.generus.app.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Berkas sementara yang bisa dibagikan ke aplikasi lain (WhatsApp, Drive, cetak). */
object Share {
    fun dir(context: Context): File = File(context.cacheDir, "shared").apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    /** Buka pemilih "Bagikan / Buka dengan" untuk berkas di folder bersama. */
    fun send(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, title).apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(view))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /** Salin aset bawaan aplikasi (mis. template Excel) ke folder bersama lalu bagikan. */
    fun asset(context: Context, assetName: String, mime: String, title: String) {
        val out = file(context, assetName)
        context.assets.open(assetName).use { input -> out.outputStream().use { input.copyTo(it) } }
        send(context, out, mime, title)
    }

    const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val PDF = "application/pdf"
}
