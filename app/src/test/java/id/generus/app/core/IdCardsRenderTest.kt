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
    }
}
