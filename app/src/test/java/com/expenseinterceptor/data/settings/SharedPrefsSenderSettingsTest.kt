package com.expenseinterceptor.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPrefsSenderSettingsTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun clear() {
    context.getSharedPreferences("sender_settings", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun `a fresh install starts with the default banks`() {
    assertEquals(
      SharedPrefsSenderSettings.DEFAULTS,
      SharedPrefsSenderSettings(context).senders.value
    )
  }

  @Test
  fun `added senders are trimmed, case insensitively unique and persisted`() {
    val settings = SharedPrefsSenderSettings(context)
    assertTrue(settings.add("  MAHABK "))
    assertFalse(settings.add("mahabk"))
    assertFalse(settings.add("   "))
    assertEquals("MAHABK", settings.senders.value.last())

    val reopened = SharedPrefsSenderSettings(context)
    assertTrue("MAHABK" in reopened.senders.value)
  }

  @Test
  fun `removing and resetting`() {
    val settings = SharedPrefsSenderSettings(context)
    settings.remove("HDFCBK")
    assertFalse("HDFCBK" in SharedPrefsSenderSettings(context).senders.value)
    settings.resetToDefaults()
    assertEquals(
      SharedPrefsSenderSettings.DEFAULTS,
      SharedPrefsSenderSettings(context).senders.value
    )
  }

  @Test
  fun `an empty saved list stays empty so nothing is intercepted`() {
    val settings = SharedPrefsSenderSettings(context)
    SharedPrefsSenderSettings.DEFAULTS.forEach(settings::remove)
    assertTrue(SharedPrefsSenderSettings(context).senders.value.isEmpty())
  }

  @Test
  fun `overlong patterns are cut to a sane length`() {
    val settings = SharedPrefsSenderSettings(context)
    settings.add("X".repeat(500))
    assertEquals(32, settings.senders.value.last().length)
  }
}
