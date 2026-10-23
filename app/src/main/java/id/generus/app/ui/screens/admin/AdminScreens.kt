package id.generus.app.ui.screens.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import id.generus.app.AppContainer
import id.generus.app.core.UserError
import id.generus.app.core.formatId
import id.generus.app.data.model.Account
import id.generus.app.data.model.AppRole
import id.generus.app.data.model.UnitLevel
import id.generus.app.data.model.WilayahUnit
import id.generus.app.data.model.initials
import id.generus.app.data.model.label
import id.generus.app.ui.LoadVm
import id.generus.app.ui.OnResult
import id.generus.app.ui.components.AppCard
import id.generus.app.ui.components.AppScreen
import id.generus.app.ui.components.Avatar
import id.generus.app.ui.components.BannerTone
import id.generus.app.ui.components.BottomSheet
import id.generus.app.ui.components.ConfirmSheet
import id.generus.app.ui.components.EmptyState
import id.generus.app.ui.components.ErrorBox
import id.generus.app.ui.components.FormColumn
import id.generus.app.ui.components.InfoBanner
import id.generus.app.ui.components.InfoDialog
import id.generus.app.ui.components.ListCard
import id.generus.app.ui.components.ListRow
import id.generus.app.ui.components.LoadableContent
import id.generus.app.ui.components.LoadingBox
import id.generus.app.ui.components.LocalToast
import id.generus.app.ui.components.Pill
import id.generus.app.ui.components.PrimaryButton
import id.generus.app.ui.components.RefreshableList
import id.generus.app.ui.components.RowDivider
import id.generus.app.ui.components.SearchPill
import id.generus.app.ui.components.SecondaryButton
import id.generus.app.ui.components.SectionCard
import id.generus.app.ui.components.SelectField
import id.generus.app.ui.components.StatusBadge
import id.generus.app.ui.components.StickyBottom
import id.generus.app.ui.components.TextInput
import id.generus.app.ui.nav.AkunBaru
import id.generus.app.ui.nav.AkunDetail
import id.generus.app.ui.nav.LocalMe
import id.generus.app.ui.popWithResult
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.theme.StatusColor
import id.generus.app.ui.vm
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Wilayah
// ---------------------------------------------------------------------------

class WilayahVm(c: AppContainer) : LoadVm<List<WilayahUnit>>(c) {
    init { load() }

    override suspend fun fetch() = c.admin.wilayah()

    fun add(parentId: String, level: UnitLevel, name: String, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) {
        validName(name)
        c.admin.addUnit(parentId, level, name.trim())
    }

    fun rename(id: String, name: String, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) {
        validName(name)
        c.admin.renameUnit(id, name.trim())
    }

    fun delete(id: String, onError: (String) -> Unit, onOk: () -> Unit) = run({ e ->
        onError(if (e.startsWith("Data masih dipakai")) "Tidak bisa dihapus: masih ada kelompok, generus, atau riwayat di dalamnya." else e)
    }, { load(); onOk() }) { c.admin.deleteUnit(id) }

    private fun validName(name: String) {
        if (name.trim().length < 2) throw UserError("Nama minimal 2 huruf")
        if (name.trim().length > 80) throw UserError("Nama terlalu panjang")
    }
}

private sealed interface WilayahSheet {
    data class Add(val parent: WilayahUnit, val level: UnitLevel) : WilayahSheet
    data class Rename(val unit: WilayahUnit) : WilayahSheet
    data class Delete(val unit: WilayahUnit) : WilayahSheet
}

