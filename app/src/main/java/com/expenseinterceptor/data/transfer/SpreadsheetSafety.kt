package com.expenseinterceptor.data.transfer

/**
 * Protection against spreadsheet formula injection ("CSV injection").
 *
 * The merchant text comes from an arbitrary SMS sender. If it starts with `=`, `+`, `-` or `@`,
 * Excel and Sheets run it as a formula when the exported file is opened. Prefixing a single quote
 * makes the cell plain text. [restore] undoes this on import so an export/import round trip is
 * lossless.
 */
object SpreadsheetSafety {
  private val TRIGGERS = setOf('=', '+', '-', '@', '\t', '\r')
  private const val GUARD = '\''

  fun neutralize(text: String): String =
    if (text.isNotEmpty() && text.first() in TRIGGERS) GUARD + text else text

  fun restore(text: String): String =
    if (text.length > 1 && text.first() == GUARD && text[1] in TRIGGERS) text.substring(1) else text
}
