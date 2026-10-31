package com.expenseinterceptor.data.transfer

import com.expenseinterceptor.domain.model.TransactionType
import com.expenseinterceptor.support.FakeExpenseRepository
import com.expenseinterceptor.support.FakeExportFileStore
import com.expenseinterceptor.support.FakePdfWriter
import com.expenseinterceptor.support.TestTime
import com.expenseinterceptor.support.expense
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseTransferTest {

  private val csv = CsvCodec(TestTime.ZONE)
  private val excel = ExcelCodec(TestTime.ZONE)
  private val store = FakeExportFileStore()
  private val exporter = ExpenseExporter(csv, excel, FakePdfWriter(), store)
  private val label = "01 Mar 2026 - 31 Mar 2026"

  private val sample =
    listOf(
      expense("500.00", "SWIGGY", "Food"),
      expense("300.00", "ZOMATO", "Food"),
      expense("1200.00", "UBER", "Travel"),
      expense("5000.00", "SALARY", "Other", type = TransactionType.CREDIT),
    )

  // ---- exporter ------------------------------------------------------------------------------

  @Test
  fun `text report totals spend and income separately`() {
    val text = exporter.textReport(sample, label)
    assertTrue(text.contains("Spent: ₹2,000.00"))
    assertTrue(text.contains("Received: ₹5,000.00"))
    assertTrue(text.contains("Transactions: 4"))
    assertTrue(text.contains("Food: ₹800.00"))
    assertTrue("credits are not part of the category spend", !text.contains("Other: ₹5,000.00"))
    assertTrue(text.contains("- SALARY: +₹5,000.00"))
    assertTrue(text.contains("- UBER: -₹1,200.00"))
  }

  @Test
  fun `text export returns the body and no file`() = runBlocking {
    val result = exporter.export(ExportFormat.TEXT, sample, label)
    assertTrue(result is ExportResult.Text)
    assertEquals("Expenses - $label", result.subject)
    assertTrue(store.files.isEmpty())
  }

  @Test
  fun `file exports are named from the range and have the right mime type`() = runBlocking {
    val csvResult = exporter.export(ExportFormat.CSV, sample, label) as ExportResult.File
    val xlsx = exporter.export(ExportFormat.EXCEL, sample, label) as ExportResult.File
    val pdf = exporter.export(ExportFormat.PDF, sample, label) as ExportResult.File

    assertEquals(
      listOf(
        "expenses_01_mar_2026_31_mar_2026.csv",
        "expenses_01_mar_2026_31_mar_2026.xlsx",
        "expenses_01_mar_2026_31_mar_2026.pdf",
      ),
      store.files.keys.toList(),
    )
    assertEquals("text/csv", csvResult.mimeType)
    assertEquals("application/pdf", pdf.mimeType)
    assertTrue(xlsx.mimeType.endsWith("spreadsheetml.sheet"))
    assertEquals(
      "PDF:4:$label",
      String(store.files.getValue("expenses_01_mar_2026_31_mar_2026.pdf"))
    )
  }

  @Test
  fun `a hostile range label cannot escape the export directory`() = runBlocking {
    exporter.export(ExportFormat.CSV, sample, "../../etc/passwd")
    assertEquals(listOf("expenses_etc_passwd.csv"), store.files.keys.toList())
  }

  @Test
  fun `an exported csv can be imported back`() = runBlocking {
    exporter.export(ExportFormat.CSV, sample, label)
    val bytes = store.files.values.single()
    val parsed = csv.read(ByteArrayInputStream(bytes))
    assertEquals(sample.map { it.amount }, parsed.expenses.map { it.amount })
    assertEquals(sample.map { it.type }, parsed.expenses.map { it.type })
  }

  // ---- importer ------------------------------------------------------------------------------

  @Test
  fun `importing the same file twice adds rows once`() = runBlocking {
    val repo = FakeExpenseRepository()
    val importer = ExpenseImporter(csv, excel, repo)
    val bytes = ByteArrayInputStreamOf(sample)

    val first = importer.import(bytes(), ImportKind.CSV)
    assertEquals(4, first.imported)
    assertEquals(0, first.duplicates)

    val second = importer.import(bytes(), ImportKind.CSV)
    assertEquals(0, second.imported)
    assertEquals(4, second.duplicates)
    assertEquals(4, repo.all.size)
    assertEquals("Imported 0 transactions, skipped 4 already present", second.message)
  }

  @Test
  fun `importing rows already stored from sms is not duplicated`() = runBlocking {
    // the stored row has millisecond precision, the csv only seconds
    val stored = expense("500.00", "SWIGGY", "Food", TestTime.millis(2026, 3, 10) + 321)
    val repo = FakeExpenseRepository(listOf(stored))
    val summary =
      ExpenseImporter(csv, excel, repo)
        .import(ByteArrayInputStreamOf(listOf(stored))(), ImportKind.CSV)
    assertEquals(1, summary.duplicates)
    assertEquals(1, repo.all.size)
  }

  @Test
  fun `summary message reports errors and the empty case`() {
    assertEquals(
      "Imported 2 transactions. 1 rows had errors",
      ImportSummary(2, 0, listOf("Row 3: bad")).message,
    )
    assertEquals("Row 2: bad", ImportSummary(0, 0, listOf("Row 2: bad")).message)
    assertEquals("No transactions to import", ImportSummary(0, 0, emptyList()).message)
    assertEquals("Imported 1 transaction", ImportSummary(1, 0, emptyList()).message)
  }

  private fun ByteArrayInputStreamOf(
    rows: List<com.expenseinterceptor.domain.model.Expense>
  ): () -> ByteArrayInputStream {
    val out = java.io.ByteArrayOutputStream().also { csv.write(rows, it) }.toByteArray()
    return { ByteArrayInputStream(out) }
  }
}
