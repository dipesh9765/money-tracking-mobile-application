package com.expenseinterceptor.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expenseinterceptor.domain.model.Expense
import com.expenseinterceptor.domain.model.ExpenseCategory
import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.model.TransactionType
import com.expenseinterceptor.domain.repository.ExpenseRepository
import com.expenseinterceptor.domain.repository.SaveResult
import com.expenseinterceptor.domain.sms.SmsFingerprint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the Quick Add screen. */
data class QuickAddUiState(
  val amount: String = "",
  val merchant: String = "",
  val category: ExpenseCategory = ExpenseCategory.OTHER,
  val title: String = "",
  val originalSms: String = "",
  val bank: String = Expense.UNKNOWN_BANK,
  val type: TransactionType = TransactionType.DEBIT,
  val timestamp: Long = 0,
  val isSaving: Boolean = false,
  val isSaved: Boolean = false,
  val errorMessage: String? = null,
)

/** Categorises one detected transaction and saves it. */
@HiltViewModel
class QuickAddViewModel
@Inject
constructor(private val repository: ExpenseRepository, private val clock: Clock) : ViewModel() {

  private val _uiState = MutableStateFlow(QuickAddUiState())
  val uiState: StateFlow<QuickAddUiState> = _uiState.asStateFlow()

  /** Fills the form from a notification. Calling it again replaces everything typed so far. */
  fun initialize(args: QuickAddArgs) {
    _uiState.value =
      QuickAddUiState(
        amount = Money.ofMinor(args.amountMinor).toPlainString(),
        merchant = args.merchant,
        originalSms = args.originalSms,
        bank = args.bank.ifBlank { Expense.UNKNOWN_BANK },
        type = args.type,
        timestamp = args.timestamp,
      )
  }

  fun updateAmount(amount: String) {
    if (AMOUNT_INPUT.matches(amount)) {
      _uiState.update { it.copy(amount = amount, errorMessage = null) }
    }
  }

  fun updateMerchant(merchant: String) =
    _uiState.update { it.copy(merchant = merchant, errorMessage = null) }

  fun updateCategory(category: ExpenseCategory) = _uiState.update { it.copy(category = category) }

  fun updateTitle(title: String) = _uiState.update { it.copy(title = title) }

  fun saveExpense() {
    val state = _uiState.value
    if (state.isSaving) return
    val amount = Money.parse(state.amount)?.takeIf { it.isPositive }
    if (amount == null) return fail("Please enter a valid amount")
    if (state.merchant.isBlank()) return fail("Please enter a merchant/description")
    _uiState.update { it.copy(isSaving = true, errorMessage = null) }

    viewModelScope.launch {
      val timestamp = state.timestamp.takeIf { it > 0 } ?: clock.millis()
      val sms = state.originalSms.takeIf { it.isNotEmpty() }
      val expense =
        Expense(
          amount = amount,
          merchant = state.merchant.trim(),
          category = state.category.displayName,
          timestamp = timestamp,
          type = state.type,
          bank = state.bank.ifBlank { Expense.UNKNOWN_BANK },
          title = state.title.trim().ifEmpty { null },
          originalSms = sms,
          dedupeKey = sms?.let { SmsFingerprint.of(it, timestamp) },
        )
      when (repository.save(expense)) {
        is SaveResult.Saved -> _uiState.update { it.copy(isSaving = false, isSaved = true) }
        SaveResult.Duplicate ->
          _uiState.update {
            it.copy(isSaving = false, errorMessage = "This message is already recorded")
          }
      }
    }
  }

  private fun fail(message: String) = _uiState.update { it.copy(errorMessage = message) }

  private companion object {
    val AMOUNT_INPUT = Regex("""^\d{0,12}\.?\d{0,2}$""")
  }
}