/** Pohon wilayah daerah → desa → kelompok (padanan /admin/wilayah, superadmin). */
@Composable
fun AdminWilayahScreen(nav: NavHostController) {
    val toast = LocalToast.current
    val model = vm { WilayahVm(it) }
    val state by model.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var sheet by remember { mutableStateOf<WilayahSheet?>(null) }

    AppScreen("Wilayah", onBack = { nav.popBackStack() }) { p ->
        RefreshableList(refreshing = state.loading && state.data != null, onRefresh = model::load, padding = p) {
            val all = state.data
            when {
                all == null && state.error != null -> item { ErrorBox(state.error!!, model::load) }
                all == null -> item { LoadingBox() }
                else -> {
                    val daerah = all.firstOrNull { it.level == UnitLevel.daerah }
                    if (daerah == null) {
                        item { EmptyState("Belum ada daerah", body = "Daerah dibuat saat penyiapan server (pnpm resmi:setup).") }
                        return@RefreshableList
                    }
                    val desa = all.filter { it.parentId == daerah.id }.sortedBy { it.name }
                    val count = { u: WilayahUnit -> if (u.level == UnitLevel.kelompok) u.generusAktif else all.filter { it.parentId == u.id }.sumOf { it.generusAktif } }
                    item {
                        AppCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Daerah ${daerah.name}", style = MaterialTheme.typography.titleMedium)
                                    Text("${desa.size} desa · ${desa.sumOf(count).formatId()} generus aktif", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { sheet = WilayahSheet.Rename(daerah) }) { Icon(Icons.Rounded.Edit, contentDescription = "Ubah nama daerah") }
                            }
                            SecondaryButton("Tambah desa", onClick = { sheet = WilayahSheet.Add(daerah, UnitLevel.desa) }, icon = Icons.Rounded.Add)
                        }
                    }
                    desa.forEach { ds ->
                        val open = ds.id in expanded
                        val kelompok = all.filter { it.parentId == ds.id }.sortedBy { it.name }
                        item(key = ds.id) {
                            ListCard {
                                ListRow(
                                    "Desa ${ds.name}",
                                    meta = "${kelompok.size} kelompok · ${count(ds).formatId()} generus aktif",
                                    trailing = { Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null) },
                                    chevron = false,
                                    onClick = { expanded = if (open) expanded - ds.id else expanded + ds.id },
                                )
                                if (open) {
                                    kelompok.forEach { k ->
                                        RowDivider()
                                        ListRow(
                                            "Kelompok ${k.name}",
                                            meta = "${k.generusAktif.formatId()} generus aktif",
                                            trailing = {
                                                Row {
                                                    IconButton(onClick = { sheet = WilayahSheet.Rename(k) }) { Icon(Icons.Rounded.Edit, contentDescription = "Ubah nama ${k.name}") }
                                                    IconButton(onClick = { sheet = WilayahSheet.Delete(k) }) { Icon(Icons.Rounded.Delete, contentDescription = "Hapus ${k.name}", tint = MaterialTheme.colorScheme.error) }
                                                }
                                            },
                                            chevron = false,
                                        )
                                    }
                                    RowDivider()
                                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SecondaryButton("Tambah kelompok", onClick = { sheet = WilayahSheet.Add(ds, UnitLevel.kelompok) }, icon = Icons.Rounded.Add, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { sheet = WilayahSheet.Rename(ds) }) { Icon(Icons.Rounded.Edit, contentDescription = "Ubah nama desa") }
                                        IconButton(onClick = { sheet = WilayahSheet.Delete(ds) }) { Icon(Icons.Rounded.Delete, contentDescription = "Hapus desa", tint = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (val s = sheet) {
        is WilayahSheet.Add -> NameSheet(if (s.level == UnitLevel.desa) "Tambah desa" else "Tambah kelompok di Desa ${s.parent.name}", "", onDismiss = { sheet = null }) { name ->
            model.add(s.parent.id, s.level, name, { toast(it) }) { sheet = null; toast("${s.level.label} $name ditambahkan.") }
        }
        is WilayahSheet.Rename -> NameSheet("Ubah nama ${s.unit.level.label.lowercase()}", s.unit.name, onDismiss = { sheet = null }) { name ->
            model.rename(s.unit.id, name, { toast(it) }) { sheet = null; toast("Nama disimpan.") }
        }
        is WilayahSheet.Delete -> ConfirmSheet(
            "Hapus ${s.unit.level.label} ${s.unit.name}?",
            "Hanya bisa bila tidak ada kelompok, generus, atau riwayat di dalamnya.",
            "Hapus",
            danger = true,
            onConfirm = { model.delete(s.unit.id, { toast(it); sheet = null }) { sheet = null; toast("Dihapus.") } },
            onDismiss = { sheet = null },
        )
        null -> {}
    }
}

@Composable
private fun NameSheet(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    BottomSheet(title, onDismiss) {
        TextInput("Nama", name, { name = it })
        PrimaryButton("Simpan", onClick = { onSave(name) }, modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------------------
// Akun
// ---------------------------------------------------------------------------

class AkunListVm(c: AppContainer) : LoadVm<List<Account>>(c) {
    val q = MutableStateFlow("")
    private var job: Job? = null

    init { load() }

    override suspend fun fetch() = c.admin.accounts(q.value)

    fun search(text: String) {
        q.value = text
        job?.cancel()
        job = viewModelScope.launch { delay(350); load() }
    }
}

/** Daftar akun & peran (padanan /admin/akun, superadmin). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminAkunScreen(nav: NavHostController) {
    val model = vm { AkunListVm(it) }
    val state by model.state.collectAsStateWithLifecycle()
    val q by model.q.collectAsStateWithLifecycle()
    OnResult(nav) { model.load() }

    AppScreen(
        "Akun & peran",
        onBack = { nav.popBackStack() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(AkunBaru) },
                icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                text = { Text("Akun baru") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { p ->
        RefreshableList(refreshing = state.loading && state.data != null, onRefresh = model::load, padding = p) {
            item { SearchPill(q, model::search, "Cari nama atau email") }
            val list = state.data
            when {
                list == null && state.error != null -> item { ErrorBox(state.error!!, model::load) }
                list == null -> item { LoadingBox() }
                list.isEmpty() -> item { EmptyState("Tidak ada akun yang cocok") }
                else -> item {
                    ListCard {
                        list.forEachIndexed { i, a ->
                            if (i > 0) RowDivider()
                            ListRow(
                                a.name,
                                meta = listOfNotNull(a.email, a.roles.joinToString(", ") { "${it.label()} · ${it.unitName}" }.ifEmpty { "Belum punya peran" }).joinToString("\n"),
                                leading = { Avatar(initials(a.name), size = 40.dp) },
                                trailing = {
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (!a.active) StatusBadge("Nonaktif", GenerusTheme.extra.alpa)
                                        if (a.mustChangePassword) StatusBadge("Password sementara", StatusColor(GenerusTheme.extra.brassText, GenerusTheme.extra.brassSoft))
                                    }
                                },
                                onClick = { nav.navigate(AkunDetail(a.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

data class AkunDetailData(val account: Account, val units: List<WilayahUnit>)

class AkunDetailVm(c: AppContainer, private val id: String) : LoadVm<AkunDetailData>(c) {
    init { load() }

    override suspend fun fetch() = AkunDetailData(c.admin.account(id) ?: throw UserError("Akun tidak ditemukan."), c.admin.wilayah())

    fun updateProfile(name: String, phone: String, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) {
        if (name.trim().length < 2) throw UserError("Nama minimal 2 huruf")
        c.admin.updateProfile(id, name.trim(), phone.trim().ifEmpty { null })
    }

    fun addRole(role: AppRole, unitId: String, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) { c.admin.addRole(id, unitId, role) }

    fun removeRole(roleId: String, onError: (String) -> Unit) = run(onError, { load() }) { c.admin.removeRole(roleId, id) }

    fun setActive(active: Boolean, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, { load(); onOk() }) { c.admin.setActive(id, active) }

    fun resetPassword(onError: (String) -> Unit, onOk: (String) -> Unit) = run(onError) {
        onOk(c.admin.resetPassword(id).tempPassword ?: throw UserError("Gagal mereset password."))
    }
}

/** Satu unit untuk pilihan peran, berlabel tingkat & induk. */
private fun unitOptions(units: List<WilayahUnit>): List<WilayahUnit> {
    val daerah = units.filter { it.level == UnitLevel.daerah }
    val desa = units.filter { it.level == UnitLevel.desa }.sortedBy { it.name }
    return daerah + desa.flatMap { d -> listOf(d) + units.filter { it.parentId == d.id }.sortedBy { it.name } }
}

private fun unitGroup(units: List<WilayahUnit>, u: WilayahUnit): String = when (u.level) {
    UnitLevel.daerah -> "Daerah"
    UnitLevel.desa -> "Desa ${u.name}"
    UnitLevel.kelompok -> "Desa ${units.firstOrNull { it.id == u.parentId }?.name ?: ""}"
}

@Composable
private fun RoleFields(units: List<WilayahUnit>, role: AppRole?, unit: WilayahUnit?, onRole: (AppRole) -> Unit, onUnit: (WilayahUnit) -> Unit) {
    val options = when (role) {
        // Tim Kurikulum hanya di tingkat daerah (PRD §3).
        AppRole.kurikulum, AppRole.superadmin -> units.filter { it.level == UnitLevel.daerah }
        else -> unitOptions(units)
    }
    SelectField("Peran", AppRole.entries, role, { it.label }, onRole)
    SelectField("Unit", options, unit, { "${it.level.label} ${it.name}" }, onUnit, group = { unitGroup(units, it) })
}

@Composable
private fun TempPasswordDialog(title: String, password: String, email: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val toast = LocalToast.current
    InfoDialog(title, "Password sementara (ditampilkan sekali). Teruskan ke pemilik akun; ia wajib menggantinya saat login pertama.", onDismiss) {
        Text(password, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 26.sp))
        if (email != null) Text("Email: $email", style = MaterialTheme.typography.bodyMedium)
        SecondaryButton("Salin", onClick = {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Password sementara", if (email != null) "Email: $email\nPassword sementara: $password" else password))
            toast("Disalin.")
        })
    }
}

/** Detail akun: profil, peran, status aktif, reset password (padanan /admin/akun/[id]). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AkunDetailScreen(nav: NavHostController, id: String) {
    val me = LocalMe.current
    val toast = LocalToast.current
    val model = vm(key = "akun-$id") { AkunDetailVm(it, id) }
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var temp by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var addRole by remember { mutableStateOf(false) }

    AppScreen(state.data?.account?.name ?: "Akun", onBack = { nav.popWithResult() }) { p ->
        LoadableContent(state, model::load, Modifier.padding(p)) { d ->
            val a = d.account
            val self = a.id == me.id
            var name by remember(a) { mutableStateOf(a.name) }
            var phone by remember(a) { mutableStateOf(a.phone ?: "") }
            FormColumn(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(initials(a.name), size = 52.dp)
                    Column(Modifier.weight(1f)) {
                        Text(a.email ?: "—", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusBadge(if (a.active) "Aktif" else "Nonaktif", if (a.active) GenerusTheme.extra.hadir else GenerusTheme.extra.alpa)
                            if (a.mustChangePassword) StatusBadge("Password sementara", StatusColor(GenerusTheme.extra.brassText, GenerusTheme.extra.brassSoft))
                        }
                    }
                }
                SectionCard("Profil") {
                    TextInput("Nama", name, { name = it })
                    TextInput("No. HP (opsional)", phone, { phone = it }, keyboardType = KeyboardType.Phone)
                    PrimaryButton("Simpan profil", onClick = { model.updateProfile(name, phone, { toast(it) }) { toast("Profil disimpan.") } }, loading = busy)
                }
                SectionCard("Peran") {
                    if (a.roles.isEmpty()) Text("Belum punya peran.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    a.roles.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill("${r.label()} · ${r.unitName}", Modifier.weight(1f, fill = false))
                            Column(Modifier.weight(1f)) {}
                            // Cegah superadmin mengunci dirinya sendiri.
                            if (!(self && r.role == AppRole.superadmin)) {
                                IconButton(onClick = { model.removeRole(r.id) { toast(it) } }) { Icon(Icons.Rounded.Delete, contentDescription = "Cabut peran ${r.label()}", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                    SecondaryButton("Tambah peran", onClick = { addRole = true }, icon = Icons.Rounded.Add)
                }
                if (!self) {
                    SectionCard("Keamanan") {
                        SecondaryButton("Reset password", onClick = { confirm = "reset" }, modifier = Modifier.fillMaxWidth())
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text(
                            if (a.active) "Akun nonaktif tidak bisa login dan tidak melihat data apa pun." else "Akun ini tidak bisa login sampai diaktifkan kembali.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (a.active) SecondaryButton("Nonaktifkan akun", onClick = { confirm = "deactivate" }, modifier = Modifier.fillMaxWidth())
                        else PrimaryButton("Aktifkan kembali", onClick = { model.setActive(true, { toast(it) }) { toast("Akun diaktifkan.") } }, loading = busy, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (addRole) {
                var role by remember { mutableStateOf<AppRole?>(null) }
                var unit by remember { mutableStateOf<WilayahUnit?>(null) }
                BottomSheet("Tambah peran", { addRole = false }) {
                    RoleFields(d.units, role, unit, { role = it; unit = null }, { unit = it })
                    PrimaryButton("Tambah", onClick = {
                        val r = role ?: return@PrimaryButton toast("Pilih peran")
                        val u = unit ?: return@PrimaryButton toast("Pilih unit")
                        model.addRole(r, u.id, { toast(it) }) { addRole = false; toast("Peran ditambahkan.") }
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
            when (confirm) {
                "reset" -> ConfirmSheet(
                    "Reset password ${a.name}?",
                    "Password lama tidak berlaku lagi. Password sementara baru ditampilkan sekali.",
                    "Reset password",
                    onConfirm = { confirm = null; model.resetPassword({ toast(it) }) { temp = it } },
                    onDismiss = { confirm = null },
                )
                "deactivate" -> ConfirmSheet(
                    "Nonaktifkan ${a.name}?",
                    "Akun tidak bisa login dan tidak melihat data apa pun sampai diaktifkan kembali.",
                    "Nonaktifkan",
                    danger = true,
                    onConfirm = { confirm = null; model.setActive(false, { toast(it) }) { toast("Akun dinonaktifkan.") } },
                    onDismiss = { confirm = null },
                )
            }
            temp?.let { TempPasswordDialog("Password direset", it, a.email) { temp = null } }
        }
    }
}

class AkunBaruVm(c: AppContainer) : LoadVm<List<WilayahUnit>>(c) {
    init { load() }

    override suspend fun fetch() = c.admin.wilayah()

    fun create(name: String, email: String, phone: String, role: AppRole?, unitId: String?, onError: (String) -> Unit, onOk: (String, String) -> Unit) = run(onError) {
        when {
            name.trim().length < 2 -> throw UserError("Nama minimal 2 huruf")
            !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim()) -> throw UserError("Email tidak valid")
            role == null -> throw UserError("Pilih peran")
            unitId == null -> throw UserError("Pilih unit")
        }
        val res = c.admin.createAccount(name.trim(), email.trim().lowercase(), phone.trim().ifEmpty { null }, role!!, unitId!!)
        onOk(res.userId ?: "", res.tempPassword ?: "")
    }
}

/** Buat akun dengan password sementara (padanan /admin/akun/baru). */
@Composable
fun AkunBaruScreen(nav: NavHostController) {
    val model = vm { AkunBaruVm(it) }
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var created by remember { mutableStateOf<Pair<String, String>?>(null) }

    AppScreen("Akun baru", onBack = { nav.popBackStack() }) { p ->
        LoadableContent(state, model::load, Modifier.padding(p)) { units ->
            var name by remember { mutableStateOf("") }
            var email by remember { mutableStateOf("") }
            var phone by remember { mutableStateOf("") }
            var role by remember { mutableStateOf<AppRole?>(null) }
            var unit by remember { mutableStateOf<WilayahUnit?>(null) }
            var error by remember { mutableStateOf<String?>(null) }
            Column(Modifier.imePadding()) {
                FormColumn(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    InfoBanner("Akun dibuat dengan password sementara yang wajib diganti saat login pertama. Tidak ada pendaftaran mandiri.")
                    if (error != null) InfoBanner(error!!, tone = BannerTone.Error)
                    TextInput("Nama", name, { name = it })
                    TextInput("Email", email, { email = it }, keyboardType = KeyboardType.Email)
                    TextInput("No. HP (opsional)", phone, { phone = it }, keyboardType = KeyboardType.Phone)
                    RoleFields(units, role, unit, { role = it; unit = null }, { unit = it })
                }
                StickyBottom {
                    PrimaryButton("Buat akun", onClick = {
                        error = null
                        model.create(name, email, phone, role, unit?.id, { error = it }) { _, pw -> created = email.trim().lowercase() to pw }
                    }, loading = busy, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    created?.let { (email, pw) -> TempPasswordDialog("Akun dibuat", pw, email) { created = null; nav.popWithResult() } }
}
