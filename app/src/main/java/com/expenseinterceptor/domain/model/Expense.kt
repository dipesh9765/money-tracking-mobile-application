package com.expenseinterceptor.domain.model

/** Categories offered when classifying a transaction. Stored by [displayName]. */
enum class ExpenseCategory(val displayName: String) {
  FOOD("Food"),
  TRAVEL("Travel"),
  BILLS("Bills"),
  SHOPPING("Shopping"),
  ENTERTAINMENT("Entertainment"),
  HEALTH("Health"),
  OTHER("Other");

  companion object {
    /** Marks a transaction found by a scan that the user has not classified yet. */
    const val UNCATEGORIZED = "Uncategorized"

    fun fromDisplayName(name: String): ExpenseCategory =
      entries.firstOrNull { it.displayName == name } ?: OTHER
  }
}

/** One recorded transaction. */
data class Expense(
  val id: Long = 0,
  val amount: Money,
  val merchant: String,
  val category: String,
  val timestamp: Long,
  val type: TransactionType = TransactionType.DEBIT,
  val bank: String = UNKNOWN_BANK,
  val title: String? = null,
  val originalSms: String? = null,
  /** Stable identity of the source SMS, so the same message is never recorded twice. */
  val dedupeKey: String? = null,
) {
  val isCredit: Boolean
    get() = type == TransactionType.CREDIT

  companion object {
    const val UNKNOWN_BANK = "Unknown Bank"
  }
}
