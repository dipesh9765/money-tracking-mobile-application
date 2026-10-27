package com.expenseinterceptor.domain.usecase

import com.expenseinterceptor.domain.port.DetectedTransaction
import com.expenseinterceptor.domain.port.ExpenseNotifier
import com.expenseinterceptor.domain.port.SenderSettings
import com.expenseinterceptor.domain.sms.ParseResult
import com.expenseinterceptor.domain.sms.SmsParser
import javax.inject.Inject

/** Decides what to do with an SMS that just arrived: notify the user if it is a transaction. */
class HandleIncomingSms
@Inject
constructor(
  private val parser: SmsParser,
  private val senders: SenderSettings,
  private val notifier: ExpenseNotifier,
) {
  /** Returns how many messages produced a notification. */
  operator fun invoke(sender: String, body: String, timestamp: Long): Boolean {
    val result = parser.parse(sender, body, senders.senders.value)
    if (result !is ParseResult.Parsed) return false
    notifier.notifyDetected(DetectedTransaction(result.transaction, body, timestamp))
    return true
  }
}
