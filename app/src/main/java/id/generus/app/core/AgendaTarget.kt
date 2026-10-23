package id.generus.app.core

import id.generus.app.data.model.Gender
import id.generus.app.data.model.Jenjang

// Sasaran agenda dipilih per "segmen" (Pra Remaja, Remaja Pelajar, Remaja Mandiri)
// lalu disimpan sebagai target_jenjang_ids + target_kategori_ids.
// Padanan src/lib/agenda-target.ts. Aturan di database: kategori hanya menyaring
// generus yang punya kategori.

data class Segment(val key: String, val label: String, val jenjangId: String, val kategoriId: String?)

/** Jenjang tanpa kategori = 1 segmen, dengan kategori = 1 per kategori. */
fun segmentsOf(jenjang: List<Jenjang>): List<Segment> = jenjang.flatMap { j ->
    if (j.kategori.isEmpty()) listOf(Segment(j.id, j.name, j.id, null))
    else j.kategori.map { k -> Segment("${j.id}:${k.id}", "${j.name} ${k.name}", j.id, k.id) }
}

/** Segmen terpilih → kolom target. Semua/tidak ada yang dipilih = tanpa filter. */
fun segmentsToTarget(selected: Set<String>, jenjang: List<Jenjang>): Pair<List<String>, List<String>> {
    val all = segmentsOf(jenjang)
    val picked = all.filter { it.key in selected }
    if (picked.isEmpty() || picked.size == all.size) return emptyList<String>() to emptyList()
    val jenjangIds = picked.map { it.jenjangId }.distinct()
    // Filter kategori hanya perlu bila ada jenjang berkategori yang tidak dipilih seluruh kategorinya.
    val partial = jenjang.any { j ->
        j.kategori.isNotEmpty() && j.id in jenjangIds && j.kategori.any { k -> picked.none { it.kategoriId == k.id } }
    }
    val kategoriIds = if (partial) picked.mapNotNull { it.kategoriId } else emptyList()
    return jenjangIds to kategoriIds
}

/** Kolom target → kunci segmen (untuk mengisi form ubah & label). */
fun targetToSegments(jenjangIds: List<String>, kategoriIds: List<String>, jenjang: List<Jenjang>): Set<String> =
    segmentsOf(jenjang).filter { s ->
        (jenjangIds.isEmpty() || s.jenjangId in jenjangIds) &&
            (kategoriIds.isEmpty() || s.kategoriId == null || s.kategoriId in kategoriIds)
    }.map { it.key }.toSet()

fun targetLabel(jenjangIds: List<String>, kategoriIds: List<String>, gender: Gender?, jenjang: List<Jenjang>): String {
    val all = segmentsOf(jenjang)
    val keys = targetToSegments(jenjangIds, kategoriIds, jenjang)
    val seg = if (keys.size == all.size) "Semua jenjang" else all.filter { it.key in keys }.joinToString(", ") { it.label }
    val g = when (gender) {
        Gender.L -> " · Laki-laki"
        Gender.P -> " · Perempuan"
        null -> ""
    }
    return seg + g
}
