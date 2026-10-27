package com.expenseinterceptor.ui.quickadd

import android.content.Intent
import com.expenseinterceptor.domain.model.TransactionType

/**
 * What the notification passes to [QuickAddActivity]. Kept in one place so the writer and the
 * reader cannot drift apart. The amount travels as integer paise, never as a floating point value.
 */
data class QuickAddArgs(
  val amountMinor: Long,
  val merchant: String,
  val originalSms: String,
  val bank: String,
  val type: TransactionType,
  val timestamp: Long,
) {
  fun writeTo(intent: Intent) {
    intent
      .putExtra(AMOUNT_MINOR, amountMinor)
      .putExtra(MERCHANT, merchant)
      .putExtra(ORIGINAL_SMS, originalSms)
      .putExtra(BANK, bank)
      .putExtra(TYPE, type.name)
      .putExtra(TIMESTAMP, timestamp)
  }

  companion object {
    private const val AMOUNT_MINOR = "extra_amount_minor"
    private const val MERCHANT = "extra_merchant"
    private const val ORIGINAL_SMS = "extra_original_sms"
    private const val BANK = "extra_bank"
    private const val TYPE = "extra_transaction_type"
    private const val TIMESTAMP = "extra_timestamp"

    /** Returns null when the intent was not produced by [writeTo] (for example a bare launch). */
    fun from(intent: Intent): QuickAddArgs? {
      if (!intent.hasExtra(AMOUNT_MINOR)) return null
      return QuickAddArgs(
        amountMinor = intent.getLongExtra(AMOUNT_MINOR, 0),
        merchant = intent.getStringExtra(MERCHANT).orEmpty(),
        originalSms = intent.getStringExtra(ORIGINAL_SMS).orEmpty(),
        bank = intent.getStringExtra(BANK).orEmpty(),
        type = TransactionType.fromText(intent.getStringExtra(TYPE)),
        timestamp = intent.getLongExtra(TIMESTAMP, System.currentTimeMillis()),
      )
    }
  }
}
