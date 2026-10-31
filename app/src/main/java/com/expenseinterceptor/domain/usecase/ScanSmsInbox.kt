package com.expenseinterceptor.domain.usecase

import com.expenseinterceptor.domain.model.DateRange
import com.expenseinterceptor.domain.model.Expense
import com.expenseinterceptor.domain.model.ExpenseCategory
import com.expenseinterceptor.domain.port.RawSms
import com.expenseinterceptor.domain.port.SenderSettings
import com.expenseinterceptor.domain.port.SmsInbox
import com.expenseinterceptor.domain.repository.BulkSaveResult
import com.expenseinterceptor.domain.repository.ExpenseRepository
import com.expenseinterceptor.domain.sms.IgnoreReason
import com.expenseinterceptor.domain.sms.ParseResult
import com.expenseinterceptor.domain.sms.ParsedTransaction
import com.expenseinterceptor.domain.sms.SmsFingerprint
import com.expenseinterceptor.domain.sms.SmsParser
import javax.inject.Inject

/** One inbox message and what the scan made of it. */
data class ScanItem(val sms: RawSms, val outcome: ScanOutcome)

sealed interface ScanOutcome {
  data class Detected(
    val transaction: ParsedTransaction,
    val dedupeKey: String,
    /** Already stored, so saving the scan will skip it. */
    val alreadySaved: Boolean,
  ) : ScanOutcome

  data class Skipped(val reason: IgnoreReason) : ScanOutcome
}

/**
 * Reads past messages and parses them, flagging the ones already stored.
 *
 * Messages from senders the user has not listed are dropped immediately and never reach the result,
 * so personal conversations are not shown or kept in memory by the scan.
 */
class ScanSmsInbox
@Inject
constructor(
  private val inbox: SmsInbox,
  private val parser: SmsParser,
  private val senders: SenderSettings,
  private val repository: ExpenseRepository,
) {
  suspend operator fun invoke(range: DateRange): List<ScanItem> {
    val allowed = senders.senders.value
    val parsed =
      inbox
        .read(range)
        .filter { parser.isAllowedSender(it.sender, allowed) }
        .map { sms ->
          val outcome =
            when (val result = parser.parse(sms.sender, sms.body, allowed)) {
              is ParseResult.Parsed ->
                ScanOutcome.Detected(
                  result.transaction,
                  SmsFingerprint.of(sms.body, sms.timestamp),
                  alreadySaved = false,
                )
              is ParseResult.Ignored -> ScanOutcome.Skipped(result.reason)
            }
          ScanItem(sms, outcome)
        }
    val keys = parsed.mapNotNull { (it.outcome as? ScanOutcome.Detected)?.dedupeKey }
    val existing = repository.existingDedupeKeys(keys)
    return parsed.map { item ->
      val outcome = item.outcome
      if (outcome is ScanOutcome.Detected && outcome.dedupeKey in existing) {
        item.copy(outcome = outcome.copy(alreadySaved = true))
      } else {
        item
      }
    }
  }
}

/** Stores the detected, not yet saved messages from a scan, leaving them uncategorized. */
class SaveScannedTransactions @Inject constructor(private val repository: ExpenseRepository) {
  suspend operator fun invoke(items: List<ScanItem>): BulkSaveResult {
    val expenses =
      items.mapNotNull { item ->
        val detected = item.outcome as? ScanOutcome.Detected
        detected?.takeUnless { it.alreadySaved }?.let { toExpense(item.sms, it) }
      }
    return repository.saveAll(expenses)
  }

  private fun toExpense(sms: RawSms, detected: ScanOutcome.Detected): Expense =
    Expense(
      amount = detected.transaction.amount,
      merchant = detected.transaction.merchant,
      category = ExpenseCategory.UNCATEGORIZED,
      timestamp = sms.timestamp,
      type = detected.transaction.type,
      bank = detected.transaction.bank,
      originalSms = sms.body,
      dedupeKey = detected.dedupeKey,
    )
}
