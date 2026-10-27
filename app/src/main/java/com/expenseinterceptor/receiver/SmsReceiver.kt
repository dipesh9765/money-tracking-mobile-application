package com.expenseinterceptor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.expenseinterceptor.domain.usecase.HandleIncomingSms
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives incoming SMS broadcasts and hands them to [HandleIncomingSms].
 *
 * Nothing about a message is logged: sender, text and amounts are financial data. The long messages
 * banks send arrive as several parts, which are joined before parsing so an amount in the first
 * part and a merchant in the second are read together.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

  @Inject lateinit var handleIncomingSms: HandleIncomingSms

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
    val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

    parts
      .filter { it.displayOriginatingAddress != null }
      .groupBy { it.displayOriginatingAddress!! }
      .forEach { (sender, pieces) ->
        val body = pieces.joinToString(separator = "") { it.messageBody.orEmpty() }
        handleIncomingSms(sender, body, pieces.first().timestampMillis)
      }
  }
}
