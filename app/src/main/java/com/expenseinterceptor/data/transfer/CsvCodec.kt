package com.expenseinterceptor.data.transfer

import com.expenseinterceptor.data.transfer.TransferFormat.Col
import com.expenseinterceptor.domain.model.Expense
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Reads and writes expenses as RFC 4180 CSV.
 *
 * Writing neutralises spreadsheet formulas (see [SpreadsheetSafety]) and prefixes a UTF-8 byte
 * order mark so Excel shows non-ASCII merchants correctly. Reading handles quoted fields that
 * contain commas, quotes and line breaks, and accepts both the current 8-column layout and the
 * older 6-column files.
 */
class CsvCodec @Inject constructor(private val zone: ZoneId) {

  private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

  fun write(expenses: List<Expense>, output: OutputStream) {
    val writer = output.bufferedWriter(Charsets.UTF_8)
    writer.write(BOM)
    writer.write(TransferFormat.HEADERS.joinToString(",") + CRLF)
    expenses.forEach { e ->
      val at = Instant.ofEpochMilli(e.timestamp).atZone(zone)
      val fields =
        listOf(
          dateFormat.format(at),
          timeFormat.format(at),
          e.amount.toPlainString(),
          SpreadsheetSafety.neutralize(e.category),
          SpreadsheetSafety.neutralize(e.merchant),
          SpreadsheetSafety.neutralize(e.title.orEmpty()),
          SpreadsheetSafety.neutralize(e.bank),
          e.type.name,
        )
      writer.write(fields.joinToString(",") { quote(it) } + CRLF)
    }
    writer.flush()
  }

  fun read(input: InputStream): ImportParse {
    val records = parseRecords(input.bufferedReader(Charsets.UTF_8))
    val header = records.firstOrNull() ?: return ImportParse(emptyList(), listOf("File is empty"))
    if (!hasExpectedHeader(header)) {
      return ImportParse(
        emptyList(),
        listOf("Invalid header. Expected: ${TransferFormat.HEADERS.joinToString(",")}"),
      )
    }
    val parser = ImportRowParser(zone)
    val expenses = mutableListOf<Expense>()
    val errors = mutableListOf<String>()
    records.drop(1).forEachIndexed { index, fields ->
      val rowNumber = index + 2
      if (fields.size < TransferFormat.LEGACY_COLUMNS) {
        errors +=
          "Row $rowNumber: expected ${TransferFormat.LEGACY_COLUMNS} columns, got ${fields.size}"
      } else {
        when (val result = parser.parse(rowNumber, toRawRow(fields))) {
          is ImportRowParser.Result.Ok -> expenses += result.expense
          is ImportRowParser.Result.Failed -> errors += result.message
        }
      }
    }
    return ImportParse(expenses, errors)
  }

  private fun toRawRow(fields: List<String>) =
    RawRow(
      date = fields[Col.DATE],
      time = fields[Col.TIME],
      amount = fields[Col.AMOUNT],
      category = fields[Col.CATEGORY],
      merchant = fields[Col.MERCHANT],
      title = fields[Col.TITLE],
      bank = fields.getOrElse(Col.BANK) { "" },
      type = fields.getOrElse(Col.TYPE) { "" },
    )

  private fun hasExpectedHeader(header: List<String>): Boolean {
    val names = header.map { it.trim().removePrefix(BOM).lowercase() }
    val expected = TransferFormat.HEADERS.map { it.lowercase() }
    return names.take(TransferFormat.LEGACY_COLUMNS) == expected.take(TransferFormat.LEGACY_COLUMNS)
  }

  private fun quote(field: String): String =
    if (field.any { it in SPECIAL }) {
      "\"" + field.replace("\"", "\"\"") + "\""
    } else {
      field
    }

  /** Splits CSV text into records of fields. Blank records are dropped. */
  internal fun parseRecords(reader: Reader): List<List<String>> = RecordReader(reader).readAll()

  /** Small state machine over the characters of a CSV document. */
  private class RecordReader(private val reader: Reader) {
    private val records = mutableListOf<List<String>>()
    private var fields = mutableListOf<String>()
    private val field = StringBuilder()
    private var inQuotes = false
    private var pending = NO_CHAR

    fun readAll(): List<List<String>> {
      while (true) {
        val c = next()
        if (c == END) break
        if (inQuotes) quoted(c.toChar()) else unquoted(c.toChar())
      }
      if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
      return records
    }

    private fun next(): Int =
      if (pending != NO_CHAR) pending.also { pending = NO_CHAR } else reader.read()

    private fun quoted(ch: Char) {
      if (ch != '"') {
        field.append(ch)
        return
      }
      val following = next()
      if (following == '"'.code) {
        field.append('"')
      } else {
        inQuotes = false
        pending = following
      }
    }

    private fun unquoted(ch: Char) {
      when {
        ch == '"' && field.isEmpty() -> inQuotes = true
        ch == ',' -> endField()
        ch == '\r' -> {
          val following = next()
          if (following != '\n'.code) pending = following
          endRecord()
        }
        ch == '\n' -> endRecord()
        else -> field.append(ch)
      }
    }

    private fun endField() {
      fields += field.toString()
      field.setLength(0)
    }

    private fun endRecord() {
      endField()
      if (fields.any { it.isNotEmpty() }) records += fields
      fields = mutableListOf()
      require(records.size <= TransferFormat.MAX_ROWS + 1) { "Too many rows" }
    }
  }

  private companion object {
    const val BOM = "\uFEFF"
    const val CRLF = "\r\n"
    val SPECIAL = setOf(',', '"', '\n', '\r')
    const val END = -1
    const val NO_CHAR = -2
  }
}
