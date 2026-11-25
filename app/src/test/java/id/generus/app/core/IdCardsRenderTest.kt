package id.generus.app.core

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import id.generus.app.data.model.Gender
import id.generus.app.data.model.KartuRow
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Merender kartu ke PNG (app/build/kartu-preview.png) untuk diperiksa secara visual. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class IdCardsRenderTest {
    @Test fun renderPreview() {
        val cards = IdCards(ApplicationProvider.getApplicationContext())
        val card = KartuRow("1", "Ahmad Nouval A.", "F12B78BFDA9B4509", Gender.L, "Jambon", "Samber", "Klaten Timur 1", "Pra Remaja", null)
        val bmp = cards.bitmap(card, 540)
        assertEquals(540, bmp.width)
        val out = File("build/kartu-preview.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertEquals(
            listOf("Muhammad Abdurrahman Al-Fatih Nur", "Muhammad Abdurrahman Al-Fatih N.", "Muhammad Abdurrahman A. N.", "Muhammad A. A. N."),
            cards.nameVariants("Muhammad  Abdurrahman Al-Fatih Nur"),
        )
        assertEquals(listOf("Ahmad"), cards.nameVariants("Ahmad"))
        val long = card.copy(name = "Muhammad Abdurrahman Al-Fatih Nur Hidayatullah", jenjang = "Remaja", kategori = "Pelajar", gender = Gender.P)
        File("build/kartu-preview-long.png").outputStream().use { cards.bitmap(long, 540).compress(Bitmap.CompressFormat.PNG, 100, it) }
        File("build/kartu-preview-qr.png").outputStream().use { cards.qrBitmap(card).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Ikon & logo merek (vector drawable) ke PNG untuk diperiksa. */
    @Test fun renderBrandDrawables() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        fun render(id: Int, name: String, w: Int, h: Int, bg: Int) {
            val d = androidx.core.content.ContextCompat.getDrawable(ctx, id)!!
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            c.drawColor(bg)
            d.setBounds(0, 0, w, h)
            d.draw(c)
            File("build/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        render(id.generus.app.R.drawable.ic_launcher_foreground, "brand-launcher", 432, 432, android.graphics.Color.WHITE)
        render(id.generus.app.R.drawable.ic_launcher_monochrome, "brand-launcher-mono", 432, 432, android.graphics.Color.DKGRAY)
        render(id.generus.app.R.drawable.kliik_icon, "brand-icon", 256, 256, android.graphics.Color.LTGRAY)
        render(id.generus.app.R.drawable.kliik_logo, "brand-logo", 937, 355, android.graphics.Color.WHITE)
        render(id.generus.app.R.drawable.kliik_logo_putih, "brand-logo-putih", 937, 355, 0xFF1A2189.toInt())
    }
}
