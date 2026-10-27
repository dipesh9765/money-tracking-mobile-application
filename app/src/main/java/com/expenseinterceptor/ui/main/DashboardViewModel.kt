package com.expenseinterceptor.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expenseinterceptor.domain.model.DateRange
import com.expenseinterceptor.domain.repository.ExpenseRepository
import com.expenseinterceptor.domain.usecase.DashboardCalculator
import com.expenseinterceptor.domain.usecase.DashboardFilter
import com.expenseinterceptor.domain.usecase.DashboardModel
import com.expenseinterceptor.domain.usecase.DashboardSortBy
import com.expenseinterceptor.domain.usecase.DashboardTimeScale
import com.expenseinterceptor.domain.usecase.TypeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Filters plus chart-only selection for the Dashboard tab. */
data class DashboardState(
  val filter: DashboardFilter = DashboardFilter(),
  val highlightedCategories: Set<String> = emptySet(),
)

@HiltViewModel
class DashboardViewModel
@Inject
constructor(
  repository: ExpenseRepository,
  private val period: ReportingPeriod,
  private val calculator: DashboardCalculator,
) : ViewModel() {

  private val _state = MutableStateFlow(DashboardState())
  val state: StateFlow<DashboardState> = _state.asStateFlow()

  val range: StateFlow<DateRange> = period.range

  val model: StateFlow<DashboardModel> =
    combine(period.range.flatMapLatest { repository.observe(it) }, _state) { list, state ->
        calculator.build(list, state.filter)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DashboardModel())

  fun updateStartDate(dayMillis: Long) = period.setStart(dayMillis)

  fun updateEndDate(dayMillis: Long) = period.setEnd(dayMillis)

  fun lastDayMillis(): Long = period.lastDayMillis()

  fun setTimeScale(scale: DashboardTimeScale) = updateFilter { it.copy(timeScale = scale) }

  fun setSortBy(sort: DashboardSortBy) = updateFilter { it.copy(sortBy = sort) }

  fun setType(type: TypeFilter) = updateFilter { it.copy(type = type) }

  fun toggleCategory(category: String) = updateFilter {
    it.copy(categories = it.categories.toggle(category))
  }

  fun toggleBank(bank: String) = updateFilter { it.copy(banks = it.banks.toggle(bank)) }

  fun toggleHighlight(category: String) =
    _state.update { it.copy(highlightedCategories = it.highlightedCategories.toggle(category)) }

  private fun updateFilter(change: (DashboardFilter) -> DashboardFilter) =
    _state.update { it.copy(filter = change(it.filter)) }

  private fun Set<String>.toggle(item: String): Set<String> =
    if (item in this) this - item else this + item

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
