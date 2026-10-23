package id.generus.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Penampil toast (snackbar) global. Pakai `LocalToast.current("Presensi tersimpan.")`. */
class Toaster(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    operator fun invoke(message: String) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(message)
        }
    }
}

val LocalToast = compositionLocalOf<Toaster> { error("Toaster belum dipasang") }

/** True bila tab bawah tampil: layar tidak perlu menambah inset navigasi sistem lagi. */
val LocalHasBottomBar = compositionLocalOf { false }

@Composable
fun ProvideToaster(host: SnackbarHostState, scope: CoroutineScope, content: @Composable () -> Unit) {
    val toaster = remember(host, scope) { Toaster(host, scope) }
    CompositionLocalProvider(LocalToast provides toaster, content = content)
}

/**
 * Kerangka layar dengan top bar ringkas. `onBack` null = layar tingkat atas.
 * `bottomBar` untuk tombol utama yang lengket di bawah.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (LocalHasBottomBar.current) {
            ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        content = content,
    )
}

/** Daftar bergulir dengan tarik-untuk-muat-ulang dan padding halaman standar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableList(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    spacing: Int = 12,
    content: LazyListScope.() -> Unit,
) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.dp),
            content = content,
        )
    }
}

/** Tombol utama lengket di bawah layar form. */
@Composable
fun StickyBottom(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Konfirmasi tindakan dalam bottom sheet (DESIGN.md §6.4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    extra: @Composable () -> Unit = {},
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            extra()
            Spacer(Modifier.height(4.dp))
            if (danger) {
                DangerButton(confirmText, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton(confirmText, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
            }
            GhostButton("Batal", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Sheet bawah umum untuk form kecil / filter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Dialog satu kali untuk informasi penting (mis. password sementara). */
@Composable
fun InfoDialog(title: String, body: String, onDismiss: () -> Unit, extra: @Composable () -> Unit = {}) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body)
                extra()
            }
        },
    )
}

/** Konten yang memuat data: kerangka saat memuat, pesan + coba lagi saat gagal. */
@Composable
fun <T> LoadableContent(state: Loadable<T>, onRetry: () -> Unit, modifier: Modifier = Modifier, loading: @Composable () -> Unit = { LoadingBox() }, content: @Composable (T) -> Unit) {
    // Kerangka -> isi berganti dengan crossfade halus.
    val phase = when {
        state.data != null -> 2
        state.error != null -> 1
        else -> 0
    }
    androidx.compose.animation.Crossfade(phase, modifier.fillMaxSize(), androidx.compose.animation.core.tween(DUR_ENTER), label = "loadable") { p ->
        Box(Modifier.fillMaxSize()) {
            when (p) {
                2 -> state.data?.let { content(it) }
                1 -> ErrorBox(state.error ?: "", onRetry)
                else -> Box(Modifier.padding(horizontal = 16.dp)) { loading() }
            }
        }
    }
}

/** Status data yang dimuat: data terakhir tetap tampil saat memuat ulang. */
data class Loadable<T>(val data: T? = null, val loading: Boolean = true, val error: String? = null)

@Composable
fun rememberFlag(initial: Boolean = false) = remember { mutableStateOf(initial) }
