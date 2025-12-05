package com.expenseinterceptor.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.expenseinterceptor.data.local.ExpenseEntity
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelExporter {

    private const val FILE_NAME_FORMAT = "expenses_%s.xlsx"

    fun exportToExcel(
        context: Context,
        expenses: List<ExpenseEntity>,
        monthName: String
    ): Uri? {
        return try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Expenses")

            val headerStyle = createHeaderStyle(workbook)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            val headers = listOf("Date", "Time", "Amount", "Category", "Merchant", "Title")
            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { index, title ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(title)
                cell.cellStyle = headerStyle
            }

            expenses.forEachIndexed { idx, expense ->
                val row = sheet.createRow(idx + 1)
                val date = Date(expense.timestamp)
                row.createCell(0).setCellValue(dateFormat.format(date))
                row.createCell(1).setCellValue(timeFormat.format(date))
                row.createCell(2).setCellValue(expense.amount)
                row.createCell(3).setCellValue(expense.category)
                row.createCell(4).setCellValue(expense.merchant)
                row.createCell(5).setCellValue(expense.title ?: "")
            }

            headers.indices.forEach { sheet.autoSizeColumn(it) }

            val exportDir = File(context.cacheDir, "exports").apply {
                if (!exists()) mkdirs()
            }
            val sanitizedMonthName = monthName.replace(" ", "_").lowercase()
            val fileName = String.format(FILE_NAME_FORMAT, sanitizedMonthName)
            val excelFile = File(exportDir, fileName)

            FileOutputStream(excelFile).use { output ->
                workbook.write(output)
            }
            workbook.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                excelFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createHeaderStyle(workbook: XSSFWorkbook): XSSFCellStyle {
        val style = workbook.createCellStyle()
        style.fillForegroundColor = IndexedColors.LIGHT_CORNFLOWER_BLUE.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER

        val font: XSSFFont = workbook.createFont()
        font.bold = true
        style.setFont(font)
        return style
    }
}

