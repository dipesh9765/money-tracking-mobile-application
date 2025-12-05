package com.expenseinterceptor.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for expense operations.
 */
@Dao
interface ExpenseDao {

    /**
     * Inserts a new expense into the database.
     * If there's a conflict, replaces the existing entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity): Long

    /**
     * Gets all expenses ordered by timestamp (newest first).
     * Returns a Flow for reactive updates.
     */
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    /**
     * Gets expenses within a specific time range.
     * Useful for filtering by month.
     * 
     * @param startTimestamp Start of the time range (inclusive)
     * @param endTimestamp End of the time range (exclusive)
     */
    @Query("""
        SELECT * FROM expenses 
        WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp 
        ORDER BY timestamp DESC
    """)
    fun getExpensesForRange(startTimestamp: Long, endTimestamp: Long): Flow<List<ExpenseEntity>>

    /**
     * Gets all expenses within a time range as a list (not Flow).
     * Useful for CSV export.
     */
    @Query("""
        SELECT * FROM expenses 
        WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp 
        ORDER BY timestamp DESC
    """)
    suspend fun getExpensesForRangeList(startTimestamp: Long, endTimestamp: Long): List<ExpenseEntity>

    /**
     * Gets the total amount of expenses within a time range.
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM expenses 
        WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp
    """)
    fun getTotalForRange(startTimestamp: Long, endTimestamp: Long): Flow<Double>

    /**
     * Deletes a specific expense by ID.
     */
    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteById(expenseId: Long)

    /**
     * Deletes all expenses.
     */
    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    /**
     * Deletes an expense entity.
     */
    @Delete
    suspend fun delete(expense: ExpenseEntity)

    /**
     * Gets a single expense by ID.
     */
    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun getById(expenseId: Long): ExpenseEntity?

    /**
     * Updates an expense.
     */
    @androidx.room.Update
    suspend fun update(expense: ExpenseEntity)
}

