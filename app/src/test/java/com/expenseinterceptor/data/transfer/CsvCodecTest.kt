package com.expenseinterceptor.data.transfer

import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.model.TransactionType
import com.expenseinterceptor.support.TestTime
import com.expenseinterceptor.support.expense
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvCodecTest {

  private val csv = CsvCodec(TestTime.ZONE)

  private fun write(vararg rows: com.expenseinterceptor.domain.model.Expense): String =
    ByteArrayOutputStream().also { csv.write(rows.toList(), it) }.toString(Charsets.UTF_8)

  private fun read(text: String) = csv.read(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))

  @Test
  fun `export then import is lossless including bank and direction`() {
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
          "2000.00",
          "RAHUL",
          "Other",
          TestTime.millis(2026, 3, 11, 9, 30),
          type = TransactionType.CREDIT,
          bank = "IDFCFB",
          title = "rent share",
        ),
      )
    val out = ByteArrayOutputStream().also { csv.write(original, it) }
    val parsed = csv.read(ByteArrayInputStream(out.toByteArray()))

    assertTrue(parsed.errors.isEmpty())
    assertEquals(original.size, parsed.expenses.size)
    original.zip(parsed.expenses).forEach { (a, b) ->
      assertEquals(a.amount, b.amount)
      assertEquals(a.merchant, b.merchant)
      assertEquals(a.category, b.category)
      assertEquals(a.timestamp, b.timestamp)
      assertEquals(a.type, b.type)
      assertEquals(a.bank, b.bank)
      assertEquals(a.title, b.title)
    }
  }

  @Test
  fun `amount is written as exact decimal text`() {
    assertTrue(write(expense("0.10")).contains(",0.10,"))
    assertTrue(write(expense("1234.00")).contains(",1234.00,"))
  }

  @Test
  fun `file starts with a byte order mark and uses crlf`() {
    val text = write(expense())
    assertTrue(text.startsWith("﻿Date,Time,Amount"))
    assertTrue(text.contains("\r\n"))
  }

  @Test
  fun `fields with commas, quotes and line breaks survive a round trip`() {
    val tricky = "Dosa, \"Special\"\nCorner"
    val parsed = read(write(expense(merchant = tricky, title = "a,b")))
    assertEquals(tricky, parsed.expenses.single().merchant)
    assertEquals("a,b", parsed.expenses.single().title)
  }

  @Test
  fun `formula injection from an sms merchant is neutralised in the file and restored on import`() {
    val hostile = "=HYPERLINK(\"http://evil\",\"pay\")"
    val text = write(expense(merchant = hostile))
    assertFalse(
      "no cell may start with =",
      text.lines().any { line -> line.split(",").any { it.startsWith("=") } }
    )
    assertTrue(text.contains("'=HYPERLINK"))
    assertEquals(hostile, read(text).expenses.single().merchant)
  }

  @Test
  fun `all formula trigger characters are neutralised`() {
    listOf("=1+1", "+1", "-1", "@SUM(A1)").forEach { payload ->
      val line = write(expense(merchant = payload)).lines()[1]
      assertTrue("$payload must be quoted with a guard: $line", line.contains("'$payload"))
    }
  }

  @Test
  fun `older six column files still import as unknown bank debits`() {
    val legacy =
      "Date,Time,Amount,Category,Merchant,Title\n2026-03-10,14:05:00,250.00,Food,CAFE,lunch\n"
    val parsed = read(legacy)
    val e = parsed.expenses.single()
    assertEquals(Money.parse("250.00"), e.amount)
    assertEquals("Unknown Bank", e.bank)
    assertEquals(TransactionType.DEBIT, e.type)
    assertEquals("lunch", e.title)
  }

  @Test
  fun `bad rows are reported with their row number and good rows are kept`() {
    val text =
      "Date,Time,Amount,Category,Merchant,Title\n" +
        "2026-03-10,10:00:00,100.00,Food,A,\n" +
        "not-a-date,10:00:00,100.00,Food,B,\n" +
        "2026-03-10,10:00:00,abc,Food,C,\n" +
        "2026-03-10,10:00:00,-5,Food,D,\n" +
        "2026-03-10,10:00:00\n" +
        "2026-03-11,25:61:00,1.00,Food,E,\n"
    val parsed = read(text)
    assertEquals(listOf("A"), parsed.expenses.map { it.merchant })
    assertEquals(5, parsed.errors.size)
    assertTrue(parsed.errors[0].startsWith("Row 3"))
    assertTrue(parsed.errors[1].startsWith("Row 4"))
    assertTrue(parsed.errors[2].startsWith("Row 5"))
    assertTrue(parsed.errors[3].startsWith("Row 6"))
    assertTrue(parsed.errors[4].startsWith("Row 7"))
  }

  @Test
  fun `wrong header and empty files are rejected with a message`() {
    assertTrue(read("a,b,c\n1,2,3\n").errors.single().startsWith("Invalid header"))
    assertEquals(listOf("File is empty"), read("").errors)
    assertTrue(read("").expenses.isEmpty())
  }

  @Test
  fun `blank time means midnight and a blank category becomes Other`() {
    val e =
      read("Date,Time,Amount,Category,Merchant,Title\n2026-03-10,,10,,SHOP,\n").expenses.single()
    assertEquals(TestTime.millis(2026, 3, 10, 0, 0), e.timestamp)
    assertEquals("Other", e.category)
  }

  @Test
  fun `short times without seconds are accepted`() {
    val e =
      read("Date,Time,Amount,Category,Merchant,Title\n2026-03-10,14:05,10,Food,SHOP,\n")
        .expenses
        .single()
    assertEquals(TestTime.millis(2026, 3, 10, 14, 5), e.timestamp)
  }

  @Test
  fun `crlf, lf and a missing final newline all parse`() {
    val header = "Date,Time,Amount,Category,Merchant,Title"
    listOf("\r\n", "\n").forEach { nl ->
      val text =
        header + nl + "2026-03-10,10:00:00,1,Food,A," + nl + "2026-03-10,11:00:00,2,Food,B,"
      assertEquals(listOf("A", "B"), read(text).expenses.map { it.merchant })
    }
  }

  @Test
  fun `a file with too many rows is refused rather than loaded into memory`() {
    val big = StringBuilder("Date,Time,Amount,Category,Merchant,Title\n")
    repeat(TransferFormat.MAX_ROWS + 5) { big.append("2026-03-10,10:00:00,1,Food,A,\n") }
    val result = runCatching { read(big.toString()) }
    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
  }

  @Test
  fun `very long fields are truncated`() {
    val long = "x".repeat(10_000)
    val e =
      read("Date,Time,Amount,Category,Merchant,Title\n2026-03-10,10:00:00,1,Food,$long,\n")
        .expenses
        .single()
    assertEquals(TransferFormat.MAX_FIELD_LENGTH, e.merchant.length)
  }
}
