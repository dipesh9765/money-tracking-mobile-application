package com.expenseinterceptor.data.transfer

import com.expenseinterceptor.data.transfer.TransferFormat.Col
import com.expenseinterceptor.domain.model.Expense
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook

/** Reads and writes expenses as an `.xlsx` workbook with the same columns as the CSV. */
class ExcelCodec @Inject constructor(private val zone: ZoneId) {

  private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

  fun write(expenses: List<Expense>, output: OutputStream) {
    XSSFWorkbook().use { workbook ->
      val sheet = workbook.createSheet("Expenses")
      val headerStyle =
        workbook.createCellStyle().apply {
          fillForegroundColor = IndexedColors.LIGHT_CORNFLOWER_BLUE.index
          fillPattern = FillPatternType.SOLID_FOREGROUND
          alignment = HorizontalAlignment.CENTER
          setFont(workbook.createFont().apply { bold = true })
        }
      val amountStyle =
        workbook.createCellStyle().apply {
          dataFormat = workbook.createDataFormat().getFormat("0.00")
        }
      val header = sheet.createRow(0)
      TransferFormat.HEADERS.forEachIndexed { i, title ->
        header.createCell(i).apply {
          setCellValue(title)
          cellStyle = headerStyle
        }
      }
      expenses.forEachIndexed { index, e ->
        val at = Instant.ofEpochMilli(e.timestamp).atZone(zone)
        val row = sheet.createRow(index + 1)
        row.createCell(Col.DATE).setCellValue(dateFormat.format(at))
        row.createCell(Col.TIME).setCellValue(timeFormat.format(at))
        row.createCell(Col.AMOUNT).apply {
          setCellValue(e.amount.toDouble())
          cellStyle = amountStyle
        }
        // String cells are never evaluated as formulas, so no neutralising is needed here.
        row.createCell(Col.CATEGORY).setCellValue(e.category)
        row.createCell(Col.MERCHANT).setCellValue(e.merchant)
        row.createCell(Col.TITLE).setCellValue(e.title.orEmpty())
        row.createCell(Col.BANK).setCellValue(e.bank)
        row.createCell(Col.TYPE).setCellValue(e.type.name)
      }
      TransferFormat.HEADERS.indices.forEach { sheet.setColumnWidth(it, COLUMN_WIDTH) }
      workbook.write(output)
    }
  }

  fun read(input: InputStream): ImportParse {
    XSSFWorkbook(input).use { workbook ->
      val sheet =
        workbook.takeIf { it.numberOfSheets > 0 }?.getSheetAt(0)
          ?: return ImportParse(emptyList(), listOf("No sheet found"))
      val header = sheet.getRow(0) ?: return ImportParse(emptyList(), listOf("Empty file"))
      val legacy = TransferFormat.LEGACY_COLUMNS
      val titles = (0 until legacy).map { text(header.getCell(it)).lowercase() }
      if (titles != TransferFormat.HEADERS.take(legacy).map { it.lowercase() }) {
        return ImportParse(
          emptyList(),
          listOf("Invalid header. Expected: ${TransferFormat.HEADERS.joinToString(", ")}"),
        )
      }
      require(sheet.lastRowNum <= TransferFormat.MAX_ROWS) { "Too many rows" }
      val parser = ImportRowParser(zone)
      val expenses = mutableListOf<Expense>()
      val errors = mutableListOf<String>()
      for (index in 1..sheet.lastRowNum) {
        val row = sheet.getRow(index) ?: continue
        val raw =
          RawRow(
            date = dateText(row.getCell(Col.DATE)),
            time = timeText(row.getCell(Col.TIME)),
            amount = amountText(row.getCell(Col.AMOUNT)),
            category = text(row.getCell(Col.CATEGORY)),
            merchant = text(row.getCell(Col.MERCHANT)),
            title = text(row.getCell(Col.TITLE)),
            bank = text(row.getCell(Col.BANK)),
            type = text(row.getCell(Col.TYPE)),
          )
        if (raw == EMPTY_ROW) continue
        when (val result = parser.parse(index + 1, raw)) {
          is ImportRowParser.Result.Ok -> expenses += result.expense
          is ImportRowParser.Result.Failed -> errors += result.message
        }
      }
      return ImportParse(expenses, errors)
    }
  }

  private fun text(cell: Cell?): String =
    when (cell?.cellType) {
      CellType.STRING -> cell.stringCellValue
      CellType.NUMERIC ->
        BigDecimal.valueOf(cell.numericCellValue).stripTrailingZeros().toPlainString()
      CellType.BOOLEAN -> cell.booleanCellValue.toString()
      else -> ""
    }

  private fun isDateCell(cell: Cell?): Boolean =
    cell?.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)

  private fun dateText(cell: Cell?): String =
    if (isDateCell(cell)) dateFormat.format(cell!!.localDateTimeCellValue) else text(cell)

  private fun timeText(cell: Cell?): String =
    if (isDateCell(cell)) timeFormat.format(cell!!.localDateTimeCellValue) else text(cell)

  private fun amountText(cell: Cell?): String =
    if (cell?.cellType == CellType.NUMERIC) {
      BigDecimal.valueOf(cell.numericCellValue).toPlainString()
    } else {
      text(cell)
    }

  private companion object {
    const val COLUMN_WIDTH = 18 * 256
    val EMPTY_ROW = RawRow("", "", "", "", "", "")
  }
}
