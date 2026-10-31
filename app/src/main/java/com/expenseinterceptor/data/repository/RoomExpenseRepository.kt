package com.expenseinterceptor.data.repository

import com.expenseinterceptor.data.local.ExpenseDao
import com.expenseinterceptor.data.local.toDomain
import com.expenseinterceptor.data.local.toEntity
import com.expenseinterceptor.domain.model.DateRange
import com.expenseinterceptor.domain.model.Expense
import com.expenseinterceptor.domain.repository.BulkSaveResult
import com.expenseinterceptor.domain.repository.ExpenseRepository
import com.expenseinterceptor.domain.repository.SaveResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [ExpenseRepository] backed by Room. */
@Singleton
class RoomExpenseRepository @Inject constructor(private val dao: ExpenseDao) : ExpenseRepository {

  override fun observe(range: DateRange): Flow<List<Expense>> =
    dao.observeRange(range.startMillis, range.endExclusiveMillis).map { rows ->
      rows.map { it.toDomain() }
    }

  override suspend fun list(range: DateRange): List<Expense> =
    dao.listRange(range.startMillis, range.endExclusiveMillis).map { it.toDomain() }

  override suspend fun save(expense: Expense): SaveResult {
    val id = dao.insert(expense.toEntity())
    return if (id == DUPLICATE_ROW) SaveResult.Duplicate else SaveResult.Saved(id)
  }

  override suspend fun saveAll(expenses: List<Expense>): BulkSaveResult {
    if (expenses.isEmpty()) return BulkSaveResult(saved = 0, skippedDuplicates = 0)
    val saved = dao.insertAllIfAbsent(expenses.map { it.toEntity() })
    return BulkSaveResult(saved = saved, skippedDuplicates = expenses.size - saved)
  }

  override suspend fun update(expense: Expense) = dao.update(expense.toEntity())

  override suspend fun delete(id: Long) = dao.deleteById(id)

  override suspend fun deleteAll() = dao.deleteAll()

  override suspend fun existingDedupeKeys(keys: Collection<String>): Set<String> =
    keys
      .distinct()
      .chunked(SQLITE_VARIABLE_LIMIT)
      .flatMap { chunk -> dao.existingKeys(chunk) }
      .toSet()

  private companion object {
    const val DUPLICATE_ROW = -1L

    // SQLite allows 999 bound variables per statement on older Android versions.
    const val SQLITE_VARIABLE_LIMIT = 900
  }
}
