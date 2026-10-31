package com.expenseinterceptor.domain.usecase

import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.model.TransactionType
import com.expenseinterceptor.support.TestTime
import com.expenseinterceptor.support.expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardCalculatorTest {

  private val calculator = DashboardCalculator(TestTime.ZONE)

  private val data =
    listOf(
      expense("500.00", "SWIGGY", "Food", TestTime.millis(2026, 3, 2), bank = "HDFCBK"),
      expense("300.00", "ZOMATO", "Food", TestTime.millis(2026, 3, 3), bank = "IDFCFB"),
      expense("1200.00", "UBER", "Travel", TestTime.millis(2026, 3, 9), bank = "HDFCBK"),
      expense(
        "5000.00",
        "SALARY",
        "Other",
        TestTime.millis(2026, 3, 1),
        type = TransactionType.CREDIT,
        bank = "HDFCBK",
      ),
    )

  @Test
  fun `net is money out minus money in`() {
    val model = calculator.build(data, DashboardFilter())
    assertEquals(
      Money.parse("500.00")!! + Money.parse("300.00")!! + Money.parse("1200.00")!! -
        Money.parse("5000.00")!!,
      model.net
    )
  }

  @Test
  fun `category shares only count debits and add up to a hundred percent`() {
    val shares = calculator.build(data, DashboardFilter()).categoryShares
    assertEquals(listOf("Travel", "Food"), shares.map { it.category })
    assertEquals(Money.parse("800.00"), shares.first { it.category == "Food" }.amount)
    assertEquals(100f, shares.sumOf { it.percentage.toDouble() }.toFloat(), 0.01f)
    assertEquals(60f, shares.first { it.category == "Travel" }.percentage, 0.01f)
  }

  @Test
  fun `no debits means no shares and no series`() {
    val credits = data.filter { it.isCredit }
    val model = calculator.build(credits, DashboardFilter())
    assertTrue(model.categoryShares.isEmpty())
    assertTrue(model.timeSeries.isEmpty())
  }

  @Test
  fun `filters combine`() {
    val filter =
      DashboardFilter(categories = setOf("Food"), banks = setOf("IDFCFB"), type = TypeFilter.DEBIT)
    assertEquals(listOf("ZOMATO"), calculator.build(data, filter).expenses.map { it.merchant })
  }

  @Test
  fun `type filter separates credits from debits`() {
    assertEquals(
      listOf("SALARY"),
      calculator.build(data, DashboardFilter(type = TypeFilter.CREDIT)).expenses.map {
        it.merchant
      },
    )
    assertEquals(3, calculator.build(data, DashboardFilter(type = TypeFilter.DEBIT)).expenses.size)
  }

  @Test
  fun `sorting`() {
    fun order(sort: DashboardSortBy) =
      calculator.build(data, DashboardFilter(sortBy = sort)).expenses.map { it.merchant }
    assertEquals(listOf("UBER", "ZOMATO", "SWIGGY", "SALARY"), order(DashboardSortBy.DATE_DESC))
    assertEquals(listOf("SALARY", "SWIGGY", "ZOMATO", "UBER"), order(DashboardSortBy.DATE_ASC))
    assertEquals(listOf("SALARY", "UBER", "SWIGGY", "ZOMATO"), order(DashboardSortBy.AMOUNT_DESC))
    assertEquals(listOf("ZOMATO", "SWIGGY", "UBER", "SALARY"), order(DashboardSortBy.AMOUNT_ASC))
    assertEquals(listOf("SWIGGY", "ZOMATO", "SALARY", "UBER"), order(DashboardSortBy.CATEGORY))
    assertEquals(listOf("SALARY", "SWIGGY", "UBER", "ZOMATO"), order(DashboardSortBy.MERCHANT))
  }

  @Test
  fun `banks are listed once and sorted`() {
    assertEquals(
      listOf("HDFCBK", "IDFCFB"),
      calculator.build(data, DashboardFilter()).banks.toList()
    )
  }

  @Test
  fun `daily buckets start at local midnight`() {
    val day = calculator.bucketStart(TestTime.millis(2026, 3, 9, 23, 59), DashboardTimeScale.DAILY)
    assertEquals(TestTime.millis(2026, 3, 9, 0, 0), day)
  }

  @Test
  fun `weekly buckets start on monday`() {
    // 2026-03-11 is a Wednesday; the week starts Monday 2026-03-09
    val week = calculator.bucketStart(TestTime.millis(2026, 3, 11), DashboardTimeScale.WEEKLY)
    assertEquals(TestTime.millis(2026, 3, 9, 0, 0), week)
    // a Sunday belongs to the week that started the previous Monday
    val sunday = calculator.bucketStart(TestTime.millis(2026, 3, 15), DashboardTimeScale.WEEKLY)
    assertEquals(TestTime.millis(2026, 3, 9, 0, 0), sunday)
  }

  @Test
  fun `monthly buckets merge a month and are sorted by time`() {
    val twoMonths = data + expense("50.00", "OLD", "Food", TestTime.millis(2026, 2, 20))
    val series =
      calculator
        .build(twoMonths, DashboardFilter(timeScale = DashboardTimeScale.MONTHLY))
        .timeSeries
    assertEquals(
      listOf(TestTime.millis(2026, 2, 1, 0, 0), TestTime.millis(2026, 3, 1, 0, 0)),
      series.map { it.startMillis }
    )
    assertEquals(Money.parse("2000.00"), series.last().amount)
  }
}
