package com.expenseinterceptor.support

import java.io.ByteArrayOutputStream

/**
 * Builds a real GSM SMS-DELIVER PDU so tests drive the platform's own `SmsMessage` parsing instead
 * of mocking it. Supports the part of the 7-bit default alphabet that equals ASCII, plus '@'.
 */
object SmsPdu {
  private const val NOT_PLAIN_ASCII = "\$_[]\\^{|}~`"

  fun deliver(sender: String, text: String): ByteArray {
    val addressDigits = pack(septets(sender))
    val body = pack(septets(text))
    val out = ByteArrayOutputStream()
    out.write(0x00) // no SMSC
    out.write(0x04) // SMS-DELIVER, no user data header
    out.write((septets(sender).size * 7 + 3) / 4) // address length in semi-octets
    out.write(0xD0) // alphanumeric sender
    out.write(addressDigits)
    out.write(0x00) // protocol id
    out.write(0x00) // data coding: GSM 7-bit
    // service centre timestamp 26-03-10 12:00:00, nibble swapped BCD
    listOf(0x62, 0x30, 0x01, 0x21, 0x00, 0x00, 0x00).forEach(out::write)
    out.write(septets(text).size)
    out.write(body)
    return out.toByteArray()
  }

  private fun septets(text: String): List<Int> =
    text.map { c ->
      require(c.code in 0x20..0x7E && c !in NOT_PLAIN_ASCII) {
        "'$c' needs the GSM extension table"
      }
      if (c == '@') 0x00 else c.code
    }

  private fun pack(septets: List<Int>): ByteArray {
    val out = ByteArrayOutputStream()
    var accumulator = 0
    var bits = 0
    for (septet in septets) {
      accumulator = accumulator or (septet shl bits)
      bits += 7
      while (bits >= 8) {
        out.write(accumulator and 0xFF)
        accumulator = accumulator ushr 8
        bits -= 8
      }
    }
    if (bits > 0) out.write(accumulator and 0xFF)
    return out.toByteArray()
  }
}
