package com.expenseinterceptor.data.transfer

import com.expenseinterceptor.domain.model.Expense
import com.expenseinterceptor.domain.model.ExpenseCategory
import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** One spreadsheet row as text, whatever file format it came from. */
data class RawRow(
  val date: String,
  val time: String,
  val amount: String,
  val category: String,
  val merchant: String,
  val title: String,
  val bank: String = "",
  val type: String = "",
)

/**
 * Turns a [RawRow] into an [Expense], or says why it cannot. Shared by the CSV and Excel readers.
 */
class ImportRowParser(private val zone: ZoneId) {

  sealed interface Result {
    data class Ok(val expense: Expense) : Result

    data class Failed(val message: String) : Result
  }

  fun parse(rowNumber: Int, row: RawRow): Result {
    val date =
      try {
        LocalDate.parse(row.date.trim())
      } catch (_: DateTimeParseException) {
        return Result.Failed("Row $rowNumber: invalid date '${row.date.take(SHOWN)}'")
      }
    val time =
      try {
        if (row.time.isBlank()) LocalTime.MIDNIGHT else LocalTime.parse(row.time.trim())
      } catch (_: DateTimeParseException) {
        return Result.Failed("Row $rowNumber: invalid time '${row.time.take(SHOWN)}'")
      }
    val amount =
      Money.parse(row.amount)
        ?: return Result.Failed("Row $rowNumber: invalid amount '${row.amount.take(SHOWN)}'")

    val timestamp = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    return Result.Ok(
      Expense(
        amount = amount,
        merchant = clean(row.merchant).ifBlank { "Unknown" },
        category = clean(row.category).ifBlank { ExpenseCategory.OTHER.displayName },
        timestamp = timestamp,
        type = TransactionType.fromText(row.type.trim()),
        bank = clean(row.bank).ifBlank { Expense.UNKNOWN_BANK },
        title = clean(row.title).ifBlank { null },
      )
    )
  }

  private fun clean(text: String): String =
    SpreadsheetSafety.restore(text.trim()).take(TransferFormat.MAX_FIELD_LENGTH)

  private companion object {
    const val SHOWN = 20
  }
}
