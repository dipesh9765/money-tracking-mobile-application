package com.expenseinterceptor.ui.main

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expenseinterceptor.data.local.ExpenseEntity
import com.expenseinterceptor.data.repository.ExpenseRepository
import com.expenseinterceptor.util.CsvExporter
import com.expenseinterceptor.util.ExcelExporter
import com.expenseinterceptor.util.PdfExporter
import com.expenseinterceptor.ui.quickadd.ExpenseCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/**
 * UI state for the main screen.
 */
data class MainUiState(
    val startDateMillis: Long,
    val endDateMillis: Long,
    val dateRangeLabel: String,
    val isPreparingShare: Boolean = false,
    val shareError: String? = null,
    val shareIntent: Intent? = null,
    val infoMessage: String? = null
)

data class EditExpenseState(
    val original: ExpenseEntity,
    val category: ExpenseCategory,
    val bank: String,
    val transactionType: String,
    val isSaving: Boolean = false,
    val error: String? = null
)

enum class ShareFormat {
    TEXT,
    CSV,
    EXCEL,
    PDF
}

/**
 * ViewModel for the main expense list screen.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val defaultRange: Pair<Long, Long> = createCurrentMonthRange()

    private val _dateRange = MutableStateFlow(defaultRange)

    /**
     * Expenses for the selected date range.
     */
    val expenses: StateFlow<List<ExpenseEntity>> = _dateRange
        .flatMapLatest { (start, end) -> repository.getExpensesForRange(start, end) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Total amount for the selected date range.
     */
    val totalAmount: StateFlow<Double> = _dateRange
        .flatMapLatest { (start, end) -> repository.getTotalForRange(start, end) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    private val _uiState = MutableStateFlow(
        MainUiState(
            startDateMillis = defaultRange.first,
            endDateMillis = defaultRange.second,
            dateRangeLabel = formatRangeLabel(defaultRange.first, defaultRange.second)
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _editState = MutableStateFlow<EditExpenseState?>(null)
    val editState: StateFlow<EditExpenseState?> = _editState.asStateFlow()

    /**
     * Returns the current range label for display.
     */
    fun getCurrentRangeLabel(): String = _uiState.value.dateRangeLabel

    fun updateStartDate(millis: Long) {
        val (_, end) = _dateRange.value
        updateDateRangeInternal(start = millis, end = maxOf(millis, end))
    }

    fun updateEndDate(millis: Long) {
        val (start, _) = _dateRange.value
        updateDateRangeInternal(start = minOf(start, millis), end = millis)
    }

    private fun updateDateRangeInternal(start: Long, end: Long) {
        val safeEnd = if (end < start) start else end
        val label = formatRangeLabel(start, safeEnd)
        _dateRange.value = start to safeEnd
        _uiState.update { current ->
            current.copy(
                startDateMillis = start,
                endDateMillis = safeEnd,
                dateRangeLabel = label
            )
        }
    }

    fun startEditing(expense: ExpenseEntity) {
        _editState.value = EditExpenseState(
            original = expense,
            category = toCategory(expense.category),
            bank = expense.bank,
            transactionType = expense.transactionType
        )
    }

    fun dismissEditing() {
        _editState.value = null
    }

    fun updateEditCategory(category: ExpenseCategory) {
        _editState.update { current ->
            current?.copy(category = category, error = null)
        }
    }

    fun updateEditBank(bank: String) {
        _editState.update { current ->
            current?.copy(bank = bank, error = null)
        }
    }

    fun updateEditTransactionType(type: String) {
        _editState.update { current ->
            current?.copy(transactionType = type, error = null)
        }
    }

    fun saveEditedExpense() {
        val state = _editState.value ?: return
        if (state.isSaving) return
        _editState.value = state.copy(isSaving = true, error = null)

        viewModelScope.launch {
            try {
                val updated = state.original.copy(
                    category = state.category.displayName,
                    bank = state.bank.ifBlank { "Unknown Bank" },
                    transactionType = state.transactionType.ifBlank { "DEBIT" }
                )
                repository.updateExpense(updated)
                _editState.value = null
                _uiState.update {
                    it.copy(infoMessage = "Expense updated")
                }
            } catch (e: Exception) {
                _editState.update {
                    it?.copy(isSaving = false, error = "Failed to update: ${e.message}")
                }
            }
        }
    }

    /**
     * Deletes an expense from the database.
     */
    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense.id)
        }
    }

    /**
     * Prepares a share intent for the requested format.
     */
    fun prepareShare(format: ShareFormat) {
        _uiState.update {
            it.copy(
                isPreparingShare = true,
                shareError = null,
                shareIntent = null
            )
        }

        viewModelScope.launch {
            try {
                val (start, end) = _dateRange.value
                val expensesForRange = repository.getExpensesForRangeList(start, end)
                if (expensesForRange.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isPreparingShare = false,
                            shareError = "No expenses in the selected range"
                        )
                    }
                    return@launch
                }

                val rangeLabel = formatRangeLabel(start, end)
                val intent = when (format) {
                    ShareFormat.TEXT -> buildTextShareIntent(expensesForRange, rangeLabel)
                    ShareFormat.CSV -> buildFileShareIntent(
                        uri = CsvExporter.exportToCsv(context, expensesForRange, rangeLabel),
                        mimeType = "text/csv",
                        subject = "Expenses - $rangeLabel"
                    )
                    ShareFormat.EXCEL -> buildFileShareIntent(
                        uri = ExcelExporter.exportToExcel(context, expensesForRange, rangeLabel),
                        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        subject = "Expenses - $rangeLabel"
                    )
                    ShareFormat.PDF -> buildFileShareIntent(
                        uri = PdfExporter.exportToPdf(context, expensesForRange, rangeLabel),
                        mimeType = "application/pdf",
                        subject = "Expenses - $rangeLabel"
                    )
                }

                if (intent == null) {
                    _uiState.update {
                        it.copy(
                            isPreparingShare = false,
                            shareError = "Failed to create share content"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isPreparingShare = false,
                            shareIntent = intent
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPreparingShare = false,
                        shareError = "Share failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
    * Consumes a pending share intent after the UI launches it.
     */
    fun consumeShareIntent() {
        _uiState.update { it.copy(shareIntent = null) }
    }

    /**
     * Clears a share error once surfaced.
     */
    fun clearShareError() {
        _uiState.update { it.copy(shareError = null) }
    }

    /**
     * Deletes all expenses.
     */
    fun deleteAllExpenses() {
        viewModelScope.launch {
            repository.deleteAllExpenses()
            _uiState.update {
                it.copy(infoMessage = "Deleted all expenses")
            }
        }
    }

    /**
     * Consumes info messages once shown.
     */
    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    private fun buildTextShareIntent(
        expenses: List<ExpenseEntity>,
        rangeLabel: String
    ): Intent {
        val body = buildString {
            appendLine("Expenses ($rangeLabel)")
            appendLine("===================")
            appendLine(CsvExporter.generateSummary(expenses))
            appendLine()
            appendLine("Transactions:")
            expenses.forEach { expense ->
                appendLine("- ${expense.merchant}: ₹%.2f".format(expense.amount) + " [${expense.category}]")
                expense.title?.takeIf { it.isNotBlank() }?.let { appendLine("  Note: $it") }
            }
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Expenses - $rangeLabel")
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }

    private fun buildFileShareIntent(
        uri: android.net.Uri?,
        mimeType: String,
        subject: String
    ): Intent? {
        uri ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun formatRangeLabel(start: Long, end: Long): String {
        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val startStr = formatter.format(start)
        val endForDisplay = (end - 1).coerceAtLeast(start)
        val endStr = formatter.format(endForDisplay)
        return if (startStr == endStr) startStr else "$startStr - $endStr"
    }

    private fun createCurrentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return start to (cal.timeInMillis + 1)
    }

    private fun toCategory(category: String): ExpenseCategory {
        return ExpenseCategory.entries.find { it.displayName == category } ?: ExpenseCategory.OTHER
    }
}
