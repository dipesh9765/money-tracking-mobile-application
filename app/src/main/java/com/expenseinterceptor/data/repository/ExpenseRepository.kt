package com.expenseinterceptor.data.repository

import com.expenseinterceptor.data.local.ExpenseDao
import com.expenseinterceptor.data.local.ExpenseEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for expense data operations.
 * Abstracts the data source from ViewModels.
 */
@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao
) {

    /**
     * Inserts a new expense.
     * @return The ID of the inserted expense.
     */
    suspend fun insertExpense(expense: ExpenseEntity): Long {
        return expenseDao.insert(expense)
    }

    /**
     * Gets all expenses as a reactive Flow.
     */
    fun getAllExpenses(): Flow<List<ExpenseEntity>> {
        return expenseDao.getAllExpenses()
    }

    /**
     * Gets expenses for the current month.
     */
    fun getCurrentMonthExpenses(): Flow<List<ExpenseEntity>> {
        val (start, end) = getCurrentMonthRange()
        return expenseDao.getExpensesForRange(start, end)
    }

    /**
     * Gets expenses for a specific month.
     * 
     * @param year The year (e.g., 2024)
     * @param month The month (0-11, where 0 is January)
     */
    fun getExpensesForMonth(year: Int, month: Int): Flow<List<ExpenseEntity>> {
        val (start, end) = getMonthRange(year, month)
        return expenseDao.getExpensesForRange(start, end)
    }

    /**
     * Gets expenses for a custom time range.
     */
    fun getExpensesForRange(startTimestamp: Long, endTimestamp: Long): Flow<List<ExpenseEntity>> {
        return expenseDao.getExpensesForRange(startTimestamp, endTimestamp)
    }

    /**
     * Gets expenses for the current month as a list.
     * Useful for CSV export.
     */
    suspend fun getCurrentMonthExpensesList(): List<ExpenseEntity> {
        val (start, end) = getCurrentMonthRange()
        return expenseDao.getExpensesForRangeList(start, end)
    }

    /**
     * Gets expenses for a custom time range as a list.
     */
    suspend fun getExpensesForRangeList(startTimestamp: Long, endTimestamp: Long): List<ExpenseEntity> {
        return expenseDao.getExpensesForRangeList(startTimestamp, endTimestamp)
    }

    /**
     * Gets the total amount spent in the current month.
     */
    fun getCurrentMonthTotal(): Flow<Double> {
        val (start, end) = getCurrentMonthRange()
        return expenseDao.getTotalForRange(start, end)
    }

    /**
     * Gets the total amount spent in a custom range.
     */
    fun getTotalForRange(startTimestamp: Long, endTimestamp: Long): Flow<Double> {
        return expenseDao.getTotalForRange(startTimestamp, endTimestamp)
    }

    /**
     * Deletes an expense by ID.
     */
    suspend fun deleteExpense(expenseId: Long) {
        expenseDao.deleteById(expenseId)
    }

    /**
     * Updates an expense entity.
     */
    suspend fun updateExpense(expense: ExpenseEntity) {
        expenseDao.update(expense)
    }

    /**
     * Deletes all expenses.
     */
    suspend fun deleteAllExpenses() {
        expenseDao.deleteAll()
    }

    /**
     * Gets an expense by ID.
     */
    suspend fun getExpenseById(expenseId: Long): ExpenseEntity? {
        return expenseDao.getById(expenseId)
    }

    /**
     * Calculates the start and end timestamps for the current month.
     */
    private fun getCurrentMonthRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        return getMonthRange(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
    }

    /**
     * Calculates the start and end timestamps for a specific month.
     */
    private fun getMonthRange(year: Int, month: Int): Pair<Long, Long> {
        val startCalendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endCalendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        return Pair(startCalendar.timeInMillis, endCalendar.timeInMillis + 1)
    }
}

