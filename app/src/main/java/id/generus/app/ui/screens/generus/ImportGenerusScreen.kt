package id.generus.app.ui.screens.generus

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.Share
import id.generus.app.core.UserError
import id.generus.app.core.Xlsx
import id.generus.app.core.userMessage
import id.generus.app.data.ImportContext
import id.generus.app.data.ImportRow
import id.generus.app.data.ImportStatus
import id.generus.app.data.generusSheet
import id.generus.app.data.validateRows
import id.generus.app.ui.components.AppCard
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.BannerTone
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.InfoBanner
import id.generus.app.ui.components.ListCard
import id.generus.app.ui.components.ListRow
import id.generus.app.ui.components.LoadingBox
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.RefreshableList
import id.generus.app.ui.components.RowDivider
import id.generus.app.ui.components.SecondaryButton
import id.generus.app.ui.components.StatBox
import id.generus.app.ui.components.StatusBadge
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.vm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_FILE_BYTES = 5L * 1024 * 1024

sealed interface ImportStep {
    data object Upload : ImportStep
    data object Working : ImportStep
    data class Preview(val fileName: String, val rows: List<ImportRow>) : ImportStep
    data class Done(val imported: Int, val skipped: Int) : ImportStep
}

class ImportVm(private val c: AppContainer) : ViewModel() {
    private val _step = MutableStateFlow<ImportStep>(ImportStep.Upload)
    val step: StateFlow<ImportStep> = _step.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Langkah 1: baca & periksa file, belum menyimpan apa pun. */
    fun preview(context: android.content.Context, uri: Uri) {
        _error.value = null
        _step.value = ImportStep.Working
        viewModelScope.launch {
            try {
                val (name, size) = withContext(Dispatchers.IO) { fileInfo(context, uri) }
                if (!name.lowercase().endsWith(".xlsx")) throw UserError("Format file harus .xlsx (Excel).")
                if (size > MAX_FILE_BYTES) throw UserError("Ukuran file maksimal 5 MB.")
                val wb = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)!!.use { Xlsx.read(it) } }
                        .getOrElse { throw UserError("File tidak bisa dibaca. Pastikan file Excel (.xlsx) tidak rusak.") }
                }
                val sheet = generusSheet(wb) ?: throw UserError("Sheet \"Generus\" tidak ditemukan. Gunakan template resmi.")
                val ctx = ImportContext(c.generus.scopeUnits("write"), c.agenda.jenjang(), c.generus.existingKeys())
                val result = withContext(Dispatchers.Default) { validateRows(sheet, ctx) }
                if (result.fileError != null) throw UserError(result.fileError)
                if (result.rows.isEmpty()) throw UserError("Tidak ada baris data di sheet Generus (baris contoh dilewati).")
                _step.value = ImportStep.Preview(name, result.rows)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = e.userMessage("Gagal membaca file.")
                _step.value = ImportStep.Upload
            }
        }
    }

    /** Langkah 2: simpan semua baris valid dalam satu transaksi. */
    fun commit() {
        val p = _step.value as? ImportStep.Preview ?: return
        val valid = p.rows.mapNotNull { it.data }
        if (valid.isEmpty()) return
        _step.value = ImportStep.Working
        viewModelScope.launch {
            try {
                c.generus.insertMany(valid.map { it.json() })
                _step.value = ImportStep.Done(valid.size, p.rows.size - valid.size)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = "Import dibatalkan, tidak ada data yang tersimpan. ${e.userMessage()}"
                _step.value = p
            }
        }
    }

    fun reset() {
        _error.value = null
        _step.value = ImportStep.Upload
    }

    private fun fileInfo(context: android.content.Context, uri: Uri): Pair<String, Long> {
        context.contentResolver.query(uri, null, null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                val name = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cur::getString) ?: "file.xlsx"
                val size = cur.getColumnIndex(android.provider.OpenableColumns.SIZE).takeIf { it >= 0 }?.let(cur::getLong) ?: 0L
                return name to size
            }
        }
        return (uri.lastPathSegment ?: "file.xlsx") to 0L
    }
}

