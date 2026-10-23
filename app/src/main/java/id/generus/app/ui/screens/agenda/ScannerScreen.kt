package id.generus.app.ui.screens.agenda

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import id.generus.app.AppContainer
import id.generus.app.core.formatDateTime
import id.generus.app.core.formatId
import id.generus.app.core.formatTime
import id.generus.app.core.userMessage
import id.generus.app.data.ScanResult
import id.generus.app.data.local.RosterItem
import id.generus.app.data.model.AttendanceStatus
import id.generus.app.data.model.label
import id.generus.app.ui.appContainer
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.BannerTone
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.InfoBanner
import id.generus.app.ui.components.ListCard
import id.generus.app.ui.components.ListRow
import id.generus.app.ui.components.OfflineStrip
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.RowDivider
import id.generus.app.ui.components.SearchPill
import id.generus.app.ui.components.SecondaryButton
import id.generus.app.ui.components.StatusBadge
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.theme.of
import id.generus.app.ui.vm
import java.time.Instant
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScanEvent(val result: ScanResult, val at: Instant = Instant.now())

class ScannerVm(private val c: AppContainer, private val agendaId: String) : ViewModel() {
    val agenda = c.presensi.observeScanAgenda(agendaId)
    val roster = c.presensi.observeRoster(agendaId)
    val outbox = c.presensi.observeOutbox(agendaId)

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()
    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    init { download() }

    /** Unduh daftar peserta (bila online). Gagal → tetap pakai unduhan sebelumnya. */
    fun download() {
        if (_downloading.value) return
        _downloading.value = true
        viewModelScope.launch {
            try {
                c.presensi.downloadRoster(agendaId)
                _downloadError.value = null
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _downloadError.value = e.userMessage("Gagal mengunduh daftar peserta.")
            } finally {
                _downloading.value = false
            }
        }
    }

    suspend fun scan(raw: String): ScanResult = c.presensi.processScan(agendaId, raw)
    suspend fun search(generusId: String): ScanResult = c.presensi.recordBySearch(agendaId, generusId)
}

