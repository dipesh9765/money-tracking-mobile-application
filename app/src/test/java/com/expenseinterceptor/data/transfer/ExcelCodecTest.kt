package com.expenseinterceptor.data.transfer

import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.model.TransactionType
import com.expenseinterceptor.support.TestTime
import com.expenseinterceptor.support.expense
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcelCodecTest {

  private val excel = ExcelCodec(TestTime.ZONE)

  private fun roundTrip(rows: List<com.expenseinterceptor.domain.model.Expense>) =
    excel.read(
      ByteArrayInputStream(ByteArrayOutputStream().also { excel.write(rows, it) }.toByteArray())
    )

  private fun workbook(build: (org.apache.poi.ss.usermodel.Sheet) -> Unit): ByteArray {
    val out = ByteArrayOutputStream()
    XSSFWorkbook().use { wb ->
      val sheet = wb.createSheet("Expenses")
      val header = sheet.createRow(0)
      listOf("Date", "Time", "Amount", "Category", "Merchant", "Title").forEachIndexed { i, t ->
        header.createCell(i).setCellValue(t)
      }
      build(sheet)
      wb.write(out)
    }
    return out.toByteArray()
  }

  @Test
  fun `export then import is lossless`() {
    val original =
      listOf(
        expense(
          "1234.50",
          "BIG BAZAAR",
          "Shopping",
          TestTime.millis(2026, 3, 10, 14, 5),
          bank = "HASTIB"
        ),
        expense(
          "0.07",
          "TOFFEE",
          "Food",
          TestTime.millis(2026, 3, 11, 9, 30),
          type = TransactionType.CREDIT
        ),
      )
    val parsed = roundTrip(original)

    assertTrue(parsed.errors.isEmpty())
    assertEquals(original.map { it.amount }, parsed.expenses.map { it.amount })
    assertEquals(original.map { it.merchant }, parsed.expenses.map { it.merchant })
    assertEquals(original.map { it.timestamp }, parsed.expenses.map { it.timestamp })
    assertEquals(original.map { it.type }, parsed.expenses.map { it.type })
    assertEquals(original.map { it.bank }, parsed.expenses.map { it.bank })
  }

  @Test
  fun `a formula looking merchant stays a plain text cell`() {
    val out = ByteArrayOutputStream().also { excel.write(listOf(expense(merchant = "=1+1")), it) }
    XSSFWorkbook(ByteArrayInputStream(out.toByteArray())).use { wb ->
      val cell = wb.getSheetAt(0).getRow(1).getCell(4)
      assertEquals(org.apache.poi.ss.usermodel.CellType.STRING, cell.cellType)
      assertEquals("=1+1", cell.stringCellValue)
    }
  }

  @Test
  fun `numeric and text amounts both import and gaps between rows are not skipped`() {
    val bytes = workbook { sheet ->
      sheet.createRow(1).apply {
        createCell(0).setCellValue("2026-03-10")
        createCell(1).setCellValue("10:00:00")
        createCell(2).setCellValue(99.99)
        createCell(3).setCellValue("Food")
        createCell(4).setCellValue("A")
      }
      // row 2 is missing entirely: the previous reader stopped at physicalNumberOfRows
      sheet.createRow(3).apply {
        createCell(0).setCellValue("2026-03-11")
        createCell(2).setCellValue("1,250.00")
        createCell(4).setCellValue("B")
      }
    }
    val parsed = excel.read(ByteArrayInputStream(bytes))
    assertEquals(listOf("A", "B"), parsed.expenses.map { it.merchant })
    assertEquals(Money.parse("99.99"), parsed.expenses[0].amount)
    assertEquals(Money.parse("1250.00"), parsed.expenses[1].amount)
  }

  @Test
  fun `real excel date cells are read in the local zone`() {
    val bytes = workbook { sheet ->
      val wb = sheet.workbook
      val dateStyle =
        wb.createCellStyle().apply { dataFormat = wb.createDataFormat().getFormat("yyyy-mm-dd") }
      val timeStyle =
        wb.createCellStyle().apply { dataFormat = wb.createDataFormat().getFormat("hh:mm:ss") }
      sheet.createRow(1).apply {
        createCell(0).apply {
          setCellValue(java.time.LocalDate.of(2026, 3, 10))
          cellStyle = dateStyle
        }
        createCell(1).apply {
          setCellValue(java.time.LocalDateTime.of(1900, 1, 1, 14, 5, 0))
          cellStyle = timeStyle
        }
        createCell(2).setCellValue(10.0)
        createCell(4).setCellValue("SHOP")
      }
    }
    val e = excel.read(ByteArrayInputStream(bytes)).expenses.single()
    assertEquals(TestTime.millis(2026, 3, 10, 14, 5), e.timestamp)
  }

  @Test
  fun `invalid rows are reported and wrong headers rejected`() {
    val bad = workbook { sheet ->
      sheet.createRow(1).apply {
        createCell(0).setCellValue("garbage")
        createCell(2).setCellValue(1.0)
      }
    }
    val parsed = excel.read(ByteArrayInputStream(bad))
    assertTrue(parsed.expenses.isEmpty())
    assertTrue(parsed.errors.single().startsWith("Row 2"))

    val wrongHeader = ByteArrayOutputStream()
    XSSFWorkbook().use { wb ->
      wb.createSheet("x").createRow(0).createCell(0).setCellValue("nope")
      wb.write(wrongHeader)
    }
    assertTrue(
      excel
        .read(ByteArrayInputStream(wrongHeader.toByteArray()))
        .errors
        .single()
        .startsWith("Invalid header")
    )
  }

  @Test
  fun `a file that is not a workbook fails with an exception the view model handles`() {
    val result = runCatching { excel.read(ByteArrayInputStream("hello".toByteArray())) }
    assertTrue(result.isFailure)
  }
}
