package com.expenseinterceptor.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for one transaction. Amounts are integer paise; see
 * [com.expenseinterceptor.domain.model.Money].
 */
@Entity(
  tableName = "expenses",
  indices = [Index(value = ["timestamp"]), Index(value = ["dedupeKey"], unique = true)],
)
data class ExpenseEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val amountMinor: Long,
  val merchant: String,
  val category: String,
  val title: String? = null,
  /** Epoch milliseconds of the transaction. */
  val timestamp: Long,
  val originalSms: String? = null,
  val bank: String,
  /** `DEBIT` or `CREDIT`. */
  val transactionType: String,
  /** Unique when present; null rows (manual entries, legacy rows) never conflict. */
  val dedupeKey: String? = null,
)
