package com.expenseinterceptor.domain.repository

import com.expenseinterceptor.domain.model.DateRange
import com.expenseinterceptor.domain.model.Expense
import kotlinx.coroutines.flow.Flow

/** Outcome of saving one expense. */
sealed interface SaveResult {
  data class Saved(val id: Long) : SaveResult

  /** An expense from the same source SMS is already stored. */
  data object Duplicate : SaveResult
}

/** Counts reported after a bulk save. */
data class BulkSaveResult(val saved: Int, val skippedDuplicates: Int)

/** Storage for expenses. The domain and presentation layers depend only on this interface. */
interface ExpenseRepository {
  fun observe(range: DateRange): Flow<List<Expense>>

  suspend fun list(range: DateRange): List<Expense>

  suspend fun save(expense: Expense): SaveResult

  /**
   * Saves each expense unless an equivalent one (same SMS key, or same time/amount/merchant)
   * exists.
   */
  suspend fun saveAll(expenses: List<Expense>): BulkSaveResult

  suspend fun update(expense: Expense)

  suspend fun delete(id: Long)

  suspend fun deleteAll()

  /** Which of [keys] are already stored; used to flag duplicates in a scan preview. */
  suspend fun existingDedupeKeys(keys: Collection<String>): Set<String>
}
