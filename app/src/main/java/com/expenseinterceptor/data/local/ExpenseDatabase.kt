package com.expenseinterceptor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Room database for storing expenses. Schemas are exported to `app/schemas`. */
@Database(entities = [ExpenseEntity::class], version = 3, exportSchema = true)
abstract class ExpenseDatabase : RoomDatabase() {

  abstract fun expenseDao(): ExpenseDao

  companion object {
    const val DATABASE_NAME = "expense_interceptor_db"

    /** Adds bank and transactionType columns with safe defaults. */
    val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE expenses ADD COLUMN bank TEXT NOT NULL DEFAULT 'Unknown Bank'")
          db.execSQL(
            "ALTER TABLE expenses ADD COLUMN transactionType TEXT NOT NULL DEFAULT 'DEBIT'"
          )
        }
      }

    /**
     * Money becomes integer paise (`amount REAL` -> `amountMinor INTEGER`), a nullable unique
     * `dedupeKey` is added, and the timestamp is indexed. SQLite cannot change a column type in
     * place, so the table is rebuilt and the rows copied.
     */
    val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS `expenses_new` (" +
              "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
              "`amountMinor` INTEGER NOT NULL, " +
              "`merchant` TEXT NOT NULL, " +
              "`category` TEXT NOT NULL, " +
              "`title` TEXT, " +
              "`timestamp` INTEGER NOT NULL, " +
              "`originalSms` TEXT, " +
              "`bank` TEXT NOT NULL, " +
              "`transactionType` TEXT NOT NULL, " +
              "`dedupeKey` TEXT)"
          )
          db.execSQL(
            "INSERT INTO `expenses_new` (`id`, `amountMinor`, `merchant`, `category`, `title`, " +
              "`timestamp`, `originalSms`, `bank`, `transactionType`, `dedupeKey`) " +
              "SELECT `id`, CAST(ROUND(`amount` * 100) AS INTEGER), `merchant`, `category`, " +
              "`title`, `timestamp`, `originalSms`, `bank`, `transactionType`, NULL " +
              "FROM `expenses`"
          )
          db.execSQL("DROP TABLE `expenses`")
          db.execSQL("ALTER TABLE `expenses_new` RENAME TO `expenses`")
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_expenses_timestamp` ON `expenses` (`timestamp`)"
          )
          db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_expenses_dedupeKey` " +
              "ON `expenses` (`dedupeKey`)"
          )
        }
      }
  }
}
