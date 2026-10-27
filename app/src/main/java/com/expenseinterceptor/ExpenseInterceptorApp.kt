package com.expenseinterceptor

import android.app.Application
import com.expenseinterceptor.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Expense Interceptor. Annotated with @HiltAndroidApp to enable Hilt
 * dependency injection.
 */
@HiltAndroidApp
class ExpenseInterceptorApp : Application() {

  override fun onCreate() {
    super.onCreate()

    // Create notification channel for expense alerts
    NotificationHelper.createNotificationChannel(this)
  }
}
