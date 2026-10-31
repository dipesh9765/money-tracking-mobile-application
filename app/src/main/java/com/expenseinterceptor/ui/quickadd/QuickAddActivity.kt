package com.expenseinterceptor.ui.quickadd

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.expenseinterceptor.ui.theme.ExpenseInterceptorTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Transparent activity that shows the Quick Add dialog. Launched from the notification when a bank
 * SMS is detected.
 */
@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {

  private val viewModel: QuickAddViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // After a rotation or process restore the view model already holds what the user typed.
    if (savedInstanceState == null) QuickAddArgs.from(intent)?.let(viewModel::initialize)

    setContent {
      ExpenseInterceptorTheme {
        QuickAddScreen(
          viewModel = viewModel,
          onDismiss = { finish() },
          onSaved = {
            Toast.makeText(this, "Expense saved", Toast.LENGTH_SHORT).show()
            finish()
          },
        )
      }
    }
  }

  /**
   * The activity is single-instance: tapping a second notification while it is open delivers the
   * new transaction here instead of creating a new activity. Without this the dialog kept showing
   * (and saving) the first transaction.
   */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    QuickAddArgs.from(intent)?.let(viewModel::initialize)
  }
}
