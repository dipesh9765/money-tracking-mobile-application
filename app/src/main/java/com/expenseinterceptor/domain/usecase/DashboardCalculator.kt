package com.expenseinterceptor.domain.usecase

import com.expenseinterceptor.domain.model.Expense
import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.model.TransactionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class DashboardSortBy {
  DATE_DESC,
  DATE_ASC,
  AMOUNT_DESC,
  AMOUNT_ASC,
  CATEGORY,
  MERCHANT,
}

enum class DashboardTimeScale {
  DAILY,
  WEEKLY,
  MONTHLY,
}

/** Which direction the dashboard shows. */
enum class TypeFilter {
  ALL,
  DEBIT,
  CREDIT,
}

data class DashboardFilter(
  val categories: Set<String> = emptySet(),
  val banks: Set<String> = emptySet(),
  val type: TypeFilter = TypeFilter.ALL,
  val sortBy: DashboardSortBy = DashboardSortBy.DATE_DESC,
  val timeScale: DashboardTimeScale = DashboardTimeScale.DAILY,
  val minAmount: Money? = null,
  val maxAmount: Money? = null,
)

data class CategoryShare(val category: String, val amount: Money, val percentage: Float)

data class TimeBucket(val startMillis: Long, val amount: Money)

/** Everything the dashboard screen shows, derived from the expenses and the active filter. */
data class DashboardModel(
  val expenses: List<Expense> = emptyList(),
  val net: Money = Money.ZERO,
  val categoryShares: List<CategoryShare> = emptyList(),
  val timeSeries: List<TimeBucket> = emptyList(),
  val banks: Set<String> = emptySet(),
)

/** Pure dashboard maths: filtering, sorting, totals, category shares and time buckets. */
class DashboardCalculator @Inject constructor(private val zone: ZoneId) {

  fun build(all: List<Expense>, filter: DashboardFilter): DashboardModel {
    val filtered = sort(applyFilter(all, filter), filter.sortBy)
    val debits = filtered.filter { !it.isCredit }
    return DashboardModel(
      expenses = filtered,
      net =
        filtered.fold(Money.ZERO) { sum, e -> if (e.isCredit) sum - e.amount else sum + e.amount },
      categoryShares = categoryShares(debits),
      timeSeries = timeSeries(debits, filter.timeScale),
      banks = all.mapTo(sortedSetOf()) { it.bank },
    )
  }

  fun applyFilter(all: List<Expense>, filter: DashboardFilter): List<Expense> =
    all.filter { e ->
      (filter.categories.isEmpty() || e.category in filter.categories) &&
        (filter.banks.isEmpty() || e.bank in filter.banks) &&
        when (filter.type) {
          TypeFilter.ALL -> true
          TypeFilter.DEBIT -> e.type == TransactionType.DEBIT
          TypeFilter.CREDIT -> e.type == TransactionType.CREDIT
        } &&
        (filter.minAmount == null || e.amount >= filter.minAmount) &&
        (filter.maxAmount == null || e.amount <= filter.maxAmount)
    }

  private fun sort(list: List<Expense>, by: DashboardSortBy): List<Expense> =
    when (by) {
      DashboardSortBy.DATE_DESC -> list.sortedByDescending { it.timestamp }
      DashboardSortBy.DATE_ASC -> list.sortedBy { it.timestamp }
      DashboardSortBy.AMOUNT_DESC -> list.sortedByDescending { it.amount }
      DashboardSortBy.AMOUNT_ASC -> list.sortedBy { it.amount }
      DashboardSortBy.CATEGORY -> list.sortedBy { it.category }
      DashboardSortBy.MERCHANT -> list.sortedBy { it.merchant.lowercase() }
    }

  private fun categoryShares(debits: List<Expense>): List<CategoryShare> {
    val total = debits.fold(Money.ZERO) { sum, e -> sum + e.amount }
    if (!total.isPositive) return emptyList()
    return debits
      .groupBy { it.category }
      .map { (category, items) ->
        val amount = items.fold(Money.ZERO) { sum, e -> sum + e.amount }
        CategoryShare(category, amount, (amount.minor * PERCENT / total.minor.toDouble()).toFloat())
      }
      .sortedByDescending { it.amount }
  }

  private fun timeSeries(debits: List<Expense>, scale: DashboardTimeScale): List<TimeBucket> =
    debits
      .groupBy { bucketStart(it.timestamp, scale) }
      .map { (start, items) -> TimeBucket(start, items.fold(Money.ZERO) { s, e -> s + e.amount }) }
      .sortedBy { it.startMillis }

  /** Start of the day, ISO week (Monday) or month containing [timestamp], in the local zone. */
  fun bucketStart(timestamp: Long, scale: DashboardTimeScale): Long {
    val date: LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val start =
      when (scale) {
        DashboardTimeScale.DAILY -> date
        DashboardTimeScale.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        DashboardTimeScale.MONTHLY -> date.withDayOfMonth(1)
      }
    return start.atStartOfDay(zone).toInstant().toEpochMilli()
  }

  private companion object {
    const val PERCENT = 100.0
  }
}
