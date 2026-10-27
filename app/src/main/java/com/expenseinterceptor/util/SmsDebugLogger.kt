package com.expenseinterceptor.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory debug logger for SMS processing. Keeps recent log entries to aid troubleshooting. */
object SmsDebugLogger {

  private const val MAX_ENTRIES = 200
  private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

  data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String,
    val preview: String,
    val stage: String,
    val outcome: String,
    val details: String? = null
  ) {
    val formattedTime: String
      get() = dateFormat.format(Date(timestamp))
  }

  private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
  val logs: StateFlow<List<LogEntry>> = _logs

  @Synchronized
  fun log(
    sender: String,
    preview: String,
    stage: String,
    outcome: String,
    details: String? = null
  ) {
    val trimmedPreview = preview.take(180)
    val entry =
      LogEntry(
        sender = sender,
        preview = trimmedPreview,
        stage = stage,
        outcome = outcome,
        details = details
      )
    val updated = (_logs.value + entry).takeLast(MAX_ENTRIES)
    _logs.value = updated
  }

  @Synchronized
  fun clear() {
    _logs.value = emptyList()
  }
}
