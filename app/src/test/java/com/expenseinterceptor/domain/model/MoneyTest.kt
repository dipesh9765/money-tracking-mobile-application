package com.expenseinterceptor.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

  @Test
  fun `parses plain, grouped and prefixed text`() {
    assertEquals(123_456L, Money.parse("1234.56")?.minor)
    assertEquals(12_345_600L, Money.parse("1,23,456")?.minor)
    assertEquals(9_900L, Money.parse("₹ 99")?.minor)
    assertEquals(5L, Money.parse("0.05")?.minor)
    assertEquals(0L, Money.parse("0")?.minor)
  }

  @Test
  fun `rounds extra decimals half up`() {
    assertEquals(1_001L, Money.parse("10.005")?.minor)
    assertEquals(1_000L, Money.parse("10.004")?.minor)
  }

  @Test
  fun `rejects text that is not a plain non negative decimal`() {
    listOf("", "abc", "-5", "1e3", "1.2.3", "12abc", "NaN", "--1").forEach {
      assertNull("'$it' should not parse", Money.parse(it))
    }
  }

  @Test
  fun `rejects absurdly large amounts instead of overflowing`() {
    assertNull(Money.parse("99999999999999999999"))
    assertNull(Money.parse("1000000000000"))
  }

  @Test
  fun `sums are exact where doubles drift`() {
    val tenCents = Money.parse("0.10")!!
    var total = Money.ZERO
    repeat(10) { total += tenCents }
    assertEquals(Money.ofRupees(1), total)
    // The same sum in Double is not exactly 1.0
    assertTrue(List(10) { 0.1 }.sum() != 1.0)
  }

  @Test
  fun `formats with indian grouping`() {
    assertEquals("₹0.00", Money.ZERO.format())
    assertEquals("₹5.05", Money.ofMinor(505).format())
    assertEquals("₹999.00", Money.ofRupees(999).format())
    assertEquals("₹1,000.00", Money.ofRupees(1_000).format())
    assertEquals("₹1,23,456.50", Money.ofMinor(12_345_650).format())
    assertEquals("₹12,34,56,789.00", Money.ofRupees(123_456_789).format())
    assertEquals("-₹1,500.00", Money.ofRupees(-1_500).format())
  }

  @Test
  fun `plain string has two decimals for spreadsheets`() {
    assertEquals("0.00", Money.ZERO.toPlainString())
    assertEquals("12.30", Money.ofMinor(1_230).toPlainString())
  }

  @Test
  fun `fromDouble accepts legacy values and rejects invalid ones`() {
    assertEquals(Money.ofMinor(12_345), Money.fromDouble(123.45))
    assertEquals(Money.ofMinor(1_999), Money.fromDouble(19.99))
    assertNull(Money.fromDouble(Double.NaN))
    assertNull(Money.fromDouble(-1.0))
    assertNull(Money.fromDouble(Double.POSITIVE_INFINITY))
  }

  @Test
  fun `comparison and arithmetic`() {
    val a = Money.ofRupees(5)
    val b = Money.ofRupees(3)
    assertEquals(Money.ofRupees(2), a - b)
    assertEquals(Money.ofRupees(8), a + b)
    assertEquals(Money.ofRupees(-5), -a)
    assertTrue(a > b)
  }
}
