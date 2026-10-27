package com.expenseinterceptor.domain.model

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** A half-open interval of epoch milliseconds: [startMillis, endExclusiveMillis). */
data class DateRange(val startMillis: Long, val endExclusiveMillis: Long) {
  init {
    require(endExclusiveMillis >= startMillis) { "range end before start" }
  }

  operator fun contains(timestamp: Long): Boolean =
    timestamp >= startMillis && timestamp < endExclusiveMillis
}

/**
 * The single place that turns calendar notions (this month, a day) into [DateRange]s.
 *
 * Time and zone are injected so tests are deterministic and the answer never depends on the
 * machine's default zone. The previous code computed month ranges in three separate places with
 * `Calendar.getInstance()`, one of them with an inclusive end and the others exclusive.
 */
class DateRanges @Inject constructor(private val clock: Clock, private val zone: ZoneId) {

  fun today(): LocalDate = LocalDate.now(clock.withZone(zone))

  fun currentMonth(): DateRange = month(YearMonth.from(today()))

  fun month(month: YearMonth): DateRange =
    DateRange(
      startMillis = startOfDay(month.atDay(1)),
      endExclusiveMillis = startOfDay(month.plusMonths(1).atDay(1)),
    )

  /** The whole local day containing [timestamp]. */
  fun dayOf(timestamp: Long): DateRange {
    val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    return DateRange(startOfDay(date), startOfDay(date.plusDays(1)))
  }

  /**
   * Range from the start of the day containing [startMillis] to the end of the day of [endMillis].
   */
  fun spanningDays(startMillis: Long, endMillis: Long): DateRange {
    val first = dayOf(minOf(startMillis, endMillis)).startMillis
    val last = dayOf(maxOf(startMillis, endMillis)).endExclusiveMillis
    return DateRange(first, last)
  }

  /** "01 Mar 2026" or "01 Mar 2026 - 31 Mar 2026"; the exclusive end is shown as its last day. */
  fun label(range: DateRange): String {
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
    val start = Instant.ofEpochMilli(range.startMillis).atZone(zone).toLocalDate()
    val lastInstant = maxOf(range.startMillis, range.endExclusiveMillis - 1)
    val end = Instant.ofEpochMilli(lastInstant).atZone(zone).toLocalDate()
    return if (start == end) formatter.format(start)
    else "${formatter.format(start)} - ${formatter.format(end)}"
  }

  private fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
}