/** Pemindai QR offline (padanan /pindai, DESIGN.md §7.11 & §8.4). */
@Composable
fun ScannerScreen(nav: NavHostController, agendaId: String) {
    val c = appContainer()
    val context = LocalContext.current
    val model = vm(key = "scan-$agendaId") { ScannerVm(it, agendaId) }
    val agenda by model.agenda.collectAsStateWithLifecycle(null)
    val roster by model.roster.collectAsStateWithLifecycle(emptyList())
    val outbox by model.outbox.collectAsStateWithLifecycle(emptyList())
    val downloading by model.downloading.collectAsStateWithLifecycle()
    val downloadError by model.downloadError.collectAsStateWithLifecycle()
    val online by c.network.online.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val feedback = remember { Feedback(context) }
    DisposableEffect(Unit) { onDispose { feedback.release() } }

    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    LaunchedEffect(Unit) { if (!hasCamera) permission.launch(Manifest.permission.CAMERA) }

    var last by remember { mutableStateOf<ScanEvent?>(null) }
    val history = remember { mutableStateListOf<ScanEvent>() }
    var query by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    fun show(r: ScanResult) {
        val e = ScanEvent(r)
        last = e
        history.add(0, e)
        if (history.size > 20) history.removeAt(history.lastIndex)
        feedback.play(r)
    }

    val outboxStatus = outbox.associate { it.generusId to it.status }
    val recorded = roster.count { (outboxStatus[it.generusId] ?: it.status) != null }

    AppScreen(
        agenda?.title ?: "Pemindai",
        onBack = { nav.popBackStack() },
        subtitle = agenda?.let { "${it.unitName} · terlambat setelah ${Instant.ofEpochMilli(it.lateAfter).formatTime()}" },
        actions = {
            IconButton(onClick = model::download, enabled = online && !downloading) {
                Icon(Icons.Rounded.CloudDownload, contentDescription = "Unduh ulang daftar peserta")
            }
        },
    ) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!online) item { OfflineStrip("Offline: hasil scan disimpan di perangkat dan dikirim otomatis saat online.") }
            if (agenda == null) {
                item {
                    if (downloading) {
                        EmptyState("Mengunduh daftar peserta…", body = "Setelah terunduh, pemindai bisa dipakai tanpa sinyal.")
                    } else {
                        EmptyState(
                            "Daftar peserta belum diunduh",
                            body = downloadError ?: "Buka pemindai saat online sekali agar daftar tersimpan di perangkat.",
                            action = { PrimaryButton("Unduh daftar", onClick = model::download, enabled = online) },
                        )
                    }
                }
                return@LazyColumn
            }
            if (downloadError != null) item { InfoBanner("${downloadError} Memakai daftar unduhan ${Instant.ofEpochMilli(agenda!!.downloadedAt).formatDateTime()}.", tone = BannerTone.Warning) }
            item { ResultPanel(last) }
            item {
                if (hasCamera) {
                    CameraBox(onCode = { raw -> scope.launch { show(model.scan(raw)) } })
                } else {
                    Column(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Color(0xFF0E2A20)).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Text("Izinkan kamera untuk memindai kartu QR. Tanpa kamera, cari nama di bawah.", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        SecondaryButton("Izinkan kamera", onClick = { permission.launch(Manifest.permission.CAMERA) })
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(recorded.formatId(), style = MaterialTheme.typography.headlineMedium)
                    Text(" / ${roster.size.formatId()} tercatat", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp).weight(1f))
                    val waiting = outbox.count { it.error == null }
                    if (waiting > 0) StatusBadge("$waiting menunggu kirim", GenerusTheme.extra.terlambat, dot = true)
                }
            }
            item {
                // Pemindai keyboard/ketik manual: isi kode kartu "GNR1:…".
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { SearchPill(code, { code = it }, "Ketik kode kartu", onSearch = { scope.launch { show(model.scan(code)); code = "" } }) }
                    SecondaryButton("Catat", onClick = { scope.launch { show(model.scan(code)); code = "" } }, enabled = code.isNotBlank())
                }
            }
            item { SearchPill(query, { query = it }, "Kartu hilang? Cari nama peserta") }
            if (query.trim().length >= 2) {
                val matches = roster.filter { it.name.contains(query.trim(), ignoreCase = true) }.take(8)
                item {
                    ListCard {
                        if (matches.isEmpty()) Text("Tidak ada peserta sasaran yang cocok.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                        matches.forEachIndexed { i, r ->
                            if (i > 0) RowDivider()
                            SearchRow(r, outboxStatus[r.generusId] ?: r.status) {
                                scope.launch { show(model.search(r.generusId)); query = "" }
                            }
                        }
                    }
                }
            }
            if (history.isNotEmpty()) {
                item { Text("Scan terakhir", style = MaterialTheme.typography.titleSmall) }
                items(history.take(10), key = { it.at.toEpochMilli().toString() + it.hashCode() }) { e -> HistoryRow(e) }
            }
        }
    }
}

@Composable
private fun SearchRow(r: RosterItem, status: String?, onClick: () -> Unit) {
    val s = status?.let { AttendanceStatus.valueOf(it) }
    ListRow(
        r.name,
        meta = "${r.kelompok} · Desa ${r.desa}",
        trailing = { if (s != null) StatusBadge(s.label, GenerusTheme.extra.of(s)) },
        onClick = if (s == null) onClick else null,
        chevron = false,
    )
}

