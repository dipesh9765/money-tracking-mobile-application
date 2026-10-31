package com.expenseinterceptor.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expenseinterceptor.domain.model.DateRange
import com.expenseinterceptor.domain.model.Expense
import com.expenseinterceptor.domain.model.ExpenseCategory
import com.expenseinterceptor.domain.model.TransactionType
import com.expenseinterceptor.domain.repository.ExpenseRepository
import com.expenseinterceptor.domain.usecase.ExpenseSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Transactions tab shows for the selected range. */
data class TransactionsState(
  val range: DateRange,
  val rangeLabel: String,
  val expenses: List<Expense> = emptyList(),
  val summary: ExpenseSummary = ExpenseSummary.of(emptyList()),
)

data class EditExpenseState(
  val original: Expense,
  val category: ExpenseCategory,
  val bank: String,
  val type: TransactionType,
  val isSaving: Boolean = false,
  val error: String? = null,
)

/** One-off messages for the UI. Events are consumed once, so they are never replayed. */
sealed interface TransactionsEvent {
  data class Message(val text: String) : TransactionsEvent

  /** A transaction was deleted; the UI offers to undo. */
  data class Deleted(val expense: Expense) : TransactionsEvent
}

@HiltViewModel
class TransactionsViewModel
@Inject
constructor(private val repository: ExpenseRepository, private val period: ReportingPeriod) :
  ViewModel() {

  val state: StateFlow<TransactionsState> =
    period.range
      .flatMapLatest { range ->
        repository.observe(range).map { list ->
          TransactionsState(range, period.label(range), list, ExpenseSummary.of(list))
        }
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        TransactionsState(period.range.value, period.label(period.range.value)),
      )

  private val _editState = MutableStateFlow<EditExpenseState?>(null)
  val editState: StateFlow<EditExpenseState?> = _editState.asStateFlow()

  private val _events = Channel<TransactionsEvent>(Channel.BUFFERED)
  val events: Flow<TransactionsEvent> = _events.receiveAsFlow()

  fun updateStartDate(dayMillis: Long) = period.setStart(dayMillis)

  fun updateEndDate(dayMillis: Long) = period.setEnd(dayMillis)

  fun startEditing(expense: Expense) {
    _editState.value =
      EditExpenseState(
        original = expense,
        category = ExpenseCategory.fromDisplayName(expense.category),
        bank = expense.bank,
        type = expense.type,
      )
  }

  fun dismissEditing() {
    _editState.value = null
  }

  fun updateEditCategory(category: ExpenseCategory) =
    _editState.update { it?.copy(category = category, error = null) }

  fun updateEditBank(bank: String) = _editState.update { it?.copy(bank = bank, error = null) }

  fun updateEditType(type: TransactionType) =
    _editState.update { it?.copy(type = type, error = null) }

  fun saveEditedExpense() {
    val edit = _editState.value ?: return
    if (edit.isSaving) return
    _editState.value = edit.copy(isSaving = true, error = null)
    viewModelScope.launch {
      val updated =
        edit.original.copy(
          category = edit.category.displayName,
          bank = edit.bank.trim().ifBlank { Expense.UNKNOWN_BANK },
          type = edit.type,
        )
      repository.update(updated)
      _editState.value = null
      _events.send(TransactionsEvent.Message("Expense updated"))
    }
  }

  fun deleteExpense(expense: Expense) {
    viewModelScope.launch {
      repository.delete(expense.id)
      _events.send(TransactionsEvent.Deleted(expense))
    }
  }

  /** Puts a deleted expense back (the Undo action). */
  fun restore(expense: Expense) {
    viewModelScope.launch {
      repository.save(expense.copy(id = 0))
      _events.send(TransactionsEvent.Message("Transaction restored"))
    }
  }

  fun deleteAllExpenses() {
    viewModelScope.launch {
      repository.deleteAll()
      _events.send(TransactionsEvent.Message("Deleted all expenses"))
    }
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
