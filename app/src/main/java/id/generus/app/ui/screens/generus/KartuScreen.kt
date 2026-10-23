package id.generus.app.ui.screens.generus

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import id.generus.app.ui.components.appear
import id.generus.app.ui.components.shimmer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.IdCards
import id.generus.app.core.Share
import id.generus.app.core.userMessage
import id.generus.app.data.model.KartuRow
import id.generus.app.data.model.ScopeUnit
import id.generus.app.data.model.UnitLevel
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.InfoBanner
import id.generus.app.ui.components.LoadingBox
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.RefreshableList
import id.generus.app.ui.components.SelectField
import id.generus.app.ui.vm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_CARDS = 600

class KartuVm(private val c: AppContainer, private val generusId: String?, initialUnit: String?) : ViewModel() {
    private val _units = MutableStateFlow<List<ScopeUnit>>(emptyList())
    val units: StateFlow<List<ScopeUnit>> = _units.asStateFlow()
    private val _unitId = MutableStateFlow(initialUnit)
    val unitId: StateFlow<String?> = _unitId.asStateFlow()
    private val _cards = MutableStateFlow<List<KartuRow>?>(null)
    val cards: StateFlow<List<KartuRow>?> = _cards.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { c.generus.scopeUnits("read") }.onSuccess { list ->
                _units.value = list
                if (_unitId.value == null && generusId == null) {
                    // Hanya satu kelompok yang bisa ditulis: langsung tampilkan.
                    list.filter { it.level == UnitLevel.kelompok }.singleOrNull()?.let { _unitId.value = it.id }
                }
            }
            if (generusId != null || _unitId.value != null) fetch()
        }
    }

    fun select(unit: ScopeUnit) {
        _unitId.value = unit.id
        fetch()
    }

    fun fetch() {
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                _cards.value = c.generus.kartu(if (generusId == null) _unitId.value else null, generusId?.let { listOf(it) })
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = e.userMessage("Gagal memuat data kartu.")
            } finally {
                _loading.value = false
            }
        }
    }
}

/** Kartu QR generus (padanan /generus/kartu): pratinjau + PDF A4 9 kartu per lembar untuk dicetak/dibagikan. */
@Composable
fun KartuScreen(nav: NavHostController, unitId: String?, generusId: String?) {
    val context = LocalContext.current
    val toast = LocalToast.current
    val model = vm(key = "kartu-$unitId-$generusId") { KartuVm(it, generusId, unitId) }
    val units by model.units.collectAsStateWithLifecycle()
    val selected by model.unitId.collectAsStateWithLifecycle()
    val cards by model.cards.collectAsStateWithLifecycle()
    val loading by model.loading.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val renderer = remember { IdCards(context.applicationContext) }
    var exporting by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun exportPdf(list: List<KartuRow>) {
        exporting = true
        scope.launch {
            try {
                val name = if (list.size == 1) "Kartu ${list.first().name}.pdf" else "Kartu Generus (${list.size}).pdf"
                val file = withContext(Dispatchers.Default) { renderer.pdf(list, Share.file(context, name.replace(Regex("[^A-Za-z0-9 ().-]"), ""))) }
                Share.send(context, file, Share.PDF, "Cetak / bagikan kartu")
            } catch (e: Throwable) {
                toast("Gagal membuat PDF kartu.")
            } finally {
                exporting = false
            }
        }
    }

    AppScreen("Kartu QR", onBack = { nav.popBackStack() }) { p ->
        RefreshableList(refreshing = loading && cards != null, onRefresh = model::fetch, padding = p) {
            item {
                Text(
                    "Kartu identitas berdiri ukuran KTP (54 × 85,6 mm) untuk lanyard, 9 kartu per lembar A4. Buat PDF lalu cetak di skala 100%, atau bagikan ke WhatsApp.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (generusId == null) item {
                SelectField(
                    "Wilayah",
                    units,
                    units.firstOrNull { it.id == selected },
                    { if (it.level == UnitLevel.desa) "Seluruh Desa ${it.name}" else "Kelompok ${it.name}" },
                    model::select,
                    group = { "Desa ${it.desaName}" },
                    placeholder = "Pilih desa / kelompok",
                )
            }
            val list = cards
            when {
                error != null -> item { ErrorBox(error!!, model::fetch) }
                list == null && loading -> item { LoadingBox() }
                list == null -> item { EmptyState("Pilih wilayah", body = "Kartu generus aktif di wilayah itu akan ditampilkan.") }
                list.isEmpty() -> item { EmptyState("Tidak ada generus aktif", body = "Hanya generus aktif dengan kartu aktif yang bisa dicetak.") }
                else -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${list.size} kartu · ${(list.size + IdCards.PER_SHEET - 1) / IdCards.PER_SHEET} lembar", style = MaterialTheme.typography.titleSmall)
                            if (list.size >= MAX_CARDS) InfoBanner("Maksimal $MAX_CARDS kartu sekali cetak; pilih wilayah lebih kecil.")
                            PrimaryButton("Buat PDF & bagikan", onClick = { exportPdf(list) }, loading = exporting, icon = Icons.Rounded.PictureAsPdf, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    // Satu kartu per baris, ukuran nyaman dibaca (seperti pratinjau PWA di HP).
                    items(list, key = { it.id }) { card ->
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            CardPreview(renderer, card, Modifier.widthIn(max = 300.dp).fillMaxWidth())
                        }
                    }
                    item {
                        Text(
                            "QR hanya berisi kode acak, bukan data pribadi. Potong mengikuti garis putus-putus, lalu lubangi tanda di atas untuk tali lanyard. Kartu hilang? Terbitkan ulang dari halaman generus.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardPreview(renderer: IdCards, card: KartuRow, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, card) {
        value = withContext(Dispatchers.Default) { renderer.bitmap(card, 900).asImageBitmap() }
    }
    // Garis potong putus-putus di sekeliling kartu, seperti di PWA.
    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    androidx.compose.foundation.layout.Box(
        modifier
            .aspectRatio(IdCards.W / IdCards.H)
            .drawBehind {
                drawRoundRect(
                    color = androidx.compose.ui.graphics.Color(0xFFA3A3A3),
                    topLeft = androidx.compose.ui.geometry.Offset(-6f, -6f),
                    size = androidx.compose.ui.geometry.Size(size.width + 12f, size.height + 12f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(36f, 36f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f, pathEffect = dash),
                )
            },
    ) {
        if (image != null) {
            Image(image!!, contentDescription = "Kartu ${card.name}", modifier = Modifier.fillMaxWidth().appear())
        } else {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().aspectRatio(IdCards.W / IdCards.H).clip(RoundedCornerShape(12.dp)).shimmer())
        }
    }
}
