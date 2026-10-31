package com.expenseinterceptor.domain.model

import com.expenseinterceptor.support.TestTime
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateRangesTest {

  private val ranges = TestTime.RANGES

  @Test
  fun `current month is the local calendar month with an exclusive end`() {
    val march = ranges.currentMonth()
    assertEquals(TestTime.millis(2026, 3, 1, 0, 0), march.startMillis)
    assertEquals(TestTime.millis(2026, 4, 1, 0, 0), march.endExclusiveMillis)
  }

  @Test
  fun `first instant of the next month is outside and the last millisecond is inside`() {
    val march = ranges.currentMonth()
    assertTrue(march.endExclusiveMillis - 1 in march)
    assertFalse(march.endExclusiveMillis in march)
    assertTrue(march.startMillis in march)
    assertFalse(march.startMillis - 1 in march)
  }

  @Test
  fun `leap year february has 29 days`() {
    val feb = ranges.month(YearMonth.of(2028, 2))
    val days = (feb.endExclusiveMillis - feb.startMillis) / (24 * 60 * 60 * 1000)
    assertEquals(29, days)
  }

  @Test
  fun `a month containing a daylight saving change is not 30 times 24 hours`() {
    val newYork = ZoneId.of("America/New_York")
    val dst =
      DateRanges(Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), newYork), newYork)
        .currentMonth()
    val hours = (dst.endExclusiveMillis - dst.startMillis) / (60 * 60 * 1000)
    assertEquals(31 * 24 - 1, hours) // clocks spring forward on 8 March 2026
  }

  @Test
  fun `the answer depends on the injected zone not the machine zone`() {
    val instant = Instant.parse("2026-03-31T20:00:00Z") // 1 April 01:30 in Kolkata
    val kolkata =
      DateRanges(Clock.fixed(instant, ZoneId.of("Asia/Kolkata")), ZoneId.of("Asia/Kolkata"))
    val utc = DateRanges(Clock.fixed(instant, ZoneId.of("UTC")), ZoneId.of("UTC"))
    assertEquals(YearMonth.of(2026, 4), YearMonth.from(kolkata.today()))
    assertEquals(YearMonth.of(2026, 3), YearMonth.from(utc.today()))
  }

  @Test
  fun `day of covers exactly one local day`() {
    val day = ranges.dayOf(TestTime.millis(2026, 3, 10, 23, 59))
    assertEquals(TestTime.millis(2026, 3, 10, 0, 0), day.startMillis)
    assertEquals(TestTime.millis(2026, 3, 11, 0, 0), day.endExclusiveMillis)
  }

  @Test
  fun `spanning days accepts either order and includes the whole last day`() {
    val a = TestTime.millis(2026, 3, 5, 15)
    val b = TestTime.millis(2026, 3, 9, 3)
    val forward = ranges.spanningDays(a, b)
    assertEquals(forward, ranges.spanningDays(b, a))
    assertEquals(TestTime.millis(2026, 3, 5, 0, 0), forward.startMillis)
    assertEquals(TestTime.millis(2026, 3, 10, 0, 0), forward.endExclusiveMillis)
  }

  @Test
  fun `label shows a single day once and a range with its last day`() {
    Locale.setDefault(Locale.ENGLISH)
    val one = ranges.dayOf(TestTime.millis(2026, 3, 10))
    assertEquals("10 Mar 2026", ranges.label(one))
    assertEquals("01 Mar 2026 - 31 Mar 2026", ranges.label(ranges.currentMonth()))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `a range cannot end before it starts`() {
    DateRange(10, 5)
  }

  @Test
  fun `today uses the clock`() {
    assertEquals(
      ZonedDateTime.of(2026, 3, 15, 0, 0, 0, 0, TestTime.ZONE).toLocalDate(),
      ranges.today()
    )
  }
}
