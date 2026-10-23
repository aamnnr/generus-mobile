package id.generus.app.core

import android.util.Xml
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

/**
 * Pembaca .xlsx ringan (tanpa Apache POI): file xlsx adalah zip berisi XML.
 * Cukup untuk template data generus — nilai sel teks, angka, dan boolean.
 * Tanggal Excel tetap berupa angka serial; ubah dengan [excelSerialToDate].
 */
object Xlsx {
    class Workbook(val sheets: Map<String, List<List<Any?>>>)

    fun read(input: InputStream): Workbook {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.trimStart('/')
                if (!e.isDirectory && (name.endsWith(".xml") || name.endsWith(".rels"))) files[name] = zip.readBytes()
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val rels = files["xl/_rels/workbook.xml.rels"]?.let(::parseRels).orEmpty()
        val sheets = LinkedHashMap<String, List<List<Any?>>>()
        files["xl/workbook.xml"]?.let(::parseWorkbook)?.forEach { (name, rid) ->
            val target = rels[rid] ?: return@forEach
            val path = if (target.startsWith("/")) target.trimStart('/') else "xl/$target"
            files[path]?.let { sheets[name] = parseSheet(it, shared) }
        }
        return Workbook(sheets)
    }

    /** Serial tanggal Excel (sistem 1900) → LocalDate. */
    fun excelSerialToDate(serial: Double): LocalDate = LocalDate.of(1899, 12, 30).plusDays(serial.toLong())

    private fun parser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(bytes.inputStream(), "UTF-8")
        }

    private fun parseWorkbook(bytes: ByteArray): List<Pair<String, String>> {
        val p = parser(bytes)
        val out = mutableListOf<Pair<String, String>>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                out += (p.getAttributeValue(null, "name") ?: "") to (p.getAttributeValue(null, "r:id") ?: "")
            }
        }
        return out
    }

    private fun parseRels(bytes: ByteArray): Map<String, String> {
        val p = parser(bytes)
        val out = HashMap<String, String>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                out[p.getAttributeValue(null, "Id") ?: continue] = p.getAttributeValue(null, "Target") ?: continue
            }
        }
        return out
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val p = parser(bytes)
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inSi = false
        var inT = false
        var inRph = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> { inSi = true; sb.clear() }
                    "t" -> inT = true
                    "rPh" -> inRph = true
                }
                XmlPullParser.TEXT -> if (inSi && inT && !inRph) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "si" -> { out += sb.toString(); inSi = false }
                    "t" -> inT = false
                    "rPh" -> inRph = false
                }
            }
        }
        return out
    }

    /** "AB12" → indeks kolom 0-based (27). */
    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return n - 1
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<Any?>> {
        val p = parser(bytes)
        val rows = mutableListOf<MutableList<Any?>>()
        var row: MutableList<Any?>? = null
        var rowIndex = -1
        var col = -1
        var type: String? = null
        val value = StringBuilder()
        var inValue = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> {
                        val r = p.getAttributeValue(null, "r")?.toIntOrNull()?.minus(1) ?: (rowIndex + 1)
                        while (rows.size < r) rows += mutableListOf<Any?>()
                        row = mutableListOf()
                        rowIndex = r
                        col = -1
                    }
                    "c" -> {
                        col = p.getAttributeValue(null, "r")?.let(::columnIndex) ?: (col + 1)
                        type = p.getAttributeValue(null, "t")
                        value.clear()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = value.toString()
                        val cell: Any? = when {
                            raw.isEmpty() -> null
                            type == "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) }
                            type == "b" -> raw == "1"
                            type == "str" || type == "inlineStr" || type == "e" -> raw
                            else -> raw.toDoubleOrNull() ?: raw
                        }
                        row?.let { r ->
                            while (r.size <= col) r += null
                            r[col] = cell
                        }
                    }
                    "row" -> row?.let { rows += it }
                }
            }
        }
        return rows
    }
}
