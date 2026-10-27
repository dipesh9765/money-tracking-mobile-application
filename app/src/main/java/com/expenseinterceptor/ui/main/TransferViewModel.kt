package com.expenseinterceptor.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expenseinterceptor.data.transfer.ExpenseExporter
import com.expenseinterceptor.data.transfer.ExpenseImporter
import com.expenseinterceptor.data.transfer.ExportFormat
import com.expenseinterceptor.data.transfer.ExportResult
import com.expenseinterceptor.data.transfer.ImportSource
import com.expenseinterceptor.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ooxml.POIXMLException
import org.apache.poi.openxml4j.exceptions.OpenXML4JException

data class TransferState(val isPreparingShare: Boolean = false, val isImporting: Boolean = false)

sealed interface TransferEvent {
  data class Message(val text: String) : TransferEvent

  /** An export is ready to hand to the system share sheet. */
  data class Share(val result: ExportResult) : TransferEvent
}

/** Exporting the selected range and importing CSV/Excel files. */
@HiltViewModel
class TransferViewModel
@Inject
constructor(
  private val repository: ExpenseRepository,
  private val period: ReportingPeriod,
  private val exporter: ExpenseExporter,
  private val importer: ExpenseImporter,
  private val source: ImportSource,
) : ViewModel() {

  private val _state = MutableStateFlow(TransferState())
  val state: StateFlow<TransferState> = _state.asStateFlow()

  private val _events = Channel<TransferEvent>(Channel.BUFFERED)
  val events: Flow<TransferEvent> = _events.receiveAsFlow()

  fun export(format: ExportFormat) {
    if (_state.value.isPreparingShare) return
    _state.update { it.copy(isPreparingShare = true) }
    viewModelScope.launch {
      try {
        val range = period.range.value
        val expenses = repository.list(range)
        if (expenses.isEmpty()) {
          _events.send(TransferEvent.Message("No expenses in the selected range"))
        } else {
          _events.send(TransferEvent.Share(exporter.export(format, expenses, period.label(range))))
        }
      } catch (e: IOException) {
        _events.send(TransferEvent.Message("Export failed: ${e.message}"))
      } finally {
        _state.update { it.copy(isPreparingShare = false) }
      }
    }
  }

  fun import(uri: String) {
    if (_state.value.isImporting) return
    _state.update { it.copy(isImporting = true) }
    viewModelScope.launch {
      val message =
        try {
          withContext(Dispatchers.IO) { source.open(uri) }
            .let { file -> file.stream.use { importer.import(it, file.kind).message } }
        } catch (e: CancellationException) {
          throw e
        } catch (e: IOException) {
          importFailed(e)
        } catch (e: IllegalArgumentException) {
          importFailed(e)
        } catch (e: OpenXML4JException) {
          importFailed(e)
        } catch (e: POIXMLException) {
          importFailed(e)
        }
      _events.send(TransferEvent.Message(message))
      _state.update { it.copy(isImporting = false) }
    }
  }

  /** The file content is never logged; only the exception type and message are. */
  private fun importFailed(e: Exception): String {
    Log.w(TAG, "Import failed", e)
    return "Import failed: ${e.message ?: "unreadable file"}"
  }

  private companion object {
    const val TAG = "TransferViewModel"
  }
}
