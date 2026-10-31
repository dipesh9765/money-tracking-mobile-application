package com.expenseinterceptor.data.transfer

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.expenseinterceptor.domain.model.Expense
import com.expenseinterceptor.domain.port.PdfReportWriter
import com.expenseinterceptor.domain.usecase.ExpenseSummary
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Draws a plain-text PDF report with the platform [PdfDocument]. */
class AndroidPdfReportWriter @Inject constructor(private val zone: ZoneId) : PdfReportWriter {

  private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  override fun write(expenses: List<Expense>, rangeLabel: String, output: OutputStream) {
    val document = PdfDocument()
    val body =
      Paint().apply {
        isAntiAlias = true
        textSize = BODY_SIZE
      }
    val title = Paint(body).apply { textSize = TITLE_SIZE }
    val lineHeight = BODY_SIZE + LINE_GAP

    var pageNumber = 1
    var page = document.startPage(pageInfo(pageNumber))
    var y = MARGIN + TOP_OFFSET

    fun line(text: String, paint: Paint = body) {
      if (y + lineHeight > PAGE_HEIGHT - MARGIN) {
        document.finishPage(page)
        page = document.startPage(pageInfo(++pageNumber))
        y = MARGIN + TOP_OFFSET
      }
      page.canvas.drawText(text, MARGIN, y, paint)
      y += paint.textSize + LINE_GAP
    }

    val summary = ExpenseSummary.of(expenses)
    line("Expenses ($rangeLabel)", title)
    line("Spent: ${summary.spent.format()}   Received: ${summary.received.format()}")
    line("Transactions: ${summary.count}")
    line(" ")
    expenses.forEach { e ->
      val at = Instant.ofEpochMilli(e.timestamp).atZone(zone)
      val sign = if (e.isCredit) "+" else "-"
      line("${stamp.format(at)} - ${e.merchant}")
      line("Category: ${e.category}   Amount: $sign${e.amount.format()}")
      e.title?.takeIf { it.isNotBlank() }?.let { line("Note: $it") }
      line("------------------------------")
    }
    document.finishPage(page)
    document.writeTo(output)
    document.close()
  }

  private fun pageInfo(number: Int) =
    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()

  private companion object {
    const val PAGE_WIDTH = 595 // A4 in points
    const val PAGE_HEIGHT = 842
    const val MARGIN = 36f
    const val TOP_OFFSET = 10f
    const val BODY_SIZE = 12f
    const val TITLE_SIZE = 16f
    const val LINE_GAP = 6f
  }
}
