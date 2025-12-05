package com.expenseinterceptor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for storing expenses.
 */
@Database(
    entities = [ExpenseEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ExpenseDatabase : RoomDatabase() {

    /**
     * Returns the DAO for expense operations.
     */
    abstract fun expenseDao(): ExpenseDao

    companion object {
        const val DATABASE_NAME = "expense_interceptor_db"

        /** Migration adds bank and transactionType columns with safe defaults. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE expenses ADD COLUMN bank TEXT NOT NULL DEFAULT 'Unknown Bank'"
                )
                database.execSQL(
                    "ALTER TABLE expenses ADD COLUMN transactionType TEXT NOT NULL DEFAULT 'DEBIT'"
                )
            }
        }
    }
}