/** Import generus dari template Excel (padanan /generus/import): pratinjau dulu, lalu simpan. */
@Composable
fun ImportGenerusScreen(nav: NavHostController) {
    val context = LocalContext.current
    val model = vm { ImportVm(it) }
    val step by model.step.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.preview(context, it) } }
    fun pick() = picker.launch(arrayOf(Share.XLSX, "application/vnd.ms-excel", "application/octet-stream"))

    AppScreen("Import Excel", onBack = { nav.popBackStack() }) { p ->
        RefreshableList(refreshing = false, onRefresh = {}, padding = p) {
            if (error != null) item { InfoBanner(error!!, tone = BannerTone.Error) }
            when (val s = step) {
                ImportStep.Upload -> item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Text("Tambah banyak generus sekaligus", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "1. Unduh template, isi sheet \"Generus\" (satu baris per generus).\n2. Pilih file yang sudah diisi; aplikasi memeriksa setiap baris dulu.\n3. Simpan baris yang valid. Data yang sudah terdaftar dilewati.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SecondaryButton("Unduh template", onClick = { Share.asset(context, "Template_Data_Generus.xlsx", Share.XLSX, "Simpan / buka template") }, icon = Icons.Rounded.Download, modifier = Modifier.fillMaxWidth())
                        PrimaryButton("Pilih file Excel", onClick = ::pick, icon = Icons.Rounded.UploadFile, modifier = Modifier.fillMaxWidth())
                    }
                }
                ImportStep.Working -> item { LoadingBox() }
                is ImportStep.Preview -> {
                    val ok = s.rows.count { it.status == ImportStatus.ok }
                    val err = s.rows.count { it.status == ImportStatus.error }
                    val dup = s.rows.count { it.status == ImportStatus.duplikat }
                    item { Text(s.fileName, style = MaterialTheme.typography.titleSmall) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatBox("Siap disimpan", "$ok", Modifier.weight(1f), GenerusTheme.extra.hadir.solid)
                            StatBox("Bermasalah", "$err", Modifier.weight(1f), if (err > 0) GenerusTheme.extra.alpa.solid else MaterialTheme.colorScheme.onSurface)
                            StatBox("Duplikat", "$dup", Modifier.weight(1f))
                        }
                    }
                    item {
                        if (ok > 0) PrimaryButton("Simpan $ok generus", onClick = model::commit, modifier = Modifier.fillMaxWidth())
                        else InfoBanner("Tidak ada baris valid untuk disimpan. Perbaiki file lalu pilih ulang.", tone = BannerTone.Warning)
                    }
                    item { SecondaryButton("Pilih file lain", onClick = ::pick, modifier = Modifier.fillMaxWidth()) }
                    val problems = s.rows.filter { it.status != ImportStatus.ok }
                    if (problems.isNotEmpty()) {
                        item { Text("Baris bermasalah / dilewati", style = MaterialTheme.typography.titleSmall) }
                        item { RowsCard(problems) }
                    }
                    val sample = s.rows.filter { it.status == ImportStatus.ok }.take(10)
                    if (sample.isNotEmpty()) {
                        item { Text("Contoh baris valid", style = MaterialTheme.typography.titleSmall) }
                        item { RowsCard(sample) }
                    }
                }
                is ImportStep.Done -> item {
                    EmptyState(
                        "${s.imported} generus tersimpan",
                        body = if (s.skipped > 0) "${s.skipped} baris dilewati (bermasalah atau duplikat)." else null,
                        action = { PrimaryButton("Selesai", onClick = { model.reset(); nav.popBackStack() }) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowsCard(rows: List<ImportRow>) {
    val x = GenerusTheme.extra
    ListCard {
        rows.forEachIndexed { i, r ->
            if (i > 0) RowDivider()
            ListRow(
                "Baris ${r.row}: ${r.name.ifBlank { "(tanpa nama)" }}",
                meta = listOfNotNull(r.kelompok.ifBlank { null }, r.jenjang.ifBlank { null }, r.message).joinToString(" · "),
                trailing = {
                    when (r.status) {
                        ImportStatus.ok -> StatusBadge("OK", x.hadir)
                        ImportStatus.error -> StatusBadge("Galat", x.alpa)
                        ImportStatus.duplikat -> StatusBadge("Duplikat", x.terlambat)
                    }
                },
                chevron = false,
            )
        }
    }
}
