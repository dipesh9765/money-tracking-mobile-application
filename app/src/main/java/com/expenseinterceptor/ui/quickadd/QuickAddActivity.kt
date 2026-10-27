package com.expenseinterceptor.ui.quickadd

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.expenseinterceptor.ui.theme.ExpenseInterceptorTheme
import com.expenseinterceptor.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint

/**
 * Transparent activity that displays the Quick Add expense dialog. Launched from the notification
 * when a bank SMS is detected.
 */
@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {

  private val viewModel: QuickAddViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Extract data from intent (passed from notification)
    val amount = intent.getDoubleExtra(NotificationHelper.Extras.AMOUNT, 0.0).takeIf { it > 0 }
    val merchant = intent.getStringExtra(NotificationHelper.Extras.MERCHANT)
    val originalSms = intent.getStringExtra(NotificationHelper.Extras.ORIGINAL_SMS)
    val bank = intent.getStringExtra(NotificationHelper.Extras.BANK)
    val transactionType = intent.getStringExtra(NotificationHelper.Extras.TRANSACTION_TYPE)

    // Initialize ViewModel with intent data
    viewModel.initialize(amount, merchant, originalSms, bank, transactionType)

    setContent {
      ExpenseInterceptorTheme {
        QuickAddScreen(
          viewModel = viewModel,
          onDismiss = { finish() },
          onSaved = {
            Toast.makeText(this, "Expense saved successfully!", Toast.LENGTH_SHORT).show()
            finish()
          }
        )
      }
    }
  }
}
