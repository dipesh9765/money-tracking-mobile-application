package com.expenseinterceptor.support

import com.expenseinterceptor.data.transfer.ImportFile
import com.expenseinterceptor.data.transfer.ImportKind
import com.expenseinterceptor.data.transfer.ImportSource
import com.expenseinterceptor.domain.model.DateRange
import com.expenseinterceptor.domain.model.DateRanges
import com.expenseinterceptor.domain.model.Expense
import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.model.TransactionType
import com.expenseinterceptor.domain.port.DetectedTransaction
import com.expenseinterceptor.domain.port.ExpenseNotifier
import com.expenseinterceptor.domain.port.ExportFileStore
import com.expenseinterceptor.domain.port.PdfReportWriter
import com.expenseinterceptor.domain.port.RawSms
import com.expenseinterceptor.domain.port.SenderSettings
import com.expenseinterceptor.domain.port.SmsInbox
import com.expenseinterceptor.domain.repository.BulkSaveResult
import com.expenseinterceptor.domain.repository.ExpenseRepository
import com.expenseinterceptor.domain.repository.SaveResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Fixed point in time and zone so no test depends on the machine clock or time zone. */
object TestTime {
  val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
  val NOW: Instant = ZonedDateTime.of(2026, 3, 15, 10, 30, 0, 0, ZONE).toInstant()
  val CLOCK: Clock = Clock.fixed(NOW, ZONE)
  val RANGES = DateRanges(CLOCK, ZONE)

  fun millis(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
    ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant().toEpochMilli()
}

fun expense(
  amount: String = "100.00",
  merchant: String = "SHOP",
  category: String = "Food",
  timestamp: Long = TestTime.millis(2026, 3, 10),
  type: TransactionType = TransactionType.DEBIT,
  bank: String = "HDFCBK",
  title: String? = null,
  dedupeKey: String? = null,
  id: Long = 0,
) =
  Expense(
    id = id,
    amount = Money.parse(amount)!!,
    merchant = merchant,
    category = category,
    timestamp = timestamp,
    type = type,
    bank = bank,
    title = title,
    dedupeKey = dedupeKey,
  )

/** In-memory repository with the same duplicate rules as the Room implementation. */
class FakeExpenseRepository(initial: List<Expense> = emptyList()) : ExpenseRepository {
  private val rows = MutableStateFlow<List<Expense>>(emptyList())
  private var nextId = 1L
  var failWith: IOException? = null

  val all: List<Expense>
    get() = rows.value

  init {
    initial.forEach { rows.value += it.copy(id = nextId++) }
  }

  override fun observe(range: DateRange): Flow<List<Expense>> =
    rows.map { list ->
      list
        .filter { it.timestamp in range }
        .sortedWith(compareByDescending<Expense> { it.timestamp }.thenByDescending { it.id })
    }

  override suspend fun list(range: DateRange): List<Expense> = observe(range).first()

  override suspend fun save(expense: Expense): SaveResult {
    failWith?.let { throw it }
    if (expense.dedupeKey != null && rows.value.any { it.dedupeKey == expense.dedupeKey }) {
      return SaveResult.Duplicate
    }
    val saved = expense.copy(id = nextId++)
    rows.value += saved
    return SaveResult.Saved(saved.id)
  }

  override suspend fun saveAll(expenses: List<Expense>): BulkSaveResult {
    var saved = 0
    expenses.forEach { e ->
      val same =
        rows.value.any {
          it.dedupeKey != null && it.dedupeKey == e.dedupeKey ||
            (it.timestamp / 1000 == e.timestamp / 1000 &&
              it.amount == e.amount &&
              it.merchant == e.merchant &&
              it.type == e.type)
        }
      if (!same && save(e) is SaveResult.Saved) saved++
    }
    return BulkSaveResult(saved, expenses.size - saved)
  }

  override suspend fun update(expense: Expense) {
    rows.value = rows.value.map { if (it.id == expense.id) expense else it }
  }

  override suspend fun delete(id: Long) {
    rows.value = rows.value.filterNot { it.id == id }
  }

  override suspend fun deleteAll() {
    rows.value = emptyList()
  }

  override suspend fun existingDedupeKeys(keys: Collection<String>): Set<String> =
    rows.value.mapNotNull { it.dedupeKey }.filter { it in keys }.toSet()
}

class FakeSenderSettings(initial: List<String> = listOf("HDFCBK", "IDFCFB")) : SenderSettings {
  private val _senders = MutableStateFlow(initial)
  override val senders: StateFlow<List<String>> = _senders.asStateFlow()

  override fun add(pattern: String): Boolean {
    val cleaned = pattern.trim()
    if (cleaned.isEmpty() || _senders.value.any { it.equals(cleaned, true) }) return false
    _senders.value += cleaned
    return true
  }

  override fun remove(pattern: String) {
    _senders.value -= pattern
  }

  override fun resetToDefaults() {
    _senders.value = listOf("HDFCBK")
  }
}

class FakeSmsInbox(var messages: List<RawSms> = emptyList()) : SmsInbox {
  var failWith: SecurityException? = null

  override suspend fun read(range: DateRange): List<RawSms> {
    failWith?.let { throw it }
    return messages.filter { it.timestamp in range }
  }
}

class FakeNotifier : ExpenseNotifier {
  val notifications = mutableListOf<DetectedTransaction>()

  override fun notifyDetected(detected: DetectedTransaction) {
    notifications += detected
  }
}

class FakeExportFileStore : ExportFileStore {
  val files = linkedMapOf<String, ByteArray>()

  override suspend fun write(fileName: String, writer: (OutputStream) -> Unit): String {
    val out = ByteArrayOutputStream()
    writer(out)
    files[fileName] = out.toByteArray()
    return "content://exports/$fileName"
  }
}

class FakePdfWriter : PdfReportWriter {
  override fun write(expenses: List<Expense>, rangeLabel: String, output: OutputStream) {
    output.write("PDF:${expenses.size}:$rangeLabel".toByteArray())
  }
}

class FakeImportSource(private val files: Map<String, Pair<ByteArray, ImportKind>> = emptyMap()) :
  ImportSource {
  override fun open(uri: String): ImportFile {
    val (bytes, kind) = files[uri] ?: throw IOException("Could not open file")
    return ImportFile(ByteArrayInputStream(bytes), kind)
  }
}
