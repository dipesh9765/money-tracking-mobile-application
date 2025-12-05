package com.expenseinterceptor.ui.scan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expenseinterceptor.data.repository.ExpenseRepository
import com.expenseinterceptor.util.SmsScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class ScanUiState(
    val startDateMillis: Long,
    val endDateMillis: Long,
    val isScanning: Boolean = false,
    val scanError: String? = null,
    val results: List<SmsScanner.ScannedSms> = emptyList(),
    val saveInProgress: Boolean = false,
    val saveResult: String? = null
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExpenseRepository
) : ViewModel() {

    private val defaultRange: Pair<Long, Long> = createCurrentMonthRange()

    private val _uiState = MutableStateFlow(
        ScanUiState(
            startDateMillis = defaultRange.first,
            endDateMillis = defaultRange.second
        )
    )
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun updateStartDate(millis: Long) {
        val end = _uiState.value.endDateMillis
        _uiState.value = _uiState.value.copy(
            startDateMillis = millis,
            endDateMillis = maxOf(millis, end)
        )
    }

    fun updateEndDate(millis: Long) {
        val start = _uiState.value.startDateMillis
        _uiState.value = _uiState.value.copy(
            endDateMillis = maxOf(millis, start)
        )
    }

    fun scan() {
        val start = _uiState.value.startDateMillis
        val end = _uiState.value.endDateMillis
        _uiState.value = _uiState.value.copy(
            isScanning = true,
            scanError = null,
            saveResult = null
        )

        viewModelScope.launch {
            try {
                val result = SmsScanner.scanRange(context, start, end)
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    results = result
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanError = e.message ?: "Scan failed"
                )
            }
        }
    }

    fun saveDetected() {
        val items = _uiState.value.results
            .mapNotNull { it.toExpenseEntity() }
        if (items.isEmpty()) {
            _uiState.value = _uiState.value.copy(saveResult = "Nothing to save")
            return
        }

        _uiState.value = _uiState.value.copy(saveInProgress = true, saveResult = null)

        viewModelScope.launch {
            try {
                items.forEach { repository.insertExpense(it) }
                _uiState.value = _uiState.value.copy(
                    saveInProgress = false,
                    saveResult = "Saved ${items.size} expenses"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    saveInProgress = false,
                    saveResult = "Save failed: ${e.message}"
                )
            }
        }
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
        return start to cal.timeInMillis
    }
}

