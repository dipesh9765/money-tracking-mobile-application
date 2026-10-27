package com.expenseinterceptor.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.expenseinterceptor.data.repository.RoomExpenseRepository
import com.expenseinterceptor.domain.model.DateRange
import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.repository.SaveResult
import com.expenseinterceptor.support.TestTime
import com.expenseinterceptor.support.expense
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var db: ExpenseDatabase
  private lateinit var repo: RoomExpenseRepository
  private val march = TestTime.RANGES.currentMonth()

  @Before
  fun setUp() {
    db =
      Room.inMemoryDatabaseBuilder(context, ExpenseDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    repo = RoomExpenseRepository(db.expenseDao())
  }

  @After fun tearDown() = db.close()

  // ---- repository over a real database ------------------------------------------------------

  @Test
  fun `money round trips as exact paise`() = runBlocking {
    repo.save(expense("0.07"))
    repo.save(expense("1234567.89", merchant = "BIG"))
    val amounts = repo.list(march).map { it.amount }
    assertTrue(Money.parse("0.07") in amounts)
    assertTrue(Money.parse("1234567.89") in amounts)
  }

  @Test
  fun `range queries are half open and newest first`() = runBlocking {
    repo.save(expense(merchant = "FIRST", timestamp = march.startMillis))
    repo.save(expense(merchant = "LAST", timestamp = march.endExclusiveMillis - 1))
    repo.save(expense(merchant = "NEXT MONTH", timestamp = march.endExclusiveMillis))
    repo.save(expense(merchant = "PREVIOUS", timestamp = march.startMillis - 1))

    assertEquals(listOf("LAST", "FIRST"), repo.list(march).map { it.merchant })
    assertEquals(listOf("LAST", "FIRST"), repo.observe(march).first().map { it.merchant })
  }

  @Test
  fun `a second row with the same dedupe key is rejected and leaves one row`() = runBlocking {
    val first = repo.save(expense(merchant = "A", dedupeKey = "key-1"))
    val second = repo.save(expense(merchant = "B", dedupeKey = "key-1"))
    assertTrue(first is SaveResult.Saved)
    assertEquals(SaveResult.Duplicate, second)
    assertEquals(listOf("A"), repo.list(march).map { it.merchant })
  }

  @Test
  fun `rows without a dedupe key never conflict`() = runBlocking {
    repeat(3) { repo.save(expense(merchant = "M$it")) }
    assertEquals(3, repo.list(march).size)
  }

  @Test
  fun `saveAll skips rows equivalent to stored ones and counts them`() = runBlocking {
    val ts = TestTime.millis(2026, 3, 10)
    repo.save(expense("10.00", "SHOP", timestamp = ts + 123)) // stored with milliseconds
    val result =
      repo.saveAll(
        listOf(
          expense("10.00", "SHOP", timestamp = ts), // same second, amount, merchant: duplicate
          expense("10.00", "SHOP", timestamp = ts + 5_000), // different second
          expense("11.00", "SHOP", timestamp = ts), // different amount
          expense("10.00", "OTHER", timestamp = ts), // different merchant
        )
      )
    assertEquals(3, result.saved)
    assertEquals(1, result.skippedDuplicates)
    assertEquals(4, repo.list(march).size)
  }

  @Test
  fun `saveAll with duplicates inside the same batch inserts them once`() = runBlocking {
    val row = expense("5.00", "TEA")
    val result = repo.saveAll(listOf(row, row, row))
    assertEquals(1, result.saved)
    assertEquals(2, result.skippedDuplicates)
  }

  @Test
  fun `existing keys are found even above the sqlite variable limit`() = runBlocking {
    repo.save(expense(dedupeKey = "present"))
    val keys = (1..2_500).map { "k$it" } + "present"
    assertEquals(setOf("present"), repo.existingDedupeKeys(keys))
  }

  @Test
  fun `update and delete`() = runBlocking {
    repo.save(expense(merchant = "A", category = "Food"))
    val stored = repo.list(march).single()
    repo.update(stored.copy(category = "Travel"))
    assertEquals("Travel", repo.list(march).single().category)
    repo.delete(stored.id)
    assertTrue(repo.list(march).isEmpty())
    repo.save(expense())
    repo.deleteAll()
    assertTrue(repo.list(march).isEmpty())
  }

  @Test
  fun `empty range is empty`() = runBlocking { assertTrue(repo.list(DateRange(0, 0)).isEmpty()) }

  // ---- migrations ---------------------------------------------------------------------------

  private fun legacyDb(name: String, create: (SQLiteDatabase) -> Unit): File {
    val file = context.getDatabasePath(name)
    file.parentFile?.mkdirs()
    file.delete()
    SQLiteDatabase.openOrCreateDatabase(file, null).use { create(it) }
    return file
  }

  private fun migrated(name: String): ExpenseDatabase =
    Room.databaseBuilder(context, ExpenseDatabase::class.java, name)
      .addMigrations(ExpenseDatabase.MIGRATION_1_2, ExpenseDatabase.MIGRATION_2_3)
      .allowMainThreadQueries()
      .build()

  @Test
  fun `version 2 database migrates to integer paise and keeps every row`() = runBlocking {
    legacyDb("v2.db") { db ->
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
          "`amount` REAL NOT NULL, `merchant` TEXT NOT NULL, `category` TEXT NOT NULL, `title` TEXT, " +
          "`timestamp` INTEGER NOT NULL, `originalSms` TEXT, `bank` TEXT NOT NULL, `transactionType` TEXT NOT NULL)"
      )
      db.execSQL(
        "INSERT INTO expenses VALUES (7, 1234.56, 'BIG BAZAAR', 'Shopping', 'weekly', ${TestTime.millis(2026, 3, 10)}, 'sms body', 'HASTIB', 'DEBIT')"
      )
      db.execSQL(
        "INSERT INTO expenses VALUES (8, ${0.1 + 0.2}, 'FLOATY', 'Food', NULL, ${TestTime.millis(2026, 3, 11)}, NULL, 'S', 'CREDIT')"
      )
      db.execSQL("PRAGMA user_version = 2")
    }

    val migrated = migrated("v2.db")
    val rows = RoomExpenseRepository(migrated.expenseDao()).list(march).sortedBy { it.id }

    assertEquals(listOf(7L, 8L), rows.map { it.id })
    assertEquals(Money.ofMinor(123_456), rows[0].amount)
    assertEquals("BIG BAZAAR", rows[0].merchant)
    assertEquals("weekly", rows[0].title)
    assertEquals("sms body", rows[0].originalSms)
    assertEquals("HASTIB", rows[0].bank)
    assertEquals(Money.ofMinor(30), rows[1].amount) // 0.30000000000000004 rounds to 30 paise
    assertTrue(rows[1].isCredit)
    assertTrue(rows.all { it.dedupeKey == null })

    // the migrated table behaves like a fresh one: unique keys and auto ids work
    val repo = RoomExpenseRepository(migrated.expenseDao())
    assertTrue(repo.save(expense(dedupeKey = "k")) is SaveResult.Saved)
    assertEquals(SaveResult.Duplicate, repo.save(expense(dedupeKey = "k")))
    assertEquals(9L, repo.list(march).maxOf { it.id })
    migrated.close()
  }

  @Test
  fun `version 1 database migrates all the way to version 3`() = runBlocking {
    legacyDb("v1.db") { db ->
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
          "`amount` REAL NOT NULL, `merchant` TEXT NOT NULL, `category` TEXT NOT NULL, `title` TEXT, " +
          "`timestamp` INTEGER NOT NULL, `originalSms` TEXT)"
      )
      db.execSQL(
        "INSERT INTO expenses VALUES (1, 99.5, 'OLD', 'Food', NULL, ${TestTime.millis(2026, 3, 3)}, NULL)"
      )
      db.execSQL("PRAGMA user_version = 1")
    }
    val migrated = migrated("v1.db")
    val row = RoomExpenseRepository(migrated.expenseDao()).list(march).single()
    assertEquals(Money.ofMinor(9_950), row.amount)
    assertEquals("Unknown Bank", row.bank)
    assertEquals(com.expenseinterceptor.domain.model.TransactionType.DEBIT, row.type)
    migrated.close()
  }

  @Test
  fun `the exported schema file exists for the current version`() {
    val schema = File("schemas/com.expenseinterceptor.data.local.ExpenseDatabase/3.json")
    assertTrue("run the build once to export the schema: ${schema.absolutePath}", schema.exists())
  }
}
