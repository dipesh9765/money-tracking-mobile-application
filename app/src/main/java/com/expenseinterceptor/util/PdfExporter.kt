package com.expenseinterceptor.util

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.expenseinterceptor.data.local.ExpenseEntity
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports expenses into a lightweight PDF (text layout) for sharing.
 */
object PdfExporter {

    private const val FILE_NAME_FORMAT = "expenses_%s.pdf"
    private const val PAGE_WIDTH = 595 // A4 width in points (approx 8.3in * 72)
    private const val PAGE_HEIGHT = 842 // A4 height in points (approx 11.7in * 72)
    private const val MARGIN = 36

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    fun exportToPdf(
        context: Context,
        expenses: List<ExpenseEntity>,
        rangeLabel: String
    ): Uri? {
        if (expenses.isEmpty()) return null

        val exportDir = File(context.cacheDir, "exports").apply {
            if (!exists()) mkdirs()
        }
        val fileName = String.format(FILE_NAME_FORMAT, sanitize(rangeLabel))
        val pdfFile = File(exportDir, fileName)

        return try {
            val document = PdfDocument()
            val paint = Paint().apply {
                isAntiAlias = true
                textSize = 12f
            }
            val titlePaint = Paint(paint).apply { textSize = 16f }

            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            var y = MARGIN + 10f

            fun ensureSpace(linesNeeded: Int = 1) {
                val lineHeight = paint.textSize + 6
                if (y + lineHeight * linesNeeded > PAGE_HEIGHT - MARGIN) {
                    document.finishPage(page)
                    pageNumber += 1
                    page = document.startPage(
                        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    )
                    y = MARGIN + 10f
                }
            }

            fun drawLine(text: String, useTitle: Boolean = false) {
                val activePaint = if (useTitle) titlePaint else paint
                ensureSpace()
                page.canvas.drawText(text, MARGIN.toFloat(), y, activePaint)
                y += activePaint.textSize + 6
            }

            drawLine("Expenses ($rangeLabel)", useTitle = true)
            val total = expenses.sumOf { it.amount }
            drawLine("Total: ${currencyFormat.format(total)}")
            drawLine("Transactions: ${expenses.size}")
            drawLine(" ")

            expenses.forEach { expense ->
                val date = Date(expense.timestamp)
                val header = "${dateFormat.format(date)} ${timeFormat.format(date)} - ${expense.merchant}"
                drawLine(header, useTitle = false)
                drawLine("Category: ${expense.category}   Amount: ${currencyFormat.format(expense.amount)}")
                expense.title?.takeIf { it.isNotBlank() }?.let { drawLine("Note: $it") }
                drawLine("------------------------------")
            }

            document.finishPage(page)
            FileOutputStream(pdfFile).use { output ->
                document.writeTo(output)
            }
            document.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun sanitize(label: String): String {
        return label.lowercase(Locale.getDefault())
            .replace(" ", "_")
            .replace("/", "_")
    }
}

