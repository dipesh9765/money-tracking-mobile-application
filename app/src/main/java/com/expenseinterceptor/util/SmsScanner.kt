package com.expenseinterceptor.util

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.expenseinterceptor.config.SmsParserConfig
import com.expenseinterceptor.data.local.ExpenseEntity

/**
 * Utility to scan historical SMS messages for expenses.
 */
object SmsScanner {

    data class ScannedSms(
        val sender: String,
        val body: String,
        val timestamp: Long,
        val processed: Boolean,
        val amount: Double?,
        val merchant: String?,
        val bank: String?,
        val transactionType: String?,
        val reason: String?
    ) {
        fun toExpenseEntity(category: String = "Uncategorized"): ExpenseEntity? {
            if (!processed || amount == null || merchant == null) return null
            return ExpenseEntity(
                amount = amount,
                merchant = merchant,
                category = category,
                originalSms = body,
                timestamp = timestamp,
                bank = bank ?: "Unknown Bank",
                transactionType = transactionType ?: "DEBIT"
            )
        }
    }

    suspend fun scanRange(
        context: Context,
        startMillis: Long,
        endMillis: Long
    ): List<ScannedSms> {
        val contentResolver = context.contentResolver
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.BODY
        )
        val selection = "${Telephony.Sms.DATE} BETWEEN ? AND ?"
        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        val results = mutableListOf<ScannedSms>()

        contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)

            while (cursor.moveToNext()) {
                val sender = cursor.getString(addressIndex) ?: continue
                val timestamp = cursor.getLong(dateIndex)
                val body = cursor.getString(bodyIndex) ?: continue

                if (!SmsParserConfig.isAllowedSender(sender)) {
                    results.add(
                        ScannedSms(
                            sender = sender,
                            body = body,
                            timestamp = timestamp,
                            processed = false,
                            amount = null,
                            merchant = null,
                            bank = null,
                            transactionType = null,
                            reason = "Sender not allowed"
                        )
                    )
                    continue
                }

                val amount = SmsParserConfig.extractAmount(body)
                if (amount == null) {
                    results.add(
                        ScannedSms(
                            sender = sender,
                            body = body,
                            timestamp = timestamp,
                            processed = false,
                            amount = null,
                            merchant = null,
                            bank = null,
                            transactionType = null,
                            reason = "Amount not found"
                        )
                    )
                    continue
                }

                val merchant = SmsParserConfig.extractMerchant(body)
                val bank = SmsParserConfig.extractBank(sender, body)
                val transactionType = SmsParserConfig.detectTransactionType(body)
                results.add(
                    ScannedSms(
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        processed = true,
                        amount = amount,
                        merchant = merchant,
                        bank = bank,
                        transactionType = transactionType,
                        reason = null
                    )
                )
            }
        }

        return results
    }
}

