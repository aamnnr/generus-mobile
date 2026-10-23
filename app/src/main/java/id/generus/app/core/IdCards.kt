package id.generus.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import id.generus.app.R
import id.generus.app.data.model.Gender
import id.generus.app.data.model.KartuRow
import id.generus.app.data.model.jenjangLabel
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Kartu identitas generus: ukuran KTP berdiri (54 × 85,6 mm) untuk lanyard.
 * Tata letak & ukuran mengikuti CSS `.idc-*` di PWA (src/app/globals.css) agar
 * hasil cetak sama. Pratinjau layar dan PDF memakai gambar yang sama.
 *
 * Koordinat digambar dalam satuan 0,1 mm (bukan mm): teks berukuran 2–4 mm yang
 * digambar lalu diperbesar membuat jarak huruf rusak di Android.
 */
class IdCards(context: Context) {
    private val sansBase: Typeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.DEFAULT
    private val loraBase: Typeface = ResourcesCompat.getFont(context, R.font.lora) ?: Typeface.SERIF
    private val sans400 = weight(sansBase, 400)
    private val sans600 = weight(sansBase, 600)
    private val sans700 = weight(sansBase, 700)
    private val sans800 = weight(sansBase, 800)
    private val lora600 = weight(loraBase, 600)

    companion object {
        const val W = 54f
        const val H = 85.6f
        const val PER_SHEET = 9

        /** Satuan gambar per mm. */
        private const val K = 10f
        private const val GREEN = 0xFF1A4A39.toInt()
        private const val BRASS = 0xFFC8A45C.toInt()
        private const val CREAM = 0xFFFBF8F1.toInt()
        private const val PT_PER_MM = 72f / 25.4f

        private fun weight(base: Typeface, w: Int): Typeface =
            if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, w, false) else Typeface.create(base, if (w >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Pratinjau satu kartu dengan lebar `widthPx`. */
    fun bitmap(card: KartuRow, widthPx: Int): Bitmap {
        val scale = widthPx / W
        val bmp = Bitmap.createBitmap(widthPx, (H * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        draw(canvas, card)
        return bmp
    }

    /** PDF A4, 9 kartu per lembar (3 × 3), dengan garis potong putus-putus (seperti cetak PWA). */
    fun pdf(cards: List<KartuRow>, out: File): File {
        val doc = PdfDocument()
        val pageW = (210 * PT_PER_MM).toInt()
        val pageH = (297 * PT_PER_MM).toInt()
        val gapX = 6f
        val gapY = 4f
        val left = (210f - (3 * W + 2 * gapX)) / 2f
        val top = (297f - (3 * H + 2 * gapY)) / 2f
        val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.25f
            color = 0xFFA3A3A3.toInt()
            pathEffect = DashPathEffect(floatArrayOf(1f, 0.8f), 0f)
        }
        cards.chunked(PER_SHEET).forEachIndexed { pageIndex, chunk ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageIndex + 1).create())
            val c = page.canvas
            c.scale(PT_PER_MM, PT_PER_MM)
            chunk.forEachIndexed { i, card ->
                val x = left + (i % 3) * (W + gapX)
                val y = top + (i / 3) * (H + gapY)
                c.save()
                c.translate(x, y)
                draw(c, card)
                // outline 0,25 mm putus-putus, offset 0,6 mm.
                c.drawRoundRect(RectF(-0.6f, -0.6f, W + 0.6f, H + 0.6f), 3.6f, 3.6f, cut)
                c.restore()
            }
            doc.finishPage(page)
        }
        out.parentFile?.mkdirs()
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun paint(sizeMm: Float, color: Int, face: Typeface, spacingMm: Float = 0f, align: Paint.Align = Paint.Align.LEFT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            textSize = sizeMm * K
            this.color = color
            typeface = face
            textAlign = align
            if (spacingMm != 0f) letterSpacing = spacingMm / sizeMm
        }

    /** Teks satu baris, dipotong "…" bila melebihi `maxMm`. Koordinat dalam mm (baseline). */
    private fun Canvas.text(s: String, xMm: Float, baselineMm: Float, p: TextPaint, maxMm: Float = W) {
        val fitted = TextUtils.ellipsize(s, p, maxMm * K, TextUtils.TruncateAt.END).toString()
        drawText(fitted, xMm * K, baselineMm * K, p)
    }

    private fun draw(c: Canvas, card: KartuRow) {
        c.save()
        c.scale(1f / K, 1f / K)
        val w = W * K
        val h = H * K
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        // .idc: putih, radius 3 mm, isi terpotong.
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w, h), 3 * K, 3 * K, Path.Direction.CW) })
        fill.color = Color.WHITE
        c.drawRect(0f, 0f, w, h, fill)

        // .idc-top: tinggi 23 mm, hijau, sudut bawah melengkung elips (50% × 4 mm).
        val topH = 23 * K
        val topPath = Path().apply {
            addRoundRect(RectF(0f, 0f, w, topH), floatArrayOf(0f, 0f, 0f, 0f, w / 2, 4 * K, w / 2, 4 * K), Path.Direction.CW)
        }
        fill.color = GREEN
        c.drawPath(topPath, fill)
        c.save()
        c.clipPath(topPath)
        // .idc-star: bintang garis 26 mm, kanan −6 mm, bawah −9 mm, putih 12%.
        drawStar(c, (W + 6 - 13) * K, (23 + 9 - 13) * K, 26 * K, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 26 * K / 24
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(31, 255, 255, 255)
        })
        c.restore()
        // .idc-slot: lubang tali 13 × 2,8 mm, garis putih 55%.
        c.drawRoundRect(
            RectF((W / 2 - 6.5f) * K, 2.6f * K, (W / 2 + 6.5f) * K, 5.4f * K), 1.4f * K, 1.4f * K,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.3f * K; color = Color.argb(140, 255, 255, 255) },
        )
        // .idc-brand: logo 10 mm + "KARTU GENERUS" / "Daerah …".
        drawLogo(c, 3.5f * K, 7.5f * K, 10 * K)
        val textX = 3.5f + 10 + 2
        c.text("KARTU GENERUS", textX, 11.8f, paint(2.5f, Color.WHITE, sans800, spacingMm = 0.35f), W - textX - 3)
        val daerah = card.daerah?.let { if (Regex("^daerah\\b", RegexOption.IGNORE_CASE).containsMatchIn(it)) it else "Daerah $it" } ?: ""
        c.text(daerah, textX, 14.9f, paint(2.6f, Color.argb(217, 255, 255, 255), sans400), W - textX - 3)

        // .idc-body: rata tengah, padding atas 2,4 mm, jarak 1 mm.
        val cx = W / 2
        var y = 23 + 2.4f
        val namePaint = paint(4.1f, 0xFF171717.toInt(), lora600)
        val nameWidth = ((W - 7) * K).toInt()
        fun layout(text: String, ellipsize: Boolean) = StaticLayout.Builder.obtain(text, 0, text.length, namePaint, nameWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.05f)
            .setIncludePad(false)
            .apply { if (ellipsize) setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END) }
            .build()
        // Nama yang tidak muat 2 baris: kata terakhir disingkat jadi huruf awal + titik
        // ("Hidayatullah" → "H."), mundur ke kata sebelumnya sampai muat. "…" hanya cadangan.
        // Berbeda dengan PWA (.idc-name memotong dengan "…"), atas permintaan pengguna.
        val variants = nameVariants(card.name)
        val nameLayout = variants.firstNotNullOfOrNull { v -> layout(v, false).takeIf { it.lineCount <= 2 } }
            ?: layout(variants.last(), true)
        c.save()
        c.translate(3.5f * K, y * K)
        nameLayout.draw(c)
        c.restore()
        y += nameLayout.height / K + 1f

        c.text("Kelompok ${card.kelompok} · Desa ${card.desa}", cx, y + 2.2f, paint(2.35f, 0xFF404040.toInt(), sans400, align = Paint.Align.CENTER), W - 7)
        y += 2.95f + 1f + 0.4f

        // .idc-pill
        val pillText = "${jenjangLabel(card.jenjang, card.kategori)} · ${if (card.gender == Gender.L) "Putra" else "Putri"}"
        val pillPaint = paint(2.3f, GREEN, sans700, align = Paint.Align.CENTER)
        val pillW = (pillPaint.measureText(pillText) / K + 4.4f).coerceAtMost(W - 7)
        val pillH = 3.9f
        fill.color = 0xFFE6EFE8.toInt()
        c.drawRoundRect(RectF((cx - pillW / 2) * K, y * K, (cx + pillW / 2) * K, (y + pillH) * K), pillH / 2 * K, pillH / 2 * K, fill)
        c.text(pillText, cx, y + 2.75f, pillPaint, W - 9)
        y += pillH + 1f + 1.4f

        // .idc-qr: 27 mm, padding 1,2 mm, garis kuningan 0,3 mm, radius 1,5 mm. QR selalu hitam di atas putih.
        val box = RectF((cx - 13.5f) * K, y * K, (cx + 13.5f) * K, (y + 27) * K)
        fill.color = Color.WHITE
        c.drawRoundRect(box, 1.5f * K, 1.5f * K, fill)
        c.drawRoundRect(
            RectF(box.left + 0.15f * K, box.top + 0.15f * K, box.right - 0.15f * K, box.bottom - 0.15f * K), 1.5f * K, 1.5f * K,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.3f * K; color = BRASS },
        )
        val inset = 1.5f * K
        c.drawBitmap(
            Qr.bitmap(Qr.payload(card.token), 400, margin = 0),
            null,
            RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset),
            Paint().apply { isFilterBitmap = false },
        )
        y += 27 + 1f

        // .idc-token: monospace 2,1 mm, jarak huruf 0,25 mm.
        c.text(card.token.chunked(4).joinToString(" "), cx, y + 2.4f, paint(2.1f, 0xFF525252.toInt(), Typeface.MONOSPACE, spacingMm = 0.25f, align = Paint.Align.CENTER))

        // .idc-foot: 5,2 mm, krem, teks coklat 1,9 mm.
        fill.color = 0xFFF1E9D6.toInt()
        c.drawRect(0f, h - 5.2f * K, w, h, fill)
        c.text("Tunjukkan kartu ini saat presensi pengajian", cx, H - 1.95f, paint(1.9f, 0xFF5C4A1F.toInt(), sans600, align = Paint.Align.CENTER), W - 4)
        c.restore()
    }

    /**
     * Bentuk nama dari utuh hingga paling singkat: kata terakhir, lalu kata sebelumnya,
     * disingkat menjadi huruf awal kapital + titik. Kata pertama tidak pernah disingkat.
     */
    fun nameVariants(name: String): List<String> {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 2) return listOf(name.trim())
        val out = mutableListOf(words.joinToString(" "))
        val cur = words.toMutableList()
        for (i in words.lastIndex downTo 1) {
            if (cur[i].length <= 2 && cur[i].endsWith(".")) continue
            cur[i] = cur[i].first().uppercaseChar() + "."
            out += cur.joinToString(" ")
        }
        return out
    }

    /** Logo varian ikon (bintang kuningan + masjid krem, pintu tembus), kotak `size`. */
    private fun drawLogo(c: Canvas, x: Float, y: Float, size: Float) {
        c.save()
        c.translate(x, y)
        c.scale(size / 100f, size / 100f)
        drawStar(c, 50f, 47f, 88f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f; color = BRASS })
        val mosque = PathParser.createPathFromPathData("M50 22C52 29 59 33 65 38C71 43 71 51 67 56L69 56L69 92L31 92L31 56L33 56C29 51 29 43 35 38C41 33 48 29 50 22Z")
        // Celah antara bintang dan masjid.
        c.drawPath(mosque, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL_AND_STROKE; strokeWidth = 9f; strokeJoin = Paint.Join.ROUND; color = GREEN })
        c.drawPath(mosque, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CREAM })
        val door = PathParser.createPathFromPathData("M42.5 92L42.5 72C42.5 66.5 46 63 50 60C54 63 57.5 66.5 57.5 72L57.5 92Z")
        c.drawPath(door, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN })
        c.restore()
    }

    private fun drawStar(c: Canvas, cx: Float, cy: Float, diameter: Float, paint: Paint) {
        val r = diameter / 2f
        val inner = r * sqrt(0.5f) / cos(Math.PI.toFloat() / 8f)
        val path = Path()
        for (i in 0 until 16) {
            val a = i * Math.PI.toFloat() / 8f - Math.PI.toFloat() / 2f
            val d = if (i % 2 == 0) r else inner
            val px = cx + d * cos(a)
            val py = cy + d * sin(a)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, paint)
    }
}
