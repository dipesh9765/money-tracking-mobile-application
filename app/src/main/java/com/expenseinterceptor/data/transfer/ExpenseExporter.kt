package com.expenseinterceptor.data.transfer

import com.expenseinterceptor.domain.model.Expense
import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.port.ExportFileStore
import com.expenseinterceptor.domain.port.PdfReportWriter
import com.expenseinterceptor.domain.usecase.ExpenseSummary
import javax.inject.Inject

enum class ExportFormat(val extension: String, val mimeType: String) {
  TEXT("txt", "text/plain"),
  CSV("csv", "text/csv"),
  EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
  PDF("pdf", "application/pdf"),
}

/** What the user can share after an export. */
sealed interface ExportResult {
  val subject: String

  data class Text(override val subject: String, val body: String) : ExportResult

  data class File(override val subject: String, val uri: String, val mimeType: String) :
    ExportResult
}

/** Turns a list of expenses into something shareable in the requested [ExportFormat]. */
class ExpenseExporter
@Inject
constructor(
  private val csv: CsvCodec,
  private val excel: ExcelCodec,
  private val pdf: PdfReportWriter,
  private val store: ExportFileStore,
) {

  suspend fun export(
    format: ExportFormat,
    expenses: List<Expense>,
    rangeLabel: String,
  ): ExportResult {
    val subject = "Expenses - $rangeLabel"
    if (format == ExportFormat.TEXT)
      return ExportResult.Text(subject, textReport(expenses, rangeLabel))
    val fileName = "expenses_${slug(rangeLabel)}.${format.extension}"
    val uri =
      store.write(fileName) { out ->
        when (format) {
          ExportFormat.CSV -> csv.write(expenses, out)
          ExportFormat.EXCEL -> excel.write(expenses, out)
          ExportFormat.PDF -> pdf.write(expenses, rangeLabel, out)
          ExportFormat.TEXT -> error("text is not a file format")
        }
      }
    return ExportResult.File(subject, uri, format.mimeType)
  }

  fun textReport(expenses: List<Expense>, rangeLabel: String): String {
    val summary = ExpenseSummary.of(expenses)
    return buildString {
      appendLine("Expenses ($rangeLabel)")
      appendLine("===================")
      appendLine("Spent: ${summary.spent.format()}")
      if (summary.received > Money.ZERO) appendLine("Received: ${summary.received.format()}")
      appendLine("Transactions: ${summary.count}")
      appendLine()
      appendLine("By category (spent):")
      expenses
        .filter { !it.isCredit }
        .groupBy { it.category }
        .mapValues { (_, items) -> items.fold(Money.ZERO) { sum, e -> sum + e.amount } }
        .entries
        .sortedByDescending { it.value }
        .forEach { (category, amount) -> appendLine("  $category: ${amount.format()}") }
      appendLine()
      appendLine("Transactions:")
      expenses.forEach { e ->
        val sign = if (e.isCredit) "+" else "-"
        appendLine("- ${e.merchant}: $sign${e.amount.format()} [${e.category}]")
        e.title?.takeIf { it.isNotBlank() }?.let { appendLine("  Note: $it") }
      }
    }
  }

  private fun slug(label: String): String =
    label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "export" }
}
