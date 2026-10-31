package com.expenseinterceptor.ui.scan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expenseinterceptor.domain.model.DateRange
import com.expenseinterceptor.domain.model.DateRanges
import com.expenseinterceptor.domain.usecase.SaveScannedTransactions
import com.expenseinterceptor.domain.usecase.ScanItem
import com.expenseinterceptor.domain.usecase.ScanOutcome
import com.expenseinterceptor.domain.usecase.ScanSmsInbox
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
  val range: DateRange,
  val isScanning: Boolean = false,
  val scanError: String? = null,
  val results: List<ScanItem> = emptyList(),
  val saveInProgress: Boolean = false,
  val saveResult: String? = null,
) {
  /** Messages that look like transactions and are not stored yet. */
  val newCount: Int
    get() = results.count { (it.outcome as? ScanOutcome.Detected)?.alreadySaved == false }

  val detectedCount: Int
    get() = results.count { it.outcome is ScanOutcome.Detected }
}

/** Backfills transactions from the SMS inbox for a chosen date range. */
@HiltViewModel
class ScanViewModel
@Inject
constructor(
  private val ranges: DateRanges,
  private val scanInbox: ScanSmsInbox,
  private val saveScanned: SaveScannedTransactions,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ScanUiState(range = ranges.currentMonth()))
  val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

  fun updateStartDate(dayMillis: Long) =
    _uiState.update {
      it.copy(range = ranges.spanningDays(dayMillis, lastDay(it.range)), results = emptyList())
    }

  fun updateEndDate(dayMillis: Long) =
    _uiState.update {
      it.copy(range = ranges.spanningDays(it.range.startMillis, dayMillis), results = emptyList())
    }

  fun lastDayMillis(): Long = lastDay(_uiState.value.range)

  fun scan() {
    if (_uiState.value.isScanning) return
    _uiState.update { it.copy(saveResult = null) }
    readInbox()
  }

  private fun readInbox() {
    val range = _uiState.value.range
    _uiState.update { it.copy(isScanning = true, scanError = null) }
    viewModelScope.launch {
      try {
        val results = scanInbox(range)
        _uiState.update { it.copy(isScanning = false, results = results) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: SecurityException) {
        Log.w(TAG, "SMS permission missing", e)
        _uiState.update {
          it.copy(isScanning = false, scanError = "Allow SMS access to scan the inbox")
        }
      }
    }
  }

  fun saveDetected() {
    val state = _uiState.value
    if (state.newCount == 0) {
      _uiState.update { it.copy(saveResult = "Nothing new to save") }
      return
    }
    _uiState.update { it.copy(saveInProgress = true, saveResult = null) }
    viewModelScope.launch {
      val saved = saveScanned(state.results)
      val skipped =
        if (saved.skippedDuplicates > 0) ", ${saved.skippedDuplicates} already saved" else ""
      _uiState.update {
        it.copy(saveInProgress = false, saveResult = "Saved ${saved.saved} expenses$skipped")
      }
      readInbox()
    }
  }

  private companion object {
    const val TAG = "ScanViewModel"
  }

  private fun lastDay(range: DateRange): Long =
    maxOf(range.startMillis, range.endExclusiveMillis - 1)
}
