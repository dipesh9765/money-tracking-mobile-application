package com.expenseinterceptor.domain.sms

import java.security.MessageDigest

/**
 * Stable identity for a bank SMS, used to refuse a second copy of the same message.
 *
 * The live receiver and the inbox scan see the same message with timestamps that can differ by a
 * few milliseconds, so the key uses only the whole UTC day of the timestamp plus the message text.
 * Bank SMS carry a reference number or balance, so two genuinely different transactions with
 * byte-identical text on the same day are very unlikely; the trade-off is recorded in
 * docs/adr/0003-sms-dedupe-key.md.
 */
object SmsFingerprint {
  private const val MILLIS_PER_DAY = 86_400_000L

  fun of(body: String, timestamp: Long): String {
    val normalized = body.trim().replace(Regex("""\s+"""), " ")
    val input = "sms|${timestamp / MILLIS_PER_DAY}|$normalized"
    return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).toHex()
  }

  private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
