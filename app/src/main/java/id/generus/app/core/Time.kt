package id.generus.app.core

import id.generus.app.BuildConfig
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Waktu agenda diinput & ditampilkan dalam zona waktu daerah (default WIB),
// disimpan sebagai timestamptz (UTC). Padanan src/lib/time.ts.

val ID_LOCALE: Locale = Locale.forLanguageTag("id-ID")
val APP_ZONE: ZoneId = runCatching { ZoneId.of(BuildConfig.APP_TIMEZONE) }.getOrDefault(ZoneId.of("Asia/Jakarta"))

/** Singkatan zona untuk label, mis. "WIB". */
fun zoneLabel(zone: ZoneId = APP_ZONE): String = when (zone.id) {
    "Asia/Jakarta", "Asia/Pontianak" -> "WIB"
    "Asia/Makassar" -> "WITA"
    "Asia/Jayapura" -> "WIT"
    else -> zone.id
}

/** Timestamp ISO dari Postgres ("2026-10-22T06:00:00+00:00") → Instant. */
fun parseInstant(iso: String): Instant = OffsetDateTime.parse(iso).toInstant()

fun today(): LocalDate = LocalDate.now(APP_ZONE)

fun currentMonth(): YearMonth = YearMonth.now(APP_ZONE)

/** Tanggal + jam di zona daerah → Instant. */
fun zonedToInstant(date: LocalDate, time: LocalTime): Instant = LocalDateTime.of(date, time).atZone(APP_ZONE).toInstant()

fun Instant.localDate(): LocalDate = atZone(APP_ZONE).toLocalDate()
fun Instant.localTime(): LocalTime = atZone(APP_ZONE).toLocalTime().withSecond(0).withNano(0)

private val TIME = DateTimeFormatter.ofPattern("HH.mm", ID_LOCALE)
private val DATE_LONG = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", ID_LOCALE)
private val DATE_MEDIUM = DateTimeFormatter.ofPattern("d MMMM yyyy", ID_LOCALE)
private val DATE_SHORT = DateTimeFormatter.ofPattern("d MMM yyyy", ID_LOCALE)
private val DAY_SHORT = DateTimeFormatter.ofPattern("EEE, d MMM", ID_LOCALE)

/** "19.30" */
fun Instant.formatTime(): String = atZone(APP_ZONE).format(TIME)

/** "Kamis, 22 Oktober 2026" */
fun Instant.formatDateLong(): String = atZone(APP_ZONE).format(DATE_LONG)

/** "22 Okt 2026 19.30" */
fun Instant.formatDateTime(): String = "${atZone(APP_ZONE).format(DATE_SHORT)} ${formatTime()}"

/** "Kam, 22 Okt" */
fun Instant.formatDayShort(): String = atZone(APP_ZONE).format(DAY_SHORT)

/** "22 Oktober 2026" */
fun LocalDate.formatMedium(): String = format(DATE_MEDIUM)

/** "22 Okt 2026" */
fun LocalDate.formatShort(): String = format(DATE_SHORT)

/** "5 Okt – 7 Nov 2026" (padanan dateRange di kurikulum/shared.tsx). */
fun dateRange(from: LocalDate, to: LocalDate): String {
    val d = DateTimeFormatter.ofPattern("d MMM", ID_LOCALE)
    return if (from.year == to.year) "${from.format(d)} – ${to.format(DATE_SHORT)}" else "${from.formatShort()} – ${to.formatShort()}"
}

/** "Oktober 2026" */
fun YearMonth.label(): String = "${month.getDisplayName(TextStyle.FULL, ID_LOCALE)} $year"

/** Awal bulan (inklusif) & awal bulan berikutnya (eksklusif) di zona daerah. */
fun YearMonth.range(): Pair<Instant, Instant> =
    atDay(1).atStartOfDay(APP_ZONE).toInstant() to plusMonths(1).atDay(1).atStartOfDay(APP_ZONE).toInstant()

/** `n` bulan terakhir, terbaru dulu. */
fun recentMonths(n: Int, from: YearMonth = currentMonth()): List<YearMonth> = (0 until n).map { from.minusMonths(it.toLong()) }

/** Usia (tahun) pada tanggal `on`. */
fun ageOn(birthDate: LocalDate, on: LocalDate = today()): Int = java.time.Period.between(birthDate, on).years

/** Angka format Indonesia: 1.234 */
fun Int.formatId(): String = String.format(ID_LOCALE, "%,d", this)
fun Long.formatId(): String = String.format(ID_LOCALE, "%,d", this)
