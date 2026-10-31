package com.expenseinterceptor

import android.app.Application
import com.expenseinterceptor.notification.AndroidExpenseNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Application class; `@HiltAndroidApp` generates the dependency graph. */
@HiltAndroidApp
class ExpenseInterceptorApp : Application() {

  @Inject lateinit var notifier: AndroidExpenseNotifier

  override fun onCreate() {
    super.onCreate()
    notifier.createChannel()
  }
}
