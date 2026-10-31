package com.expenseinterceptor.receiver

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.model.TransactionType
import com.expenseinterceptor.domain.port.SenderSettings
import com.expenseinterceptor.support.SmsPdu
import com.expenseinterceptor.ui.quickadd.QuickAddArgs
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Drives the real receiver, parser, sender settings and notifier with real SMS PDUs and checks the
 * notification that would be shown, including the intent the user taps.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class SmsReceiverTest {

  @get:Rule val hilt = HiltAndroidRule(this)

  private val app: Application = ApplicationProvider.getApplicationContext()
  private val manager: NotificationManager
    get() = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  @Before
  fun setUp() {
    hilt.inject()
    shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
  }

  private fun broadcast(vararg pdus: ByteArray) {
    val intent =
      Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        .putExtra("pdus", arrayOf<Any>(*pdus))
        .putExtra("format", "3gpp")
    SmsReceiver().onReceive(app, intent)
  }

  private fun shown() = shadowOf(manager).allNotifications

  @Test
  fun `a bank sms posts a private notification that opens quick add with the parsed values`() {
    broadcast(
      SmsPdu.deliver("VM-HDFCBK-S", "Rs.1,234.50 debited from A/c XX1234 at BIG BAZAAR on 10-03-26")
    )

    val notification = shown().single()
    assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notification.visibility)
    assertNotNull("lock screen shows a generic version", notification.publicVersion)
    assertTrue(
      "amount must not appear on the lock screen",
      notification.publicVersion.extras.getCharSequence("android.text") == null,
    )

    val tap = shadowOf(notification.contentIntent).savedIntent
    val args = QuickAddArgs.from(tap)!!
    assertEquals(Money.parse("1234.50")!!.minor, args.amountMinor)
    assertEquals("BIG BAZAAR", args.merchant)
    assertEquals("HDFCBK", args.bank)
    assertEquals(TransactionType.DEBIT, args.type)
  }

  @Test
  fun `a multipart sms is joined before parsing`() {
    broadcast(
      SmsPdu.deliver("VM-HDFCBK-S", "Rs 450.00 debited from A/c XX1 at "),
      SmsPdu.deliver("VM-HDFCBK-S", "CAFE COFFEE DAY on 10-03"),
    )
    val args = QuickAddArgs.from(shadowOf(shown().single().contentIntent).savedIntent)!!
    assertEquals(45_000L, args.amountMinor)
    assertEquals("CAFE COFFEE DAY", args.merchant)
  }

  @Test
  fun `otp, offers and unknown senders post nothing`() {
    broadcast(SmsPdu.deliver("VM-HDFCBK-S", "Your OTP for Rs 500 payment is 123456. Do not share"))
    broadcast(SmsPdu.deliver("AD-PROMO", "Rs 99 debited at SHOP"))
    broadcast(SmsPdu.deliver("VM-HDFCBK-S", "Get a loan up to Rs 5,00,000 today"))
    assertTrue(shown().isEmpty())
  }

  @Test
  fun `the same sms delivered twice replaces the notification instead of stacking`() {
    val pdu = SmsPdu.deliver("VM-HDFCBK-S", "Rs 10.00 debited at TEA STALL")
    broadcast(pdu)
    broadcast(pdu)
    assertEquals(1, shown().size)
  }

  @Test
  fun `two different sms in quick succession both notify`() {
    broadcast(SmsPdu.deliver("VM-HDFCBK-S", "Rs 10.00 debited at TEA STALL"))
    broadcast(SmsPdu.deliver("VM-HDFCBK-S", "Rs 20.00 debited at BOOK SHOP"))
    assertEquals(2, shown().size)
  }

  @Test
  fun `a sender added in settings is honoured by the receiver`() {
    broadcast(SmsPdu.deliver("VM-MAHABK-S", "Rs 10.00 debited at TEA STALL"))
    assertTrue(shown().isEmpty())

    val settings =
      EntryPointAccessors.fromApplication(app, SettingsEntryPoint::class.java).senders()
    settings.add("MAHABK")
    broadcast(SmsPdu.deliver("VM-MAHABK-S", "Rs 10.00 debited at TEA STALL"))
    assertEquals(1, shown().size)
  }

  @Test
  fun `no notification without the notification permission, and no crash`() {
    shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    broadcast(SmsPdu.deliver("VM-HDFCBK-S", "Rs 10.00 debited at TEA STALL"))
    assertTrue(shown().isEmpty())
  }

  @Test
  fun `unrelated broadcasts are ignored`() {
    SmsReceiver().onReceive(app, Intent("android.intent.action.BOOT_COMPLETED"))
    assertTrue(shown().isEmpty())
  }

  @dagger.hilt.EntryPoint
  @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
  interface SettingsEntryPoint {
    fun senders(): SenderSettings
  }
}
