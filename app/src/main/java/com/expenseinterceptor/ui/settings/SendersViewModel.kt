package com.expenseinterceptor.ui.settings

import androidx.lifecycle.ViewModel
import com.expenseinterceptor.domain.port.SenderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SendersUiState(val input: String = "", val message: String? = null)

/** Edits which SMS sender ids are treated as banks, without rebuilding the app. */
@HiltViewModel
class SendersViewModel @Inject constructor(private val settings: SenderSettings) : ViewModel() {

  val senders: StateFlow<List<String>> = settings.senders

  private val _state = MutableStateFlow(SendersUiState())
  val state: StateFlow<SendersUiState> = _state.asStateFlow()

  fun updateInput(text: String) = _state.update { it.copy(input = text, message = null) }

  fun add() {
    val pattern = _state.value.input
    when {
      pattern.isBlank() -> _state.update { it.copy(message = "Enter a sender id such as HDFCBK") }
      settings.add(pattern) -> _state.update { SendersUiState() }
      else -> _state.update { it.copy(message = "Already in the list") }
    }
  }

  fun remove(pattern: String) = settings.remove(pattern)

  fun resetToDefaults() = settings.resetToDefaults()
}
