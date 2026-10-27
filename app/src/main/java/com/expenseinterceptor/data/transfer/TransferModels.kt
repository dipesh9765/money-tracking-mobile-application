package com.expenseinterceptor.data.transfer

import com.expenseinterceptor.domain.model.Expense

/** Rows read from a file, plus a message for each row that could not be used. */
data class ImportParse(val expenses: List<Expense>, val errors: List<String>)

object TransferFormat {
  val HEADERS = listOf("Date", "Time", "Amount", "Category", "Merchant", "Title", "Bank", "Type")

  /** Files written before bank and type were exported only have these columns. */
  const val LEGACY_COLUMNS = 6
  const val MAX_ROWS = 50_000

  /** Column positions, shared by the CSV and Excel readers and writers. */
  object Col {
    const val DATE = 0
    const val TIME = 1
    const val AMOUNT = 2
    const val CATEGORY = 3
    const val MERCHANT = 4
    const val TITLE = 5
    const val BANK = 6
    const val TYPE = 7
  }

  const val MAX_FIELD_LENGTH = 2_000
}
