package com.expenseinterceptor.util

import android.content.Context
import android.net.Uri
import com.expenseinterceptor.data.local.ExpenseEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook

/**
 * Utility object for importing expenses from Excel (.xlsx) format. Supports the format produced by
 * ExcelExporter.
 */
object ExcelImporter {

  private const val DATE_FORMAT = "yyyy-MM-dd"
  private const val TIME_FORMAT = "HH:mm:ss"
  private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
  private val timeFormat = SimpleDateFormat(TIME_FORMAT, Locale.getDefault())

  /**
   * Imports expenses from an Excel file at the given URI.
   *
   * @param context Application context
   * @param uri URI of the Excel file (e.g., from file picker)
   * @return Result containing parsed expenses and any parse errors, or failure on fatal errors
   */
  fun importFromExcel(context: Context, uri: Uri): Result<ImportResult> {
    return try {
      context.contentResolver.openInputStream(uri)?.use { inputStream ->
        val workbook = XSSFWorkbook(inputStream)
        try {
          parseExcel(workbook)
        } finally {
          workbook.close()
        }
      } ?: Result.failure(Exception("Could not open file"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun parseExcel(workbook: XSSFWorkbook): Result<ImportResult> {
    val expenses = mutableListOf<ExpenseEntity>()
    val parseErrors = mutableListOf<String>()

    val sheet =
      workbook.getSheetAt(0)
        ?: return Result.success(ImportResult(emptyList(), listOf("No sheet found")))

    val headerRow =
      sheet.getRow(0) ?: return Result.success(ImportResult(emptyList(), listOf("Empty file")))

    val expectedHeaders = listOf("Date", "Time", "Amount", "Category", "Merchant", "Title")
    for (i in 0 until minOf(6, headerRow.lastCellNum.toInt())) {
      val cell = headerRow.getCell(i)
      val value = getCellStringValue(cell)
      if (value.trim().lowercase() != expectedHeaders[i].lowercase()) {
        return Result.success(
          ImportResult(
            emptyList(),
            listOf("Invalid header. Expected: Date, Time, Amount, Category, Merchant, Title")
          )
        )
      }
    }

    for (rowIndex in 1 until sheet.physicalNumberOfRows) {
      val row = sheet.getRow(rowIndex) ?: continue
      if (isRowEmpty(row)) continue

      val timestamp = parseTimestampFromRow(row)
      val amountCell = row.getCell(2)
      val category = getCellStringValue(row.getCell(3)).trim().ifBlank { "Other" }
      val merchant = getCellStringValue(row.getCell(4)).trim().ifBlank { "Unknown" }
      val title = getCellStringValue(row.getCell(5)).trim().takeIf { it.isNotBlank() }
      if (timestamp == null) {
        parseErrors.add("Row ${rowIndex + 1}: Invalid date/time")
        continue
      }

      val amount =
        when {
          amountCell == null -> null
          amountCell.cellType == CellType.NUMERIC -> amountCell.numericCellValue
          amountCell.cellType == CellType.STRING -> amountCell.stringCellValue.toDoubleOrNull()
          else -> null
        }
      if (amount == null || amount < 0) {
        parseErrors.add("Row ${rowIndex + 1}: Invalid amount")
        continue
      }

      expenses.add(
        ExpenseEntity(
          amount = amount,
          merchant = merchant,
          category = category,
          title = title,
          timestamp = timestamp,
          originalSms = null,
          bank = "Unknown Bank",
          transactionType = "DEBIT"
        )
      )
    }

    return Result.success(ImportResult(expenses, parseErrors))
  }

  private fun getCellStringValue(cell: Cell?): String {
    if (cell == null) return ""
    return when (cell.cellType) {
      CellType.STRING -> cell.stringCellValue
      CellType.NUMERIC -> {
        if (DateUtil.isCellDateFormatted(cell)) {
          val date = cell.dateCellValue
          dateFormat.format(date) + " " + timeFormat.format(date)
        } else {
          cell.numericCellValue.toString()
        }
      }
      CellType.BOOLEAN -> cell.booleanCellValue.toString()
      CellType.FORMULA -> {
        try {
          cell.stringCellValue
        } catch (e: Exception) {
          cell.numericCellValue.toString()
        }
      }
      else -> ""
    }
  }

  private fun parseTimestampFromRow(row: Row): Long? {
    val dateCell = row.getCell(0)
    val timeCell = row.getCell(1)
    if (dateCell == null) return null

    return when {
      dateCell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell) -> {
        val date = dateCell.dateCellValue
        val time =
          when {
            timeCell != null &&
              timeCell.cellType == CellType.NUMERIC &&
              DateUtil.isCellDateFormatted(timeCell) -> timeCell.dateCellValue
            timeCell != null -> {
              val timeStr = getCellStringValue(timeCell)
              if (timeStr.isNotBlank()) {
                timeFormat.parse(timeStr)
                  ?: SimpleDateFormat("HH:mm", Locale.getDefault()).parse(timeStr)
              } else null
            }
            else -> null
          }
        val cal = Calendar.getInstance()
        cal.time = date
        time?.let {
          val timeCal = Calendar.getInstance()
          timeCal.time = it
          cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
          cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
          cal.set(Calendar.SECOND, timeCal.get(Calendar.SECOND))
        }
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
      }
      else -> {
        val dateStr = getCellStringValue(dateCell)
        val timeStr = getCellStringValue(timeCell)
        parseTimestamp(dateStr, timeStr)
      }
    }
  }

  private fun isRowEmpty(row: Row): Boolean {
    for (i in 0 until 6) {
      val cell = row.getCell(i)
      if (cell != null && getCellStringValue(cell).isNotBlank()) {
        return false
      }
    }
    return true
  }

  private fun parseTimestamp(dateStr: String, timeStr: String): Long? {
    return try {
      val datePart = dateStr.trim()
      val timePart = timeStr.trim()

      when {
        datePart.isBlank() -> null
        timePart.isBlank() -> {
          val date = dateFormat.parse(datePart) ?: return null
          val cal = Calendar.getInstance()
          cal.time = date
          cal.set(Calendar.HOUR_OF_DAY, 0)
          cal.set(Calendar.MINUTE, 0)
          cal.set(Calendar.SECOND, 0)
          cal.set(Calendar.MILLISECOND, 0)
          cal.timeInMillis
        }
        else -> {
          val date = dateFormat.parse(datePart) ?: return null
          val time =
            timeFormat.parse(timePart)
              ?: run { SimpleDateFormat("HH:mm", Locale.getDefault()).parse(timePart) }
              ?: return null
          val cal = Calendar.getInstance()
          cal.time = date
          val timeCal = Calendar.getInstance()
          timeCal.time = time
          cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
          cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
          cal.set(Calendar.SECOND, timeCal.get(Calendar.SECOND))
          cal.set(Calendar.MILLISECOND, 0)
          cal.timeInMillis
        }
      }
    } catch (e: Exception) {
      null
    }
  }
}
