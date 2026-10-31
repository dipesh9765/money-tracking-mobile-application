package com.expenseinterceptor.ui

import app.cash.turbine.test
import com.expenseinterceptor.data.transfer.CsvCodec
import com.expenseinterceptor.data.transfer.ExcelCodec
import com.expenseinterceptor.data.transfer.ExpenseExporter
import com.expenseinterceptor.data.transfer.ExpenseImporter
import com.expenseinterceptor.data.transfer.ExportFormat
import com.expenseinterceptor.data.transfer.ExportResult
import com.expenseinterceptor.data.transfer.ImportKind
import com.expenseinterceptor.domain.model.ExpenseCategory
import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.model.TransactionType
import com.expenseinterceptor.domain.port.RawSms
import com.expenseinterceptor.domain.sms.SmsParser
import com.expenseinterceptor.domain.usecase.DashboardCalculator
import com.expenseinterceptor.domain.usecase.DashboardSortBy
import com.expenseinterceptor.domain.usecase.SaveScannedTransactions
import com.expenseinterceptor.domain.usecase.ScanSmsInbox
import com.expenseinterceptor.domain.usecase.TypeFilter
import com.expenseinterceptor.support.FakeExpenseRepository
import com.expenseinterceptor.support.FakeExportFileStore
import com.expenseinterceptor.support.FakeImportSource
import com.expenseinterceptor.support.FakePdfWriter
import com.expenseinterceptor.support.FakeSenderSettings
import com.expenseinterceptor.support.FakeSmsInbox
import com.expenseinterceptor.support.MainDispatcherRule
import com.expenseinterceptor.support.TestTime
import com.expenseinterceptor.support.expense
import com.expenseinterceptor.ui.main.DashboardViewModel
import com.expenseinterceptor.ui.main.ReportingPeriod
import com.expenseinterceptor.ui.main.TransactionsEvent
import com.expenseinterceptor.ui.main.TransactionsViewModel
import com.expenseinterceptor.ui.main.TransferEvent
import com.expenseinterceptor.ui.main.TransferViewModel
import com.expenseinterceptor.ui.quickadd.QuickAddArgs
import com.expenseinterceptor.ui.quickadd.QuickAddViewModel
import com.expenseinterceptor.ui.scan.ScanViewModel
import com.expenseinterceptor.ui.settings.SendersViewModel
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {

  @get:Rule val main = MainDispatcherRule()

  private val ts = TestTime.millis(2026, 3, 10)
  private val period = ReportingPeriod(TestTime.RANGES)

  // ---- TransactionsViewModel ----------------------------------------------------------------

  private fun transactions(repo: FakeExpenseRepository) = TransactionsViewModel(repo, period)

  @Test
  fun `state lists the selected range and totals spend and income`() =
    runTest(main.dispatcher) {
      val repo =
        FakeExpenseRepository(
          listOf(
            expense("100.00", timestamp = ts),
            expense("50.00", timestamp = ts + 1),
            expense("900.00", type = TransactionType.CREDIT, timestamp = ts + 2),
            expense("7.00", timestamp = TestTime.millis(2026, 1, 3)), // outside March
          )
        )
      val vm = transactions(repo)
      backgroundScope.launch { vm.state.collect {} }

      val state = vm.state.value
      assertEquals(3, state.expenses.size)
      assertEquals(Money.parse("150.00"), state.summary.spent)
      assertEquals(Money.parse("900.00"), state.summary.received)
    }

  @Test
  fun `changing the end date narrows the list`() =
    runTest(main.dispatcher) {
      val repo =
        FakeExpenseRepository(
          listOf(
            expense(timestamp = TestTime.millis(2026, 3, 2)),
            expense(timestamp = TestTime.millis(2026, 3, 20))
          )
        )
      val vm = transactions(repo)
      backgroundScope.launch { vm.state.collect {} }

      vm.updateEndDate(TestTime.millis(2026, 3, 5))
      assertEquals(1, vm.state.value.expenses.size)
      vm.updateStartDate(TestTime.millis(2026, 3, 1))
      assertEquals(TestTime.millis(2026, 3, 6, 0, 0), vm.state.value.range.endExclusiveMillis)
    }

  @Test
  fun `editing saves the new category, bank and direction`() =
    runTest(main.dispatcher) {
      val repo =
        FakeExpenseRepository(listOf(expense(category = ExpenseCategory.UNCATEGORIZED, bank = "X")))
      val vm = transactions(repo)
      val original = repo.all.single()

      vm.startEditing(original)
      assertEquals(ExpenseCategory.OTHER, vm.editState.value?.category) // unknown maps to Other
      vm.updateEditCategory(ExpenseCategory.HEALTH)
      vm.updateEditBank("  ICICIB ")
      vm.updateEditType(TransactionType.CREDIT)
      vm.events.test {
        vm.saveEditedExpense()
        assertEquals(TransactionsEvent.Message("Expense updated"), awaitItem())
      }

      val saved = repo.all.single()
      assertEquals("Health", saved.category)
      assertEquals("ICICIB", saved.bank)
      assertEquals(TransactionType.CREDIT, saved.type)
      assertNull(vm.editState.value)
    }

  @Test
  fun `blank bank falls back to unknown and dismiss discards edits`() =
    runTest(main.dispatcher) {
      val repo = FakeExpenseRepository(listOf(expense()))
      val vm = transactions(repo)
      vm.startEditing(repo.all.single())
      vm.updateEditBank("   ")
      vm.saveEditedExpense()
      assertEquals("Unknown Bank", repo.all.single().bank)

      vm.startEditing(repo.all.single())
      vm.dismissEditing()
      assertNull(vm.editState.value)
    }

  @Test
  fun `delete announces an undo and restore brings the row back`() =
    runTest(main.dispatcher) {
      val repo = FakeExpenseRepository(listOf(expense(merchant = "KEEP", dedupeKey = "k1")))
      val vm = transactions(repo)
      val target = repo.all.single()

      vm.events.test {
        vm.deleteExpense(target)
        val event = awaitItem() as TransactionsEvent.Deleted
        assertEquals(target, event.expense)
        assertTrue(repo.all.isEmpty())

        vm.restore(event.expense)
        assertEquals(TransactionsEvent.Message("Transaction restored"), awaitItem())
      }
      assertEquals("KEEP", repo.all.single().merchant)
    }

  @Test
  fun `delete all empties the store`() =
    runTest(main.dispatcher) {
      val repo = FakeExpenseRepository(listOf(expense(), expense()))
      val vm = transactions(repo)
      vm.events.test {
        vm.deleteAllExpenses()
        assertEquals(TransactionsEvent.Message("Deleted all expenses"), awaitItem())
      }
      assertTrue(repo.all.isEmpty())
    }

  // ---- DashboardViewModel -------------------------------------------------------------------

  @Test
  fun `dashboard filters react to toggles and share the period with the list`() =
    runTest(main.dispatcher) {
      val repo =
        FakeExpenseRepository(
          listOf(
            expense("100.00", category = "Food", bank = "HDFCBK", timestamp = ts),
            expense("200.00", category = "Travel", bank = "IDFCFB", timestamp = ts + 1),
            expense("50.00", category = "Food", type = TransactionType.CREDIT, timestamp = ts + 2),
          )
        )
      val vm = DashboardViewModel(repo, period, DashboardCalculator(TestTime.ZONE))
      backgroundScope.launch { vm.model.collect {} }

      assertEquals(3, vm.model.value.expenses.size)
      vm.toggleCategory("Food")
      assertEquals(2, vm.model.value.expenses.size)
      vm.setType(TypeFilter.DEBIT)
      assertEquals(1, vm.model.value.expenses.size)
      vm.toggleCategory("Food") // off again
      vm.toggleBank("IDFCFB")
      assertEquals(listOf("Travel"), vm.model.value.expenses.map { it.category })
      vm.setSortBy(DashboardSortBy.AMOUNT_ASC)
      assertEquals(DashboardSortBy.AMOUNT_ASC, vm.state.value.filter.sortBy)

      vm.toggleHighlight("Food")
      assertEquals(setOf("Food"), vm.state.value.highlightedCategories)

      // moving the shared period away from March empties the dashboard
      vm.updateStartDate(TestTime.millis(2026, 3, 25))
      vm.updateEndDate(TestTime.millis(2026, 3, 28))
      assertTrue(vm.model.value.expenses.isEmpty())
      assertEquals(period.range.value, vm.range.value)
    }

  // ---- TransferViewModel --------------------------------------------------------------------

  private val csv = CsvCodec(TestTime.ZONE)
  private val excel = ExcelCodec(TestTime.ZONE)

  private fun transfer(repo: FakeExpenseRepository, source: FakeImportSource = FakeImportSource()) =
    TransferViewModel(
      repo,
      period,
      ExpenseExporter(csv, excel, FakePdfWriter(), FakeExportFileStore()),
      ExpenseImporter(csv, excel, repo),
      source,
    )

  @Test
  fun `exporting an empty range says so instead of sharing an empty file`() =
    runTest(main.dispatcher) {
      val vm = transfer(FakeExpenseRepository())
      vm.events.test {
        vm.export(ExportFormat.CSV)
        assertEquals(TransferEvent.Message("No expenses in the selected range"), awaitItem())
      }
      assertFalse(vm.state.value.isPreparingShare)
    }

  @Test
  fun `exporting produces a share event`() =
    runTest(main.dispatcher) {
      val vm = transfer(FakeExpenseRepository(listOf(expense(timestamp = ts))))
      vm.events.test {
        vm.export(ExportFormat.CSV)
        val share = awaitItem() as TransferEvent.Share
        assertTrue(share.result is ExportResult.File)
      }
      assertFalse(vm.state.value.isPreparingShare)
    }

  @Test
  fun `import reports what was added and refuses a bad file without crashing`() =
    runTest(main.dispatcher) {
      val bytes =
        ByteArrayOutputStream()
          .also { csv.write(listOf(expense("12.00", timestamp = ts)), it) }
          .toByteArray()
      val source =
        FakeImportSource(
          mapOf(
            "content://good" to (bytes to ImportKind.CSV),
            "content://bad" to ("nope".toByteArray() to ImportKind.EXCEL)
          )
        )
      val repo = FakeExpenseRepository()
      val vm = transfer(repo, source)

      vm.events.test {
        vm.import("content://good")
        assertEquals(TransferEvent.Message("Imported 1 transaction"), awaitItem())
        vm.import("content://good")
        assertEquals(
          TransferEvent.Message("Imported 0 transactions, skipped 1 already present"),
          awaitItem()
        )
        vm.import("content://bad")
        assertTrue((awaitItem() as TransferEvent.Message).text.startsWith("Import failed"))
        vm.import("content://missing")
        assertEquals(TransferEvent.Message("Import failed: Could not open file"), awaitItem())
      }
      assertEquals(1, repo.all.size)
      assertFalse(vm.state.value.isImporting)
    }

  // ---- QuickAddViewModel --------------------------------------------------------------------

  private val args =
    QuickAddArgs(
      amountMinor = 123_450,
      merchant = "BIG BAZAAR",
      originalSms = "Rs 1234.50 debited at BIG BAZAAR",
      bank = "HASTIB",
      type = TransactionType.DEBIT,
      timestamp = ts,
    )

  @Test
  fun `quick add is prefilled from the notification`() {
    val vm = QuickAddViewModel(FakeExpenseRepository(), TestTime.CLOCK)
    vm.initialize(args)
    val s = vm.uiState.value
    assertEquals("1234.50", s.amount)
    assertEquals("BIG BAZAAR", s.merchant)
    assertEquals("HASTIB", s.bank)
    assertEquals(ts, s.timestamp)
  }

  @Test
  fun `a second notification replaces the first transaction`() {
    val vm = QuickAddViewModel(FakeExpenseRepository(), TestTime.CLOCK)
    vm.initialize(args)
    vm.updateTitle("typed note")
    vm.initialize(args.copy(amountMinor = 5_000, merchant = "UBER"))
    assertEquals("50.00", vm.uiState.value.amount)
    assertEquals("UBER", vm.uiState.value.merchant)
    assertEquals("", vm.uiState.value.title)
  }

  @Test
  fun `saving stores the categorised expense with the original time and sms key`() =
    runTest(main.dispatcher) {
      val repo = FakeExpenseRepository()
      val vm = QuickAddViewModel(repo, TestTime.CLOCK)
      vm.initialize(args)
      vm.updateCategory(ExpenseCategory.SHOPPING)
      vm.updateTitle("  groceries ")
      vm.saveExpense()

      val saved = repo.all.single()
      assertEquals(Money.parse("1234.50"), saved.amount)
      assertEquals("Shopping", saved.category)
      assertEquals("groceries", saved.title)
      assertEquals(ts, saved.timestamp)
      assertNotNull(saved.dedupeKey)
      assertTrue(vm.uiState.value.isSaved)
    }

  @Test
  fun `saving the same message twice is refused`() =
    runTest(main.dispatcher) {
      val repo = FakeExpenseRepository()
      val vm = QuickAddViewModel(repo, TestTime.CLOCK)
      vm.initialize(args)
      vm.saveExpense()
      vm.initialize(args)
      vm.saveExpense()

      assertEquals(1, repo.all.size)
      assertEquals("This message is already recorded", vm.uiState.value.errorMessage)
      assertFalse(vm.uiState.value.isSaved)
    }

  @Test
  fun `validation rejects empty, zero and malformed amounts and blank merchants`() =
    runTest(main.dispatcher) {
      val repo = FakeExpenseRepository()
      val vm = QuickAddViewModel(repo, TestTime.CLOCK)
      vm.updateMerchant("SHOP")
      listOf("", "0", "0.00", ".").forEach {
        vm.updateAmount(it)
        vm.saveExpense()
        assertEquals("Please enter a valid amount", vm.uiState.value.errorMessage)
      }
      vm.updateAmount("10")
      vm.updateMerchant("   ")
      vm.saveExpense()
      assertEquals("Please enter a merchant/description", vm.uiState.value.errorMessage)
      assertTrue(repo.all.isEmpty())
    }

  @Test
  fun `amount input only accepts digits with at most two decimals`() {
    val vm = QuickAddViewModel(FakeExpenseRepository(), TestTime.CLOCK)
    vm.updateAmount("12.5")
    vm.updateAmount("12.567") // rejected, keeps 12.5
    vm.updateAmount("1e5") // rejected
    vm.updateAmount("-3") // rejected
    assertEquals("12.5", vm.uiState.value.amount)
    vm.updateAmount("1234567890123") // 13 digits rejected
    assertEquals("12.5", vm.uiState.value.amount)
  }

  @Test
  fun `a manual entry without an sms uses the clock and has no dedupe key`() =
    runTest(main.dispatcher) {
      val repo = FakeExpenseRepository()
      val vm = QuickAddViewModel(repo, TestTime.CLOCK)
      vm.updateAmount("45")
      vm.updateMerchant("Tea")
      vm.saveExpense()
      val saved = repo.all.single()
      assertEquals(TestTime.NOW.toEpochMilli(), saved.timestamp)
      assertNull(saved.dedupeKey)
    }

  // ---- ScanViewModel ------------------------------------------------------------------------

  private fun scanVm(
    inbox: FakeSmsInbox,
    repo: FakeExpenseRepository = FakeExpenseRepository()
  ): ScanViewModel {
    val senders = FakeSenderSettings()
    return ScanViewModel(
      TestTime.RANGES,
      ScanSmsInbox(inbox, SmsParser(), senders, repo),
      SaveScannedTransactions(repo),
    )
  }

  @Test
  fun `scan then save stores new transactions and a second save finds nothing new`() =
    runTest(main.dispatcher) {
      val inbox =
        FakeSmsInbox(
          listOf(
            RawSms("VM-HDFCBK-S", "Rs 100.00 debited at A", ts),
            RawSms("VM-HDFCBK-S", "Rs 200.00 debited at B", ts + 1000),
            RawSms("VM-HDFCBK-S", "Your OTP is 1", ts + 2000),
          )
        )
      val repo = FakeExpenseRepository()
      val vm = scanVm(inbox, repo)

      vm.scan()
      assertEquals(3, vm.uiState.value.results.size)
      assertEquals(2, vm.uiState.value.detectedCount)
      assertEquals(2, vm.uiState.value.newCount)

      vm.saveDetected()
      assertEquals(2, repo.all.size)
      assertEquals("Saved 2 expenses", vm.uiState.value.saveResult)
      assertEquals(0, vm.uiState.value.newCount) // refreshed by the automatic rescan

      vm.saveDetected()
      assertEquals("Nothing new to save", vm.uiState.value.saveResult)
      assertEquals(2, repo.all.size)
    }

  @Test
  fun `scan explains a missing sms permission`() =
    runTest(main.dispatcher) {
      val vm = scanVm(FakeSmsInbox().apply { failWith = SecurityException("denied") })
      vm.scan()
      assertEquals("Allow SMS access to scan the inbox", vm.uiState.value.scanError)
      assertFalse(vm.uiState.value.isScanning)
    }

  @Test
  fun `changing the scan dates clears stale results`() =
    runTest(main.dispatcher) {
      val vm = scanVm(FakeSmsInbox(listOf(RawSms("VM-HDFCBK-S", "Rs 1.00 debited at A", ts))))
      vm.scan()
      assertEquals(1, vm.uiState.value.results.size)
      vm.updateStartDate(TestTime.millis(2026, 3, 2))
      assertTrue(vm.uiState.value.results.isEmpty())
      assertTrue(vm.lastDayMillis() >= vm.uiState.value.range.startMillis)
    }

  // ---- SendersViewModel ---------------------------------------------------------------------

  @Test
  fun `adding a sender validates input and rejects duplicates`() =
    runTest(main.dispatcher) {
      val settings = FakeSenderSettings(listOf("HDFCBK"))
      val vm = SendersViewModel(settings)

      vm.add()
      assertNotNull(vm.state.value.message)

      vm.updateInput("kotakb")
      assertNull(vm.state.value.message)
      vm.add()
      assertEquals(listOf("HDFCBK", "kotakb"), vm.senders.first())
      assertEquals("", vm.state.value.input)

      vm.updateInput("KOTAKB")
      vm.add()
      assertEquals("Already in the list", vm.state.value.message)

      vm.remove("HDFCBK")
      assertEquals(listOf("kotakb"), vm.senders.first())
      vm.resetToDefaults()
      assertEquals(listOf("HDFCBK"), vm.senders.first())
    }
}
