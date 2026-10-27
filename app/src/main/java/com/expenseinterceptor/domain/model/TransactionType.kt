package com.expenseinterceptor.domain.model

/** Direction of money relative to the user's account. */
enum class TransactionType {
  DEBIT,
  CREDIT;

  companion object {
    /** Lenient parse for stored and imported text; anything unrecognised is a debit. */
    fun fromText(text: String?): TransactionType =
      if (text.equals(CREDIT.name, ignoreCase = true)) CREDIT else DEBIT
  }
}
