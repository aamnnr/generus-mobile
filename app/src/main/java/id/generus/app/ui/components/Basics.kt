package id.generus.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.generus.app.ui.theme.GenerusTheme
import id.generus.app.ui.theme.Palette
import id.generus.app.ui.theme.StatusColor

/** Jarak tepi halaman (DESIGN.md §4): 16 dp di HP. */
val PagePadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)

/** Kartu putih radius 16 dengan bayangan sangat lembut. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.contentColorFor(color).takeOrElse { MaterialTheme.colorScheme.onSurface }) {
        Column(
            modifier
                .appear()
                .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
                .shadow(2.dp, shape, ambientColor = Palette.Green950.copy(alpha = 0.06f), spotColor = Palette.Green950.copy(alpha = 0.08f))
                .clip(shape)
                .background(color)
                .then(if (onClick != null) Modifier.clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onClick) else Modifier)
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Kartu bertajuk seksi: judul h2 + tautan opsional di kanan. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            action?.invoke()
        }
        content()
    }
}

/** Overline: "AGENDA BERIKUTNYA". */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

/** Avatar inisial (tanpa foto generus/akun). */
@Composable
fun Avatar(initials: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Ubin ikon: kotak pasir radius 14 dengan ikon hijau. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 48.dp, active: Boolean = false) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) MaterialTheme.colorScheme.secondaryContainer else GenerusTheme.extra.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size / 2))
    }
}

/** Badge status: pil 24 dp, latar lembut + teks warna status. */
@Composable
fun StatusBadge(text: String, color: StatusColor, modifier: Modifier = Modifier, dot: Boolean = false) {
    Row(
        modifier.heightIn(min = 24.dp).clip(CircleShape).background(color.soft).padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(color.solid))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color.solid, maxLines = 1)
    }
}

/** Pil netral (chip peran, keterangan kecil). */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.secondaryContainer, content: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = modifier.clip(CircleShape).background(container).padding(horizontal = 12.dp, vertical = 4.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Bilah progres tipis (green-600 di atas green-200). fraction 0–1. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp, color: Color = GenerusTheme.extra.bar, track: Color = GenerusTheme.extra.track) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600, easing = EaseOutSoft), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(animated).height(height).clip(CircleShape).background(color))
    }
}

/** Kolom cari berbentuk pil, tinggi 48. */
@Composable
fun SearchPill(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onSearch: (() -> Unit)? = null) {
    val shape = CircleShape
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian") }
        }
    }
}

/** Chip filter yang bisa digeser horizontal. Aktif = hijau penuh, teks putih. */
@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt -> Chip(label(opt), opt == selected, onClick = { onSelect(opt) }) }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else GenerusTheme.extra.tile, tween(DUR_COLOR), label = "chip-bg")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, tween(DUR_COLOR), label = "chip-fg")
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction)
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(interaction, LocalIndication.current, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** Item daftar: (avatar/ubin) · judul tebal + meta · trailing opsional · chevron. Seluruh baris bisa diketuk. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta != null) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
        if (chevron && onClick != null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Kelompok baris dalam satu kartu dengan pemisah tipis. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier
                .appear()
                .fillMaxWidth()
                .shadow(2.dp, shape, ambientColor = Palette.Green950.copy(alpha = 0.06f), spotColor = Palette.Green950.copy(alpha = 0.08f))
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .animateContentSize(tween(DUR_ENTER, easing = EaseOutSoft)),
            content = content,
        )
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(MaterialTheme.colorScheme.outline))
}

/** Empty state: siluet masjid kuningan, judul, satu kalimat, satu tombol. */
@Composable
fun EmptyState(title: String, modifier: Modifier = Modifier, body: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MosqueSilhouette(GenerusTheme.extra.brass.copy(alpha = 0.4f), Modifier.width(96.dp).height(58.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    // Kerangka berbentuk kartu, bukan spinner (DESIGN.md §9).
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBlock(height = 96.dp)
        repeat(4) { SkeletonRow() }
    }
}

/** Kerangka abu-abu berdenyut pengganti konten yang sedang dimuat. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 64.dp, radius: Dp = 16.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(radius)).shimmer())
}

/** Kerangka satu baris daftar: lingkaran + dua garis teks. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).shimmer())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(7.dp)).shimmer())
            Box(Modifier.fillMaxWidth(0.45f).height(11.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    EmptyState(
        title = "Belum bisa dimuat",
        body = message,
        modifier = modifier,
        action = onRetry?.let { { SecondaryButton("Coba lagi", onClick = it) } },
    )
}

/** Banner lembut di atas konten (info/peringatan). */
@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, tone: BannerTone = BannerTone.Info, icon: ImageVector? = null, action: (@Composable RowScope.() -> Unit)? = null) {
    val (bg, fg) = when (tone) {
        BannerTone.Info -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        BannerTone.Warning -> GenerusTheme.extra.brassSoft to GenerusTheme.extra.brassText
        BannerTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        BannerTone.Success -> GenerusTheme.extra.hadir.soft to GenerusTheme.extra.hadir.solid
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = fg, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

enum class BannerTone { Info, Warning, Error, Success }

/** Strip "offline" lembut (kuningan) di atas layar. */
@Composable
fun OfflineStrip(text: String = "Anda sedang offline. Presensi disimpan di perangkat dan dikirim saat online.") {
    Surface(color = GenerusTheme.extra.brassSoft) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = GenerusTheme.extra.brassText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = GenerusTheme.extra.brassText)
        }
    }
}

/** Kotak statistik: judul kecil + angka besar (Lora). */
@Composable
fun StatBox(title: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    AppCard(modifier, padding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Pasangan label–nilai dalam satu baris (definisi). */
@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor, textAlign = TextAlign.End, modifier = Modifier.weight(1.3f))
    }
}
