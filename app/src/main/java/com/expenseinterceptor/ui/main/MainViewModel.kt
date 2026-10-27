package com.expenseinterceptor.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expenseinterceptor.data.local.ExpenseEntity
import com.expenseinterceptor.data.repository.ExpenseRepository
import com.expenseinterceptor.ui.quickadd.ExpenseCategory
import com.expenseinterceptor.util.CsvExporter
import com.expenseinterceptor.util.CsvImporter
import com.expenseinterceptor.util.ExcelExporter
import com.expenseinterceptor.util.ExcelImporter
import com.expenseinterceptor.util.PdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the main screen. */
data class MainUiState(
  val startDateMillis: Long,
  val endDateMillis: Long,
  val dateRangeLabel: String,
  val isPreparingShare: Boolean = false,
  val shareError: String? = null,
  val shareIntent: Intent? = null,
  val infoMessage: String? = null,
  val deletedForUndo: ExpenseEntity? = null,
  val isImporting: Boolean = false,
  val importMessage: String? = null
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

enum class DashboardSortBy {
  DATE_DESC,
  DATE_ASC,
  AMOUNT_DESC,
  AMOUNT_ASC,
  CATEGORY,
  MERCHANT
}

enum class DashboardTimeScale {
  DAILY,
  WEEKLY,
  MONTHLY
}

data class DashboardState(
  val selectedCategories: Set<String> = emptySet(),
  val selectedBanks: Set<String> = emptySet(),
  val transactionTypeFilter: String = "ALL",
  val sortBy: DashboardSortBy = DashboardSortBy.DATE_DESC,
  val timeScale: DashboardTimeScale = DashboardTimeScale.DAILY,
  val minAmount: Double? = null,
  val maxAmount: Double? = null,
  val highlightedCategories: Set<String> = emptySet()
)

data class CategoryBreakdown(val category: String, val amount: Double, val percentage: Float)

data class TimeSeriesPoint(val label: String, val timestamp: Long, val amount: Double)

/** ViewModel for the main expense list screen. */
@HiltViewModel
class MainViewModel
@Inject
constructor(
  private val repository: ExpenseRepository,
  @ApplicationContext private val context: Context
) : ViewModel() {

  private val defaultRange: Pair<Long, Long> = createCurrentMonthRange()

  private val _dateRange = MutableStateFlow(defaultRange)

  /** Expenses for the selected date range. */
  val expenses: StateFlow<List<ExpenseEntity>> =
    _dateRange
      .flatMapLatest { (start, end) -> repository.getExpensesForRange(start, end) }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
      )

  /** Total amount for the selected date range. */
  val totalAmount: StateFlow<Double> =
    _dateRange
      .flatMapLatest { (start, end) -> repository.getTotalForRange(start, end) }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
      )

  private val _uiState =
    MutableStateFlow(
      MainUiState(
        startDateMillis = defaultRange.first,
        endDateMillis = defaultRange.second,
        dateRangeLabel = formatRangeLabel(defaultRange.first, defaultRange.second)
      )
    )
  val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

  private val _editState = MutableStateFlow<EditExpenseState?>(null)
  val editState: StateFlow<EditExpenseState?> = _editState.asStateFlow()

  private val _dashboardState = MutableStateFlow(DashboardState())
  val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

  /** Filtered and sorted expenses for the dashboard. */
  val dashboardExpenses: StateFlow<List<ExpenseEntity>> =
    combine(expenses, _dashboardState) { expenseList, dashState ->
        applyDashboardFilters(expenseList, dashState)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
      )

  /** Total amount for filtered dashboard expenses (debits positive, credits negative for net). */
  val dashboardTotalAmount: StateFlow<Double> =
    dashboardExpenses
      .map { list ->
        list.sumOf { e ->
          if (e.transactionType.equals("CREDIT", ignoreCase = true)) -e.amount else e.amount
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
      )

  /** Category breakdown for pie chart (debit expenses only for spending view). */
  val categoryBreakdown: StateFlow<List<CategoryBreakdown>> =
    dashboardExpenses
      .map { list ->
        val debits = list.filter { !it.transactionType.equals("CREDIT", ignoreCase = true) }
        val total = debits.sumOf { it.amount }
        if (total <= 0) return@map emptyList<CategoryBreakdown>()
        debits
          .groupBy { it.category }
          .map { (cat, items) ->
            val amt = items.sumOf { it.amount }
            CategoryBreakdown(cat, amt, (amt / total * 100).toFloat())
          }
          .sortedByDescending { it.amount }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
      )

  /** Time series data for bar/line chart. */
  val timeSeriesData: StateFlow<List<TimeSeriesPoint>> =
    combine(dashboardExpenses, _dashboardState, _dateRange) { list, dashState, (start, end) ->
        buildTimeSeries(list, dashState.timeScale, start, end)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
      )

  /** Unique banks in current expense data. */
  val availableBanks: StateFlow<Set<String>> =
    expenses
      .map { list -> list.map { it.bank }.toSet() }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
      )

  fun setDashboardCategories(selected: Set<String>) {
    _dashboardState.update { it.copy(selectedCategories = selected) }
  }

  fun setDashboardBanks(selected: Set<String>) {
    _dashboardState.update { it.copy(selectedBanks = selected) }
  }

  fun setDashboardTransactionType(type: String) {
    _dashboardState.update { it.copy(transactionTypeFilter = type) }
  }

  fun setDashboardSortBy(sortBy: DashboardSortBy) {
    _dashboardState.update { it.copy(sortBy = sortBy) }
  }

  fun setDashboardTimeScale(scale: DashboardTimeScale) {
    _dashboardState.update { it.copy(timeScale = scale) }
  }

  fun setDashboardAmountRange(min: Double?, max: Double?) {
    _dashboardState.update { it.copy(minAmount = min, maxAmount = max) }
  }

  fun setDashboardHighlightedCategories(categories: Set<String>) {
    _dashboardState.update { it.copy(highlightedCategories = categories) }
  }

  fun toggleDashboardCategory(category: String) {
    _dashboardState.update { state ->
      val next =
        if (category in state.selectedCategories) {
          state.selectedCategories - category
        } else {
          state.selectedCategories + category
        }
      state.copy(selectedCategories = next)
    }
  }

  fun toggleDashboardBank(bank: String) {
    _dashboardState.update { state ->
      val next =
        if (bank in state.selectedBanks) {
          state.selectedBanks - bank
        } else {
          state.selectedBanks + bank
        }
      state.copy(selectedBanks = next)
    }
  }

  fun toggleDashboardHighlight(category: String) {
    _dashboardState.update { state ->
      val next =
        if (category in state.highlightedCategories) {
          state.highlightedCategories - category
        } else {
          state.highlightedCategories + category
        }
      state.copy(highlightedCategories = next)
    }
  }

  private fun applyDashboardFilters(
    list: List<ExpenseEntity>,
    state: DashboardState
  ): List<ExpenseEntity> {
    var result = list
    if (state.selectedCategories.isNotEmpty()) {
      result = result.filter { it.category in state.selectedCategories }
    }
    if (state.selectedBanks.isNotEmpty()) {
      result = result.filter { it.bank in state.selectedBanks }
    }
    when (state.transactionTypeFilter.uppercase()) {
      "DEBIT" -> result = result.filter { !it.transactionType.equals("CREDIT", ignoreCase = true) }
      "CREDIT" -> result = result.filter { it.transactionType.equals("CREDIT", ignoreCase = true) }
    }
    state.minAmount?.let { min -> result = result.filter { it.amount >= min } }
    state.maxAmount?.let { max -> result = result.filter { it.amount <= max } }
    result =
      when (state.sortBy) {
        DashboardSortBy.DATE_DESC -> result.sortedByDescending { it.timestamp }
        DashboardSortBy.DATE_ASC -> result.sortedBy { it.timestamp }
        DashboardSortBy.AMOUNT_DESC -> result.sortedByDescending { it.amount }
        DashboardSortBy.AMOUNT_ASC -> result.sortedBy { it.amount }
        DashboardSortBy.CATEGORY -> result.sortedBy { it.category }
        DashboardSortBy.MERCHANT -> result.sortedBy { it.merchant }
      }
    return result
  }

  private fun buildTimeSeries(
    list: List<ExpenseEntity>,
    scale: DashboardTimeScale,
    startMillis: Long,
    endMillis: Long
  ): List<TimeSeriesPoint> {
    val debits = list.filter { !it.transactionType.equals("CREDIT", ignoreCase = true) }
    if (debits.isEmpty()) return emptyList()
    val formatter =
      SimpleDateFormat(
        when (scale) {
          DashboardTimeScale.DAILY -> "dd MMM"
          DashboardTimeScale.WEEKLY -> "'W'w"
          DashboardTimeScale.MONTHLY -> "MMM yyyy"
        },
        Locale.getDefault()
      )
    val cal = Calendar.getInstance()
    val grouped =
      debits.groupBy { e ->
        cal.timeInMillis = e.timestamp
        when (scale) {
          DashboardTimeScale.DAILY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
          }
          DashboardTimeScale.WEEKLY -> {
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
          }
          DashboardTimeScale.MONTHLY -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
          }
        }
      }
    return grouped
      .map { (ts, items) ->
        TimeSeriesPoint(formatter.format(Date(ts)), ts, items.sumOf { it.amount })
      }
      .sortedBy { it.timestamp }
  }

  /** Returns the current range label for display. */
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
      current.copy(startDateMillis = start, endDateMillis = safeEnd, dateRangeLabel = label)
    }
  }

  fun startEditing(expense: ExpenseEntity) {
    _editState.value =
      EditExpenseState(
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
    _editState.update { current -> current?.copy(category = category, error = null) }
  }

  fun updateEditBank(bank: String) {
    _editState.update { current -> current?.copy(bank = bank, error = null) }
  }

  fun updateEditTransactionType(type: String) {
    _editState.update { current -> current?.copy(transactionType = type, error = null) }
  }

  fun saveEditedExpense() {
    val state = _editState.value ?: return
    if (state.isSaving) return
    _editState.value = state.copy(isSaving = true, error = null)

    viewModelScope.launch {
      try {
        val updated =
          state.original.copy(
            category = state.category.displayName,
            bank = state.bank.ifBlank { "Unknown Bank" },
            transactionType = state.transactionType.ifBlank { "DEBIT" }
          )
        repository.updateExpense(updated)
        _editState.value = null
        _uiState.update { it.copy(infoMessage = "Expense updated") }
      } catch (e: Exception) {
        _editState.update { it?.copy(isSaving = false, error = "Failed to update: ${e.message}") }
      }
    }
  }

  /** Deletes an expense from the database and stores it for undo. */
  fun deleteExpense(expense: ExpenseEntity) {
    viewModelScope.launch {
      _uiState.update { it.copy(deletedForUndo = expense) }
      repository.deleteExpense(expense.id)
    }
  }

  /** Restores the last deleted expense (called when user taps Undo). */
  fun restoreDeletedExpense() {
    val expense = _uiState.value.deletedForUndo ?: return
    viewModelScope.launch {
      repository.insertExpense(expense.copy(id = 0))
      _uiState.update { it.copy(deletedForUndo = null, infoMessage = "Transaction restored") }
    }
  }

  /** Clears the pending undo state (called when snackbar is dismissed without Undo). */
  fun clearDeletedForUndo() {
    _uiState.update { it.copy(deletedForUndo = null) }
  }

  /** Prepares a share intent for the requested format. */
  fun prepareShare(format: ShareFormat) {
    _uiState.update { it.copy(isPreparingShare = true, shareError = null, shareIntent = null) }

    viewModelScope.launch {
      try {
        val (start, end) = _dateRange.value
        val expensesForRange = repository.getExpensesForRangeList(start, end)
        if (expensesForRange.isEmpty()) {
          _uiState.update {
            it.copy(isPreparingShare = false, shareError = "No expenses in the selected range")
          }
          return@launch
        }

        val rangeLabel = formatRangeLabel(start, end)
        val intent =
          when (format) {
            ShareFormat.TEXT -> buildTextShareIntent(expensesForRange, rangeLabel)
            ShareFormat.CSV ->
              buildFileShareIntent(
                uri = CsvExporter.exportToCsv(context, expensesForRange, rangeLabel),
                mimeType = "text/csv",
                subject = "Expenses - $rangeLabel"
              )
            ShareFormat.EXCEL ->
              buildFileShareIntent(
                uri = ExcelExporter.exportToExcel(context, expensesForRange, rangeLabel),
                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                subject = "Expenses - $rangeLabel"
              )
            ShareFormat.PDF ->
              buildFileShareIntent(
                uri = PdfExporter.exportToPdf(context, expensesForRange, rangeLabel),
                mimeType = "application/pdf",
                subject = "Expenses - $rangeLabel"
              )
          }

        if (intent == null) {
          _uiState.update {
            it.copy(isPreparingShare = false, shareError = "Failed to create share content")
          }
        } else {
          _uiState.update { it.copy(isPreparingShare = false, shareIntent = intent) }
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(isPreparingShare = false, shareError = "Share failed: ${e.message}")
        }
      }
    }
  }

  /** Consumes a pending share intent after the UI launches it. */
  fun consumeShareIntent() {
    _uiState.update { it.copy(shareIntent = null) }
  }

  /** Clears a share error once surfaced. */
  fun clearShareError() {
    _uiState.update { it.copy(shareError = null) }
  }

  /** Deletes all expenses. */
  fun deleteAllExpenses() {
    viewModelScope.launch {
      repository.deleteAllExpenses()
      _uiState.update { it.copy(infoMessage = "Deleted all expenses") }
    }
  }

  /** Consumes info messages once shown. */
  fun clearInfoMessage() {
    _uiState.update { it.copy(infoMessage = null) }
  }

  /** Imports expenses from a CSV or Excel file at the given URI. */
  fun importFromUri(uri: Uri) {
    _uiState.update { it.copy(isImporting = true, importMessage = null) }

    viewModelScope.launch {
      try {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val uriString = uri.toString().lowercase()
        val isExcel =
          mimeType.contains("spreadsheet") ||
            mimeType.contains("excel") ||
            uriString.endsWith(".xlsx") ||
            uriString.contains(".xlsx")

        val result =
          if (isExcel) {
            ExcelImporter.importFromExcel(context, uri)
          } else {
            CsvImporter.importFromCsv(context, uri)
          }

        result.fold(
          onSuccess = { importResult ->
            when {
              importResult.expenses.isEmpty() -> {
                val errorMsg = importResult.parseErrors.firstOrNull() ?: "No transactions to import"
                _uiState.update { it.copy(isImporting = false, importMessage = errorMsg) }
              }
              else -> {
                repository.insertExpenses(importResult.expenses)
                val successMsg =
                  if (importResult.parseErrors.isEmpty()) {
                    "Imported ${importResult.expenses.size} transactions"
                  } else {
                    "Imported ${importResult.expenses.size} transactions. ${importResult.parseErrors.size} rows had errors."
                  }
                _uiState.update { it.copy(isImporting = false, importMessage = successMsg) }
              }
            }
          },
          onFailure = { e ->
            _uiState.update {
              it.copy(isImporting = false, importMessage = "Import failed: ${e.message}")
            }
          }
        )
      } catch (e: Exception) {
        _uiState.update {
          it.copy(isImporting = false, importMessage = "Import failed: ${e.message}")
        }
      }
    }
  }

  /** Clears the import message after it has been shown. */
  fun clearImportMessage() {
    _uiState.update { it.copy(importMessage = null) }
  }

  private fun buildTextShareIntent(expenses: List<ExpenseEntity>, rangeLabel: String): Intent {
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
    val cal =
      Calendar.getInstance().apply {
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
