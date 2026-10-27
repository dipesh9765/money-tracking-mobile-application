package com.expenseinterceptor.di

import com.expenseinterceptor.data.repository.RoomExpenseRepository
import com.expenseinterceptor.data.settings.SharedPrefsSenderSettings
import com.expenseinterceptor.data.sms.ContentResolverSmsInbox
import com.expenseinterceptor.data.transfer.AndroidExportFileStore
import com.expenseinterceptor.data.transfer.AndroidPdfReportWriter
import com.expenseinterceptor.data.transfer.ContentResolverImportSource
import com.expenseinterceptor.data.transfer.ImportSource
import com.expenseinterceptor.domain.port.ExpenseNotifier
import com.expenseinterceptor.domain.port.ExportFileStore
import com.expenseinterceptor.domain.port.PdfReportWriter
import com.expenseinterceptor.domain.port.SenderSettings
import com.expenseinterceptor.domain.port.SmsInbox
import com.expenseinterceptor.domain.repository.ExpenseRepository
import com.expenseinterceptor.notification.AndroidExpenseNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds each port in the domain layer to its Android implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
  @Binds abstract fun expenses(impl: RoomExpenseRepository): ExpenseRepository

  @Binds @Singleton abstract fun senders(impl: SharedPrefsSenderSettings): SenderSettings

  @Binds abstract fun smsInbox(impl: ContentResolverSmsInbox): SmsInbox

  @Binds abstract fun notifier(impl: AndroidExpenseNotifier): ExpenseNotifier

  @Binds abstract fun exportStore(impl: AndroidExportFileStore): ExportFileStore

  @Binds abstract fun pdf(impl: AndroidPdfReportWriter): PdfReportWriter

  @Binds abstract fun importSource(impl: ContentResolverImportSource): ImportSource
}