/** Panel hasil di atas kamera: hijau = tercatat, kuning = sudah tercatat, merah = ditolak. */
@Composable
private fun ResultPanel(e: ScanEvent?) {
    val x = GenerusTheme.extra
    val (bg, title, sub) = when (val r = e?.result) {
        null -> Triple(MaterialTheme.colorScheme.surfaceVariant, "Arahkan kamera ke kartu QR", "Hasil scan tampil di sini.")
        is ScanResult.Ok -> Triple(x.hadir.solid, r.name, "${r.kelompok} · ${r.status.label}")
        is ScanResult.Duplicate -> Triple(x.terlambat.solid, r.name, "Sudah tercatat: ${r.status.label} · ${r.kelompok}")
        ScanResult.Unknown -> Triple(x.alpa.solid, "Bukan peserta sasaran", "Kartu tidak dikenal, sudah tidak aktif, atau di luar sasaran agenda.")
        ScanResult.Invalid -> Triple(x.alpa.solid, "Bukan kartu Generus", "QR tidak dikenali. Pastikan memindai kartu generus.")
        is ScanResult.Closed -> Triple(x.alpa.solid, "Tidak bisa mencatat", r.reason)
    }
    val color by animateColorAsState(bg, tween(300), label = "scan")
    val fg = if (e == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
    Column(
        Modifier.fillMaxWidth().heightIn(min = 112.dp).clip(RoundedCornerShape(20.dp)).background(color).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = if (e == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.9f))
    }
}

@Composable
private fun HistoryRow(e: ScanEvent) {
    val x = GenerusTheme.extra
    val (name, meta, badge) = when (val r = e.result) {
        is ScanResult.Ok -> Triple(r.name, r.kelompok, r.status.label to x.of(r.status))
        is ScanResult.Duplicate -> Triple(r.name, r.kelompok, "Sudah" to x.terlambat)
        ScanResult.Unknown -> Triple("Tidak dikenal", "Bukan peserta sasaran", "Ditolak" to x.alpa)
        ScanResult.Invalid -> Triple("QR tidak valid", "Bukan kartu Generus", "Ditolak" to x.alpa)
        is ScanResult.Closed -> Triple("Tidak dicatat", r.reason, "Ditolak" to x.alpa)
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text("$meta · ${e.at.formatTime()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusBadge(badge.first, badge.second)
        }
    }
}

/** Kamera 1:1 dengan bingkai bidik; memanggil `onCode` untuk tiap QR (sama tidak diulang < 2,5 dtk). */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraBox(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    val lastCode = remember { arrayOf("", 0L) }
    val currentOnCode by androidx.compose.runtime.rememberUpdatedState(onCode)

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            scanner.close()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(executor) { proxy ->
                        val media = proxy.image
                        if (media == null) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                            .addOnSuccessListener { codes ->
                                val raw = codes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                                val now = System.currentTimeMillis()
                                if (raw == lastCode[0] && now - (lastCode[1] as Long) < 2500) return@addOnSuccessListener
                                lastCode[0] = raw
                                lastCode[1] = now
                                ContextCompat.getMainExecutor(ctx).execute { currentOnCode(raw) }
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                    runCatching {
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                view
            },
        )
        // Bingkai bidik putih radius 16 di tengah.
        Box(Modifier.align(Alignment.Center).fillMaxWidth(0.62f).aspectRatio(1f).border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(16.dp)))
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            IconButton(
                onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.4f)),
            ) {
                Icon(if (torch) Icons.Rounded.FlashlightOff else Icons.Rounded.FlashlightOn, contentDescription = if (torch) "Matikan senter" else "Nyalakan senter", tint = Color.White)
            }
        }
    }
}

/** Bunyi + getar umpan balik scan. */
private class Feedback(context: Context) {
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()
    private val vibrator: Vibrator? = if (android.os.Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun play(r: ScanResult) {
        when (r) {
            is ScanResult.Ok -> {
                tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                vibrate(longArrayOf(0, 60))
            }
            is ScanResult.Duplicate -> {
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
                vibrate(longArrayOf(0, 60, 80, 60))
            }
            else -> {
                tone?.startTone(ToneGenerator.TONE_SUP_ERROR, 300)
                vibrate(longArrayOf(0, 250))
            }
        }
    }

    private fun vibrate(pattern: LongArray) {
        runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    fun release() {
        tone?.release()
    }
}
