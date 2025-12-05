package com.expenseinterceptor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.expenseinterceptor.config.SmsParserConfig
import com.expenseinterceptor.util.NotificationHelper
import com.expenseinterceptor.util.SmsDebugLogger

/**
 * BroadcastReceiver that listens for incoming SMS messages.
 * 
 * Filters messages based on configured sender IDs and extracts
 * transaction details to show a categorization notification.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Verify it's an SMS received intent
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        Log.d(TAG, "SMS received, processing...")
        SmsDebugLogger.log(
            sender = "N/A",
            preview = "Incoming SMS broadcast",
            stage = "receive",
            outcome = "received"
        )

        // Extract SMS messages from intent
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        
        if (messages.isNullOrEmpty()) {
            Log.d(TAG, "No messages found in intent")
            return
        }

        // Process each message
        for (smsMessage in messages) {
            val sender = smsMessage.displayOriginatingAddress ?: continue
            val body = smsMessage.messageBody ?: continue

            Log.d(TAG, "Processing SMS from: $sender")

            SmsDebugLogger.log(
                sender = sender,
                preview = body,
                stage = "sender_check",
                outcome = "processing"
            )

            if (!SmsParserConfig.isAllowedSender(sender)) {
                Log.d(TAG, "Sender not in allowed list: $sender")
                SmsDebugLogger.log(
                    sender = sender,
                    preview = body,
                    stage = "sender_check",
                    outcome = "skipped",
                    details = "Sender not allowed"
                )
                continue
            }

            val amount = SmsParserConfig.extractAmount(body)
            if (amount == null) {
                Log.d(TAG, "Could not extract amount from SMS")
                SmsDebugLogger.log(
                    sender = sender,
                    preview = body,
                    stage = "amount_parse",
                    outcome = "skipped",
                    details = "Amount not found"
                )
                continue
            }

            val merchant = SmsParserConfig.extractMerchant(body)
            val bank = SmsParserConfig.extractBank(sender, body)
            val transactionType = SmsParserConfig.detectTransactionType(body)

            Log.d(TAG, "Detected expense: Amount=$amount, Merchant=$merchant")
            SmsDebugLogger.log(
                sender = sender,
                preview = body,
                stage = "success",
                outcome = "expense_detected",
                details = "Amount=$amount, Merchant=$merchant, Bank=$bank, Type=$transactionType"
            )

            NotificationHelper.showExpenseNotification(
                context = context,
                amount = amount,
                merchant = merchant,
                bank = bank,
                transactionType = transactionType,
                originalSms = body
            )
        }
    }
}

