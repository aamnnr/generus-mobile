package id.generus.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.generus.app.data.model.TreeUnit
import id.generus.app.data.model.UnitLevel
import id.generus.app.data.model.label
import java.text.Normalizer

// Pemilih wilayah berjenjang: daerah → desa → kelompok, tiap tingkat menjorok ke
// kanan dan bisa dibuka-tutup; ketik untuk langsung mencari unit tertentu.
// Padanan src/components/unit-picker.tsx di PWA.

private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").trim()

private data class PickerRow(val unit: TreeUnit, val depth: Int, val hasChildren: Boolean)

/**
 * `value` = id unit terpilih; null = kosong. Dengan `emptyLabel`, pilihan kosong
 * (mis. "Semua wilayah") tampil paling atas.
 */
@Composable
fun UnitPickerField(
    label: String,
    units: List<TreeUnit>,
    value: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Pilih wilayah",
    emptyLabel: String? = null,
    error: String? = null,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val byId = remember(units) { units.associateBy { it.id } }
    val selected = value?.let(byId::get)
    val parent = selected?.parentId?.let(byId::get)
    val text = selected?.let { "${it.level.label} ${it.name}" + (parent?.let { p -> " · ${p.level.label} ${p.name}" } ?: "") } ?: (emptyLabel ?: "")

    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { Icon(Icons.Rounded.UnfoldMore, contentDescription = null) },
            supportingText = (error ?: supporting)?.let { { Text(it) } },
            isError = error != null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier.matchParentSize().padding(bottom = if (error != null || supporting != null) 20.dp else 0.dp)
                .clickable(enabled = enabled, role = Role.Button) { open = true },
        )
    }
    if (open) {
        UnitPickerSheet(units, value, emptyLabel, onDismiss = { open = false }) {
            onSelect(it)
            open = false
        }
    }
}

@Composable
private fun UnitPickerSheet(units: List<TreeUnit>, value: String?, emptyLabel: String?, onDismiss: () -> Unit, onChoose: (String?) -> Unit) {
    val byId = remember(units) { units.associateBy { it.id } }
    val children = remember(units) { units.groupBy { u -> u.parentId?.takeIf(byId::containsKey) } }
    var query by remember { mutableStateOf("") }
    // Mulai dari tingkat atas yang tertutup; buka jalur ke unit terpilih (dan daerah bila hanya satu).
    var expanded by remember {
        val exp = HashSet<String>()
        var p = value?.let(byId::get)?.parentId
        while (p != null && byId.containsKey(p)) { exp += p; p = byId[p]?.parentId }
        children[null]?.singleOrNull()?.let { exp += it.id }
        mutableStateOf<Set<String>>(exp)
    }

    val q = norm(query)
    // Saat mencari: tampilkan unit yang cocok beserta leluhurnya, semua terbuka.
    val matches: Set<String>? = if (q.isEmpty()) null else buildSet {
        for (u in units) {
            if (norm("${u.level.label} ${u.name}").contains(q) || norm(u.name).contains(q)) {
                var x: TreeUnit? = u
                while (x != null && add(x.id)) x = x.parentId?.let(byId::get)
            }
        }
    }
    val rows = buildList {
        fun walk(parent: String?, depth: Int) {
            for (u in children[parent].orEmpty()) {
                if (matches != null && u.id !in matches) continue
                val has = children[u.id].orEmpty().isNotEmpty()
                add(PickerRow(u, depth, has))
                if (has && (matches != null || u.id in expanded)) walk(u.id, depth + 1)
            }
        }
        walk(null, 0)
    }

    BottomSheet("Pilih wilayah", onDismiss) {
        SearchPill(query, { query = it }, "Ketik nama daerah, desa, atau kelompok")
        LazyColumn(Modifier.heightIn(max = 460.dp)) {
            if (emptyLabel != null && matches == null) item {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onChoose(null) }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(emptyLabel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (value == null) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (rows.isEmpty()) item {
                Text("Tidak ada wilayah yang cocok.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp, horizontal = 12.dp))
            }
            items(rows, key = { it.unit.id }) { r ->
                val u = r.unit
                val isOpen = matches != null || u.id in expanded
                val rot by animateFloatAsState(if (isOpen) 90f else 0f, label = "chevron")
                val toggle = { expanded = if (u.id in expanded) expanded - u.id else expanded + u.id }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) {
                            when {
                                u.selectable -> onChoose(u.id)
                                r.hasChildren && matches == null -> toggle()
                            }
                        }
                        .padding(start = (r.depth * 20).dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (r.hasChildren) {
                        IconButton(onClick = { if (matches == null) toggle() }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = if (isOpen) "Tutup" else "Buka", modifier = Modifier.rotate(rot))
                        }
                    } else {
                        Spacer(Modifier.width(40.dp))
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(u.level.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            u.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (u.level != UnitLevel.kelompok) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                u.id == value -> MaterialTheme.colorScheme.primary
                                !u.selectable -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (u.selectable && u.level != UnitLevel.kelompok && r.hasChildren) {
                            Text("(seluruh)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (u.id == value) Icon(Icons.Rounded.Check, contentDescription = "Terpilih", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
