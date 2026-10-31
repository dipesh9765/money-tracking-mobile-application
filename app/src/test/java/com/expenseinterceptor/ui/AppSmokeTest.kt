package com.expenseinterceptor.ui

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.expenseinterceptor.ui.main.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Boots the real activity with the real Hilt graph, a real Room database and the real view models,
 * then walks the main screens. A wiring mistake anywhere (a missing binding, a crash in a
 * composable, a broken migration) fails here.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w411dp-h2400dp")
class AppSmokeTest {

  private val hilt = HiltAndroidRule(this)
  private val compose = createAndroidComposeRule<MainActivity>()
  private val permissions =
    object : ExternalResource() {
      override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app)
          .grantPermissions(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.POST_NOTIFICATIONS,
          )
      }
    }

  @get:Rule val chain: RuleChain = RuleChain.outerRule(hilt).around(permissions).around(compose)

  @Before fun setUp() = hilt.inject()

  private fun tap(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()

  @Test
  fun `home screen shows the empty state and both tabs work`() {
    compose.onNodeWithText("Expense Interceptor").assertExists()
    compose.onNodeWithText("No expenses yet").assertExists()
    compose.onNodeWithText("Total Spent").assertExists()

    tap("Dashboard")
    compose.onNodeWithText("Time scale").assertExists()
    compose.onNodeWithText("Transaction type").assertExists()

    tap("Transactions")
    compose.onNodeWithText("Total Spent").assertExists()
  }

  @Test
  fun `bank senders screen lists the defaults and lets the user add one`() {
    compose.onNodeWithContentDescription("More options").performClick()
    compose.onNodeWithText("Bank senders").performClick()

    compose.onNodeWithText("HDFCBK").assertIsDisplayed()
    compose.onNodeWithText("Reset to defaults").assertExists()
    compose.onNodeWithContentDescription("Back").performClick()
    compose.onNodeWithText("No expenses yet").assertExists()
  }

  @Test
  fun `scan screen opens and reports an empty inbox`() {
    compose.onNodeWithContentDescription("More options").performClick()
    compose.onNodeWithText("Scan Old SMS").performClick()
    compose.onNodeWithText("Date Range").assertExists()
    compose.onNodeWithText("No results yet.").assertExists()
  }

  @Test
  fun `share dialog offers every export format`() {
    compose.onNodeWithContentDescription("Share or export").performClick()
    listOf("Formatted text", "CSV file", "Excel (.xlsx)", "PDF file").forEach {
      compose.onNodeWithText(it).assertExists()
    }
  }
}
