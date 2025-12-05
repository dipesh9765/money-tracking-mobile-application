package com.expenseinterceptor.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.expenseinterceptor.data.local.ExpenseEntity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility object for exporting expenses to CSV format.
 */
object CsvExporter {

    private const val CSV_HEADER = "Date,Time,Amount,Category,Merchant,Title"
    private const val DATE_FORMAT = "yyyy-MM-dd"
    private const val TIME_FORMAT = "HH:mm:ss"
    private const val FILE_NAME_FORMAT = "expenses_%s.csv"

    /**
     * Exports a list of expenses to a CSV file.
     * 
     * @param context Application context
     * @param expenses List of expenses to export
     * @param monthName Name of the month for the file name
     * @return URI of the created CSV file, or null if export failed
     */
    fun exportToCsv(
        context: Context,
        expenses: List<ExpenseEntity>,
        monthName: String
    ): Uri? {
        return try {
            // Create exports directory in cache
            val exportDir = File(context.cacheDir, "exports").apply {
                if (!exists()) mkdirs()
            }

            // Create file with formatted name
            val sanitizedMonthName = monthName.replace(" ", "_").lowercase()
            val fileName = String.format(FILE_NAME_FORMAT, sanitizedMonthName)
            val csvFile = File(exportDir, fileName)

            // Write CSV content
            FileWriter(csvFile).use { writer ->
                // Write header
                writer.appendLine(CSV_HEADER)

                // Write each expense
                val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
                val timeFormat = SimpleDateFormat(TIME_FORMAT, Locale.getDefault())

                expenses.forEach { expense ->
                    val date = Date(expense.timestamp)
                    val line = buildCsvLine(
                        dateFormat.format(date),
                        timeFormat.format(date),
                        expense.amount.toString(),
                        escapeCsvField(expense.category),
                        escapeCsvField(expense.merchant),
                        escapeCsvField(expense.title ?: "")
                    )
                    writer.appendLine(line)
                }
            }

            // Return FileProvider URI for sharing
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Builds a CSV line from individual fields.
     */
    private fun buildCsvLine(vararg fields: String): String {
        return fields.joinToString(",")
    }

    /**
     * Escapes a field for CSV format.
     * Wraps in quotes if contains comma, quote, or newline.
     * Doubles any internal quotes.
     */
    private fun escapeCsvField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }

    /**
     * Generates a summary of the export.
     */
    fun generateSummary(expenses: List<ExpenseEntity>): String {
        val total = expenses.sumOf { it.amount }
        val byCategory = expenses.groupBy { it.category }
            .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        return buildString {
            appendLine("Expense Summary")
            appendLine("===============")
            appendLine("Total: ₹%.2f".format(total))
            appendLine("Transactions: ${expenses.size}")
            appendLine()
            appendLine("By Category:")
            byCategory.forEach { (category, amount) ->
                appendLine("  $category: ₹%.2f".format(amount))
            }
        }
    }
}

