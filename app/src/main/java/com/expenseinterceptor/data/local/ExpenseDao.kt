package com.expenseinterceptor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Data access for expenses. */
@Dao
abstract class ExpenseDao {

  /** Returns the new row id, or -1 if the unique dedupe key already exists. */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insert(expense: ExpenseEntity): Long

  @Query(
    """
    SELECT * FROM expenses
    WHERE timestamp >= :startMillis AND timestamp < :endExclusiveMillis
    ORDER BY timestamp DESC, id DESC
    """
  )
  abstract fun observeRange(startMillis: Long, endExclusiveMillis: Long): Flow<List<ExpenseEntity>>

  @Query(
    """
    SELECT * FROM expenses
    WHERE timestamp >= :startMillis AND timestamp < :endExclusiveMillis
    ORDER BY timestamp DESC, id DESC
    """
  )
  abstract suspend fun listRange(startMillis: Long, endExclusiveMillis: Long): List<ExpenseEntity>

  @Query("SELECT dedupeKey FROM expenses WHERE dedupeKey IN (:keys)")
  abstract suspend fun existingKeys(keys: List<String>): List<String>

  /** How many rows are the same transaction: same second, amount, merchant and direction. */
  @Query(
    """
    SELECT COUNT(*) FROM expenses
    WHERE timestamp >= :secondStart AND timestamp < :secondStart + 1000
      AND amountMinor = :amountMinor AND merchant = :merchant AND transactionType = :type
    """
  )
  abstract suspend fun countEquivalent(
    secondStart: Long,
    amountMinor: Long,
    merchant: String,
    type: String,
  ): Int

  @Update abstract suspend fun update(expense: ExpenseEntity)

  @Query("DELETE FROM expenses WHERE id = :id") abstract suspend fun deleteById(id: Long)

  @Query("DELETE FROM expenses") abstract suspend fun deleteAll()

  /** Inserts rows that are not already present, in one transaction. Returns how many were added. */
  @Transaction
  open suspend fun insertAllIfAbsent(expenses: List<ExpenseEntity>): Int {
    var inserted = 0
    for (expense in expenses) {
      val secondStart = expense.timestamp - Math.floorMod(expense.timestamp, MILLIS_PER_SECOND)
      val equivalent =
        countEquivalent(secondStart, expense.amountMinor, expense.merchant, expense.transactionType)
      if (equivalent == 0 && insert(expense) != -1L) inserted++
    }
    return inserted
  }

  private companion object {
    const val MILLIS_PER_SECOND = 1000L
  }
}
