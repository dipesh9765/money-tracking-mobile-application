package com.expenseinterceptor.domain.usecase

import com.expenseinterceptor.domain.model.ExpenseCategory
import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.model.TransactionType
import com.expenseinterceptor.domain.port.RawSms
import com.expenseinterceptor.domain.sms.IgnoreReason
import com.expenseinterceptor.domain.sms.SmsFingerprint
import com.expenseinterceptor.domain.sms.SmsParser
import com.expenseinterceptor.support.FakeExpenseRepository
import com.expenseinterceptor.support.FakeNotifier
import com.expenseinterceptor.support.FakeSenderSettings
import com.expenseinterceptor.support.FakeSmsInbox
import com.expenseinterceptor.support.TestTime
import com.expenseinterceptor.support.expense
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsUseCasesTest {

  private val ts = TestTime.millis(2026, 3, 10)
  private val debitSms =
    RawSms("VM-HDFCBK-S", "Rs.1,234.50 debited from A/c XX1 at BIG BAZAAR on 10-03", ts)
  private val creditSms =
    RawSms("VM-HDFCBK-S", "Rs 2000.00 credited to your A/c XX1 from RAHUL", ts + 1_000)
  private val otpSms = RawSms("VM-HDFCBK-S", "Your OTP is 123456. Do not share it", ts + 2_000)
  private val strangerSms = RawSms("AD-PROMO", "Rs 99 debited at SHOP", ts + 3_000)

  // ---- HandleIncomingSms --------------------------------------------------------------------

  @Test
  fun `a bank transaction notifies the user with the parsed values`() {
    val notifier = FakeNotifier()
    val handle = HandleIncomingSms(SmsParser(), FakeSenderSettings(), notifier)

    assertTrue(handle(debitSms.sender, debitSms.body, debitSms.timestamp))

    val detected = notifier.notifications.single()
    assertEquals(Money.parse("1234.50"), detected.transaction.amount)
    assertEquals("BIG BAZAAR", detected.transaction.merchant)
    assertEquals(debitSms.body, detected.originalSms)
    assertEquals(ts, detected.timestamp)
  }

  @Test
  fun `otp and unknown senders produce no notification`() {
    val notifier = FakeNotifier()
    val handle = HandleIncomingSms(SmsParser(), FakeSenderSettings(), notifier)
    assertFalse(handle(otpSms.sender, otpSms.body, otpSms.timestamp))
    assertFalse(handle(strangerSms.sender, strangerSms.body, strangerSms.timestamp))
    assertTrue(notifier.notifications.isEmpty())
  }

  @Test
  fun `changing the sender list takes effect immediately`() {
    val notifier = FakeNotifier()
    val senders = FakeSenderSettings(listOf("HDFCBK"))
    val handle = HandleIncomingSms(SmsParser(), senders, notifier)
    assertFalse(handle("VM-KOTAKB-S", "Rs 10.00 debited at SHOP", ts))
    senders.add("KOTAKB")
    assertTrue(handle("VM-KOTAKB-S", "Rs 10.00 debited at SHOP", ts))
  }

  // ---- ScanSmsInbox -------------------------------------------------------------------------

  private fun scanner(inbox: FakeSmsInbox, repo: FakeExpenseRepository = FakeExpenseRepository()) =
    ScanSmsInbox(inbox, SmsParser(), FakeSenderSettings(), repo)

  @Test
  fun `scan classifies every message`() = runBlocking {
    val inbox = FakeSmsInbox(listOf(debitSms, creditSms, otpSms, strangerSms))
    val items = scanner(inbox)(TestTime.RANGES.currentMonth())

    val outcomes = items.associate { it.sms to it.outcome }
    assertTrue(outcomes[debitSms] is ScanOutcome.Detected)
    assertEquals(
      TransactionType.CREDIT,
      (outcomes[creditSms] as ScanOutcome.Detected).transaction.type,
    )
    assertEquals(ScanOutcome.Skipped(IgnoreReason.NOT_A_TRANSACTION), outcomes[otpSms])
    assertEquals("messages from other senders never appear", null, outcomes[strangerSms])
    assertEquals(3, items.size)
  }

  @Test
  fun `scan only reads the requested range`() = runBlocking {
    val old = debitSms.copy(timestamp = TestTime.millis(2026, 1, 5))
    val items = scanner(FakeSmsInbox(listOf(debitSms, old)))(TestTime.RANGES.currentMonth())
    assertEquals(listOf(debitSms), items.map { it.sms })
  }

  @Test
  fun `saving a scan stores uncategorized expenses once and a rescan finds them already saved`() =
    runBlocking {
      val repo = FakeExpenseRepository()
      val inbox = FakeSmsInbox(listOf(debitSms, creditSms, otpSms))
      val scan = scanner(inbox, repo)
      val save = SaveScannedTransactions(repo)
      val range = TestTime.RANGES.currentMonth()

      val first = save(scan(range))
      assertEquals(2, first.saved)
      assertTrue(repo.all.all { it.category == ExpenseCategory.UNCATEGORIZED })
      assertTrue(repo.all.all { it.dedupeKey != null && it.originalSms != null })

      val rescan = scan(range)
      val detected = rescan.map { it.outcome }.filterIsInstance<ScanOutcome.Detected>()
      assertTrue("both are flagged as already saved", detected.all { it.alreadySaved })

      val second = save(rescan)
      assertEquals(0, second.saved)
      assertEquals(2, repo.all.size)
    }

  @Test
  fun `scan failure from a missing permission is surfaced to the caller`() {
    val inbox = FakeSmsInbox(listOf(debitSms)).apply { failWith = SecurityException("no READ_SMS") }
    val result = runCatching { runBlocking { scanner(inbox)(TestTime.RANGES.currentMonth()) } }
    assertTrue(result.exceptionOrNull() is SecurityException)
  }

  // ---- fingerprint --------------------------------------------------------------------------

  @Test
  fun `the same sms always has the same fingerprint, even with different spacing or millis`() {
    val a = SmsFingerprint.of("Rs 100 debited  at SHOP\n", ts)
    val b = SmsFingerprint.of("  Rs 100 debited at SHOP", ts + 42)
    assertEquals(a, b)
    assertEquals(64, a.length)
  }

  @Test
  fun `different text or a different day gives a different fingerprint`() {
    val base = SmsFingerprint.of("Rs 100 debited at SHOP", ts)
    assertNotEquals(base, SmsFingerprint.of("Rs 101 debited at SHOP", ts))
    assertNotEquals(base, SmsFingerprint.of("Rs 100 debited at SHOP", ts + 2 * 86_400_000L))
  }

  // ---- ExpenseSummary -----------------------------------------------------------------------

  @Test
  fun `summary separates spent from received`() {
    val summary =
      ExpenseSummary.of(
        listOf(
          expense("100.00"),
          expense("50.50"),
          expense("1000.00", type = TransactionType.CREDIT),
        )
      )
    assertEquals(Money.parse("150.50"), summary.spent)
    assertEquals(Money.parse("1000.00"), summary.received)
    assertEquals(3, summary.count)
    assertEquals(Money.ofMinor(-84_950), summary.net)
  }
}
