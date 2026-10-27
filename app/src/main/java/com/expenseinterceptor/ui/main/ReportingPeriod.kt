package com.expenseinterceptor.ui.main

import com.expenseinterceptor.domain.model.DateRange
import com.expenseinterceptor.domain.model.DateRanges
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The date range shared by the Transactions and Dashboard tabs, so changing it on one tab is
 * reflected on the other. Starts as the current month.
 */
@Singleton
class ReportingPeriod @Inject constructor(private val ranges: DateRanges) {

  private val _range = MutableStateFlow(ranges.currentMonth())
  val range: StateFlow<DateRange> = _range.asStateFlow()

  /** Moves the first day. [dayMillis] is any instant on the chosen day. */
  fun setStart(dayMillis: Long) {
    _range.value = ranges.spanningDays(dayMillis, lastDayMillis())
  }

  /** Moves the last day (inclusive). [dayMillis] is any instant on the chosen day. */
  fun setEnd(dayMillis: Long) {
    _range.value = ranges.spanningDays(_range.value.startMillis, dayMillis)
  }

  fun label(range: DateRange): String = ranges.label(range)

  /** The last instant that is still inside the range, which the pickers show as the end date. */
  fun lastDayMillis(): Long = maxOf(_range.value.startMillis, _range.value.endExclusiveMillis - 1)
}
