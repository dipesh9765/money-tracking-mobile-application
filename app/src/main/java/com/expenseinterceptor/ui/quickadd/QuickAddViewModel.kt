package com.expenseinterceptor.ui.quickadd

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expenseinterceptor.data.local.ExpenseEntity
import com.expenseinterceptor.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Available expense categories. */
enum class ExpenseCategory(val displayName: String) {
  FOOD("Food"),
  TRAVEL("Travel"),
  BILLS("Bills"),
  SHOPPING("Shopping"),
  ENTERTAINMENT("Entertainment"),
  HEALTH("Health"),
  OTHER("Other")
}

/** UI state for the Quick Add screen. */
data class QuickAddUiState(
  val amount: String = "",
  val merchant: String = "",
  val category: ExpenseCategory = ExpenseCategory.OTHER,
  val title: String = "",
  val originalSms: String = "",
  val bank: String = "Unknown Bank",
  val transactionType: String = "DEBIT",
  val isSaving: Boolean = false,
  val isSaved: Boolean = false,
  val errorMessage: String? = null
)

/** ViewModel for the Quick Add expense screen. */
@HiltViewModel
class QuickAddViewModel
@Inject
constructor(private val repository: ExpenseRepository, savedStateHandle: SavedStateHandle) :
  ViewModel() {

  private val _uiState = MutableStateFlow(QuickAddUiState())
  val uiState: StateFlow<QuickAddUiState> = _uiState.asStateFlow()

  /** Initializes the ViewModel with data from the notification intent. */
  fun initialize(
    amount: Double?,
    merchant: String?,
    originalSms: String?,
    bank: String?,
    transactionType: String?
  ) {
    _uiState.value =
      _uiState.value.copy(
        amount = amount?.toString() ?: "",
        merchant = merchant ?: "",
        originalSms = originalSms ?: "",
        bank = bank ?: "Unknown Bank",
        transactionType = transactionType ?: "DEBIT"
      )
  }

  /** Updates the amount field. */
  fun updateAmount(amount: String) {
    // Only allow valid number input
    if (amount.isEmpty() || amount.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
      _uiState.value = _uiState.value.copy(amount = amount, errorMessage = null)
    }
  }

  /** Updates the merchant field. */
  fun updateMerchant(merchant: String) {
    _uiState.value = _uiState.value.copy(merchant = merchant, errorMessage = null)
  }

  /** Updates the selected category. */
  fun updateCategory(category: ExpenseCategory) {
    _uiState.value = _uiState.value.copy(category = category)
  }

  /** Updates the optional title field. */
  fun updateTitle(title: String) {
    _uiState.value = _uiState.value.copy(title = title)
  }

  /** Validates input and saves the expense to the database. */
  fun saveExpense() {
    val state = _uiState.value

    // Validate amount
    val amount = state.amount.toDoubleOrNull()
    if (amount == null || amount <= 0) {
      _uiState.value = state.copy(errorMessage = "Please enter a valid amount")
      return
    }

    // Validate merchant
    if (state.merchant.isBlank()) {
      _uiState.value = state.copy(errorMessage = "Please enter a merchant/description")
      return
    }

    _uiState.value = state.copy(isSaving = true, errorMessage = null)

    viewModelScope.launch {
      try {
        val expense =
          ExpenseEntity(
            amount = amount,
            merchant = state.merchant.trim(),
            category = state.category.displayName,
            title = state.title.trim().ifEmpty { null },
            originalSms = state.originalSms.ifEmpty { null },
            bank = state.bank.ifBlank { "Unknown Bank" },
            transactionType = state.transactionType.ifBlank { "DEBIT" }
          )

        repository.insertExpense(expense)

        _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
      } catch (e: Exception) {
        _uiState.value =
          _uiState.value.copy(
            isSaving = false,
            errorMessage = "Failed to save expense: ${e.message}"
          )
      }
    }
  }

  /** Clears any error message. */
  fun clearError() {
    _uiState.value = _uiState.value.copy(errorMessage = null)
  }
}
