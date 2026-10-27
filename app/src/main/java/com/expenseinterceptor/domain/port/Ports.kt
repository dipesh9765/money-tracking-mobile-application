package com.expenseinterceptor.domain.port

import com.expenseinterceptor.domain.model.DateRange
import com.expenseinterceptor.domain.model.Expense
import com.expenseinterceptor.domain.sms.ParsedTransaction
import java.io.OutputStream
import kotlinx.coroutines.flow.StateFlow

/** One raw message as stored on the phone. */
data class RawSms(val sender: String, val body: String, val timestamp: Long)

/** Reads the device SMS inbox. Needs the READ_SMS permission. */
interface SmsInbox {
  suspend fun read(range: DateRange): List<RawSms>
}

/** What the user is told when a bank SMS is recognised. */
data class DetectedTransaction(
  val transaction: ParsedTransaction,
  val originalSms: String,
  val timestamp: Long,
)

interface ExpenseNotifier {
  fun notifyDetected(detected: DetectedTransaction)
}

/** The bank sender ids the user wants intercepted. */
interface SenderSettings {
  val senders: StateFlow<List<String>>

  /** Adds [pattern] unless blank or already present. Returns true if it was added. */
  fun add(pattern: String): Boolean

  fun remove(pattern: String)

  fun resetToDefaults()
}

/** Where generated export files go before they are shared. */
interface ExportFileStore {
  /** Writes a file under [fileName] and returns a shareable content URI as text. */
  suspend fun write(fileName: String, writer: (OutputStream) -> Unit): String
}

/** Renders the PDF report. Android-only, so kept behind a port. */
interface PdfReportWriter {
  fun write(expenses: List<Expense>, rangeLabel: String, output: OutputStream)
}
