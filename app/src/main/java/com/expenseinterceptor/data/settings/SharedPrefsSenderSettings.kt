package com.expenseinterceptor.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.expenseinterceptor.domain.port.SenderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The editable list of bank sender ids, kept in SharedPreferences.
 *
 * It was a Kotlin constant, which forced users to edit source and rebuild to support their bank.
 * The list is read synchronously because the SMS receiver must decide within the broadcast.
 */
@Singleton
class SharedPrefsSenderSettings(private val prefs: SharedPreferences) : SenderSettings {

  @Inject
  constructor(
    @ApplicationContext context: Context
  ) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

  private val _senders = MutableStateFlow(load())
  override val senders: StateFlow<List<String>> = _senders.asStateFlow()

  override fun add(pattern: String): Boolean {
    val cleaned = pattern.trim().take(MAX_PATTERN_LENGTH)
    if (cleaned.isEmpty() || _senders.value.any { it.equals(cleaned, ignoreCase = true) }) {
      return false
    }
    save(_senders.value + cleaned)
    return true
  }

  override fun remove(pattern: String) = save(_senders.value - pattern)

  override fun resetToDefaults() = save(DEFAULTS)

  private fun load(): List<String> =
    prefs.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: DEFAULTS

  private fun save(list: List<String>) {
    prefs.edit().putString(KEY, list.joinToString(SEPARATOR.toString())).apply()
    _senders.value = list
  }

  companion object {
    private const val PREFS_NAME = "sender_settings"
    private const val KEY = "allowed_senders"
    private const val SEPARATOR = '\n'
    private const val MAX_PATTERN_LENGTH = 32

    /** Common Indian bank SMS header ids. Users add their own in the app. */
    val DEFAULTS =
      listOf(
        "HDFCBK",
        "ICICIB",
        "SBIINB",
        "SBIUPI",
        "AXISBK",
        "KOTAKB",
        "IDFCFB",
        "YESBNK",
        "PNBSMS"
      )
  }
}
