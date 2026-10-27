package com.expenseinterceptor.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An amount of rupees stored as an integer number of paise.
 *
 * Floating point cannot represent most decimal amounts exactly, so summing a month of `Double`
 * transactions drifts. Integer paise add exactly, compare exactly and round-trip through the
 * database and spreadsheets without surprises.
 */
@JvmInline
value class Money private constructor(val minor: Long) : Comparable<Money> {

  operator fun plus(other: Money): Money = Money(Math.addExact(minor, other.minor))

  operator fun minus(other: Money): Money = Money(Math.subtractExact(minor, other.minor))

  operator fun unaryMinus(): Money = Money(-minor)

  override operator fun compareTo(other: Money): Int = minor.compareTo(other.minor)

  val isPositive: Boolean
    get() = minor > 0

  /** Rupee value for charts, which only need an approximation. Never use it for arithmetic. */
  fun toDouble(): Double = minor / PAISE_PER_RUPEE.toDouble()

  /** Plain decimal text with exactly two fractional digits, e.g. `1234.50`. Safe for CSV/XLSX. */
  fun toPlainString(): String = BigDecimal.valueOf(minor, 2).toPlainString()

  /** Display text with the Indian digit grouping, e.g. `₹1,23,456.50`. */
  fun format(): String {
    val negative = minor < 0
    val abs = Math.abs(minor)
    val rupees = (abs / PAISE_PER_RUPEE).toString()
    val paise = (abs % PAISE_PER_RUPEE).toString().padStart(2, '0')
    return (if (negative) "-" else "") + "₹" + groupIndian(rupees) + "." + paise
  }

  override fun toString(): String = toPlainString()

  companion object {
    const val PAISE_PER_RUPEE = 100L

    /** Largest amount accepted from untrusted text: 10^11 rupees. Keeps sums far from overflow. */
    private const val MAX_MINOR = 10_000_000_000_000L
    private const val GROUP_AFTER_FIRST = 3
    private const val GROUP_SIZE = 2

    val ZERO = Money(0)

    fun ofMinor(minor: Long): Money = Money(minor)

    fun ofRupees(rupees: Long): Money = Money(Math.multiplyExact(rupees, PAISE_PER_RUPEE))

    /**
     * Parses user, SMS or spreadsheet text such as `1234.5`, `1,23,456.00` or `₹ 99`.
     *
     * Returns null for anything that is not a plain non-negative decimal, for negative numbers, and
     * for amounts above [MAX_MINOR]. More than two decimals are rounded half-up.
     */
    fun parse(text: String): Money? {
      val cleaned = text.trim().removePrefix("₹").replace(",", "").replace(" ", "")
      if (cleaned.isEmpty() || !PLAIN_DECIMAL.matches(cleaned)) return null
      val minor =
        BigDecimal(cleaned)
          .setScale(2, RoundingMode.HALF_UP)
          .movePointRight(2)
          .toBigIntegerExact()
          .takeIf { it.bitLength() < Long.SIZE_BITS }
          ?.toLong()
      return minor?.takeIf { it <= MAX_MINOR }?.let(::Money)
    }

    /** Converts a spreadsheet or legacy `Double` rupee value, rounding to the nearest paisa. */
    fun fromDouble(rupees: Double): Money? {
      if (rupees.isNaN() || rupees.isInfinite() || rupees < 0) return null
      return parse(BigDecimal.valueOf(rupees).toPlainString())
    }

    private val PLAIN_DECIMAL = Regex("""\d+(\.\d+)?""")

    private fun groupIndian(digits: String): String {
      if (digits.length <= GROUP_AFTER_FIRST) return digits
      val head = digits.dropLast(GROUP_AFTER_FIRST)
      val tail = digits.takeLast(GROUP_AFTER_FIRST)
      return head.reversed().chunked(GROUP_SIZE).joinToString(",").reversed() + "," + tail
    }
  }
}
