package com.expenseinterceptor.di

import android.content.Context
import androidx.room.Room
import com.expenseinterceptor.data.local.ExpenseDao
import com.expenseinterceptor.data.local.ExpenseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

/** Application-wide infrastructure: the database, the clock and the time zone. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides
  @Singleton
  fun provideExpenseDatabase(@ApplicationContext context: Context): ExpenseDatabase =
    Room.databaseBuilder(context, ExpenseDatabase::class.java, ExpenseDatabase.DATABASE_NAME)
      .addMigrations(ExpenseDatabase.MIGRATION_1_2, ExpenseDatabase.MIGRATION_2_3)
      .build()

  @Provides fun provideExpenseDao(database: ExpenseDatabase): ExpenseDao = database.expenseDao()

  @Provides fun provideClock(): Clock = Clock.systemUTC()

  @Provides fun provideZone(): ZoneId = ZoneId.systemDefault()
}
