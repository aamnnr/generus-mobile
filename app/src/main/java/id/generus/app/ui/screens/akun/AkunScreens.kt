package id.generus.app.ui.screens.akun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import id.generus.app.BuildConfig
import id.generus.app.data.model.canWriteGenerus
import id.generus.app.data.model.initials
import id.generus.app.data.model.isSuperadmin
import id.generus.app.data.model.label
import id.generus.app.ui.appContainer
import id.generus.app.ui.components.AppCard
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.Avatar
import id.generus.app.ui.components.ConfirmSheet
import id.generus.app.ui.components.IconTile
import id.generus.app.ui.components.ListCard
import id.generus.app.ui.components.ListRow
import id.generus.app.ui.components.Overline
import id.generus.app.ui.components.Pill
import id.generus.app.ui.components.SingleChips
import id.generus.app.data.local.ThemeMode
import id.generus.app.ui.components.RowDivider
import id.generus.app.ui.nav.AdminAkun
import id.generus.app.ui.nav.AdminWilayah
import id.generus.app.ui.nav.GantiPassword
import id.generus.app.ui.nav.ImportGenerus
import id.generus.app.ui.nav.Kartu
import id.generus.app.ui.nav.Kenaikan
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.nav.bottomTabs
import id.generus.app.ui.nav.mainItems
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class MenuItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val meta: String? = null, val onClick: () -> Unit)

/** Lainnya (DESIGN.md §6.3): menu yang tidak muat di tab bawah, akun, dan keluar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LainnyaScreen(nav: NavHostController) {
    val me = LocalMe.current
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val pending by c.presensi.pendingCount.collectAsStateWithLifecycle(0)
    var confirmLogout by remember { mutableStateOf<Int?>(null) }

    val inTabs = bottomTabs(me).map { it.route::class }.toSet()
    val more = mainItems(me).filter { it.route::class !in inTabs }.map { MenuItem(it.label, it.icon) { nav.navigate(it.route) } }
    val generusTools = buildList {
        if (me.canWriteGenerus) {
            add(MenuItem("Kartu QR", Icons.Rounded.Badge, "Cetak / bagikan kartu generus") { nav.navigate(Kartu()) })
            add(MenuItem("Kenaikan jenjang", Icons.Rounded.TrendingUp, "Naikkan jenjang/kategori secara massal") { nav.navigate(Kenaikan) })
            add(MenuItem("Import Excel", Icons.Rounded.UploadFile, "Tambah generus dari template Excel") { nav.navigate(ImportGenerus) })
        }
    }
    val admin = buildList {
        if (me.isSuperadmin) {
            add(MenuItem("Wilayah", Icons.Rounded.AccountTree, "Daerah, desa, kelompok") { nav.navigate(AdminWilayah) })
            add(MenuItem("Akun & peran", Icons.Rounded.ManageAccounts) { nav.navigate(AdminAkun) })
        }
    }

    fun signOut() {
        scope.launch {
            c.auth.signOut()
            c.presensi.clearAll()
            c.cache.clear()
            c.session.onSignedOut()
        }
    }

    AppScreen("Lainnya") { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(initials(me.name), size = 52.dp)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(me.name, style = MaterialTheme.typography.titleMedium)
                            if (me.email != null) Text(me.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        me.roles.forEach { Pill("${it.label()} · ${it.unitName}") }
                    }
                }
            }
            if (pending > 0) {
                item {
                    ListCard {
                        ListRow(
                            "$pending presensi menunggu dikirim",
                            meta = "Akan dikirim otomatis saat ada sinyal. Ketuk untuk kirim sekarang.",
                            leading = { IconTile(Icons.Rounded.CloudUpload, size = 40.dp) },
                            onClick = { c.presensi.requestSync(0) },
                            chevron = false,
                        )
                    }
                }
            }
            menuSection("Menu", more)
            menuSection("Generus", generusTools)
            menuSection("Admin", admin)
            item {
                val mode by c.theme.mode.collectAsStateWithLifecycle()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Overline("Tampilan", Modifier.padding(horizontal = 4.dp))
                    AppCard {
                        SingleChips(ThemeMode.entries, mode, { it.label }, { c.theme.set(it) })
                    }
                }
            }
            menuSection(
                "Akun",
                listOf(
                    MenuItem("Ganti password", Icons.Rounded.Key) { nav.navigate(GantiPassword) },
                    MenuItem("Keluar", Icons.AutoMirrored.Rounded.Logout) {
                        scope.launch {
                            val n = c.presensi.pendingCount.first()
                            confirmLogout = n
                        }
                    },
                ),
            )
            item {
                Text(
                    "Generus ${BuildConfig.VERSION_NAME} · server ${if (BuildConfig.FEATURE_KURIKULUM) "dengan modul kurikulum" else "Tahap 1"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    confirmLogout?.let { n ->
        ConfirmSheet(
            title = "Keluar dari akun?",
            body = if (n > 0) "$n presensi belum terkirim dan akan terhapus dari perangkat ini bila Anda keluar sekarang. Sebaiknya kirim dulu saat ada sinyal."
            else "Daftar peserta pemindai yang diunduh di perangkat ini juga akan dihapus.",
            confirmText = "Keluar",
            danger = n > 0,
            onConfirm = { confirmLogout = null; signOut() },
            onDismiss = { confirmLogout = null },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.menuSection(title: String, items: List<MenuItem>) {
    if (items.isEmpty()) return
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline(title, Modifier.padding(horizontal = 4.dp))
            ListCard {
                items.forEachIndexed { i, m ->
                    if (i > 0) RowDivider()
                    ListRow(m.label, meta = m.meta, leading = { IconTile(m.icon, size = 40.dp) }, onClick = m.onClick)
                }
            }
        }
    }
}
