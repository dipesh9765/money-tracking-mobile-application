package com.expenseinterceptor.util

import android.content.Context
import android.net.Uri
import com.expenseinterceptor.data.local.ExpenseEntity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Result of an import operation.
 */
data class ImportResult(
    val expenses: List<ExpenseEntity>,
    val parseErrors: List<String>
)

/**
 * Utility object for importing expenses from CSV format.
 * Supports the format produced by CsvExporter.
 */
object CsvImporter {

    private const val EXPECTED_HEADER = "Date,Time,Amount,Category,Merchant,Title"
    private const val DATE_FORMAT = "yyyy-MM-dd"
    private const val TIME_FORMAT = "HH:mm:ss"
    private val TIME_FORMAT_SHORT = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val DATE_TIME_FORMAT = SimpleDateFormat("$DATE_FORMAT $TIME_FORMAT", Locale.getDefault())

    /**
     * Imports expenses from a CSV file at the given URI.
     *
     * @param context Application context
     * @param uri URI of the CSV file (e.g., from file picker)
     * @return Result containing parsed expenses and any parse errors, or failure on fatal errors
     */
    fun importFromCsv(context: Context, uri: Uri): Result<ImportResult> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                parseCsv(reader)
            } ?: Result.failure(Exception("Could not open file"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseCsv(reader: BufferedReader): Result<ImportResult> {
        val expenses = mutableListOf<ExpenseEntity>()
        val parseErrors = mutableListOf<String>()

        val headerLine = reader.readLine() ?: return Result.success(
            ImportResult(emptyList(), listOf("File is empty"))
        )

        val headerColumns = headerLine.split(",").map { it.trim().lowercase() }
        val expectedColumns = listOf("date", "time", "amount", "category", "merchant", "title")
        if (headerColumns.size < 6 || !headerColumns.take(6).equals(expectedColumns)) {
            return Result.success(
                ImportResult(emptyList(), listOf("Invalid header. Expected: $EXPECTED_HEADER"))
            )
        }

        val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val timeFormat = SimpleDateFormat(TIME_FORMAT, Locale.getDefault())

        var lineNumber = 1
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            lineNumber++
            val trimmed = line!!.trim()
            if (trimmed.isEmpty()) continue

            val fields = parseCsvLine(trimmed)
            if (fields.size < 6) {
                parseErrors.add("Row $lineNumber: Expected 6 columns, got ${fields.size}")
                continue
            }

            val dateStr = fields[0]
            val timeStr = fields[1]
            val amountStr = fields[2]
            val category = fields[3]
            val merchant = fields[4]
            val title = fields[5]

            val timestamp = parseTimestamp(dateStr, timeStr, dateFormat, timeFormat)
            if (timestamp == null) {
                parseErrors.add("Row $lineNumber: Invalid date/time '$dateStr' '$timeStr'")
                continue
            }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount < 0) {
                parseErrors.add("Row $lineNumber: Invalid amount '$amountStr'")
                continue
            }

            val expense = ExpenseEntity(
                amount = amount,
                merchant = merchant.ifBlank { "Unknown" },
                category = category.ifBlank { "Other" },
                title = title.trim().takeIf { it.isNotBlank() },
                timestamp = timestamp,
                originalSms = null,
                bank = "Unknown Bank",
                transactionType = "DEBIT"
            )
            expenses.add(expense)
        }

        return Result.success(ImportResult(expenses, parseErrors))
    }

    private fun parseTimestamp(
        dateStr: String,
        timeStr: String,
        dateFormat: SimpleDateFormat,
        timeFormat: SimpleDateFormat
    ): Long? {
        return try {
            val date = dateFormat.parse(dateStr.trim()) ?: return null
            val time = timeFormat.parse(timeStr.trim())
                ?: TIME_FORMAT_SHORT.parse(timeStr.trim())
            val cal = java.util.Calendar.getInstance()
            cal.time = date
            val timeCal = java.util.Calendar.getInstance()
            timeCal.time = time ?: return null
            cal.set(java.util.Calendar.HOUR_OF_DAY, timeCal.get(java.util.Calendar.HOUR_OF_DAY))
            cal.set(java.util.Calendar.MINUTE, timeCal.get(java.util.Calendar.MINUTE))
            cal.set(java.util.Calendar.SECOND, timeCal.get(java.util.Calendar.SECOND))
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses a CSV line, handling quoted fields and escaped quotes.
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        val sb = StringBuilder()

        while (i < line.length) {
            when (val c = line[i]) {
                '"' -> {
                    sb.clear()
                    i++
                    while (i < line.length) {
                        when (val ch = line[i]) {
                            '"' -> {
                                if (i + 1 < line.length && line[i + 1] == '"') {
                                    sb.append('"')
                                    i += 2
                                } else {
                                    i++
                                    break
                                }
                            }
                            else -> {
                                sb.append(ch)
                                i++
                            }
                        }
                    }
                    result.add(sb.toString())
                    sb.clear()
                }
                ',' -> {
                    result.add(sb.toString())
                    sb.clear()
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        result.add(sb.toString())
        return result
    }
}
