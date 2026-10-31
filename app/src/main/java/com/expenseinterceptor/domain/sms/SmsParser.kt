package com.expenseinterceptor.domain.sms

import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.model.TransactionType
import javax.inject.Inject

/** A bank SMS understood as one transaction. */
data class ParsedTransaction(
  val amount: Money,
  val merchant: String,
  val bank: String,
  val type: TransactionType,
)

/** Why a message was not turned into a transaction. */
enum class IgnoreReason {
  SENDER_NOT_ALLOWED,
  NOT_A_TRANSACTION,
  AMOUNT_NOT_FOUND,
}

sealed interface ParseResult {
  data class Parsed(val transaction: ParsedTransaction) : ParseResult

  data class Ignored(val reason: IgnoreReason) : ParseResult
}

/**
 * Turns the text of a bank SMS into a [ParsedTransaction].
 *
 * Pure Kotlin with no Android dependency so it can be exercised by table-driven unit tests. The
 * rules are deliberately conservative: a message must name a sender the user allowed, look like a
 * transaction (not an OTP or an offer) and contain a currency-marked amount that is not a balance
 * or limit.
 */
class SmsParser @Inject constructor() {

  fun parse(sender: String, body: String, allowedSenders: Collection<String>): ParseResult {
    if (!isAllowedSender(sender, allowedSenders)) {
      return ParseResult.Ignored(IgnoreReason.SENDER_NOT_ALLOWED)
    }
    val text = body.replace(WHITESPACE, " ").trim()
    val type = detectType(text) ?: return ParseResult.Ignored(IgnoreReason.NOT_A_TRANSACTION)
    val amount = extractAmount(text) ?: return ParseResult.Ignored(IgnoreReason.AMOUNT_NOT_FOUND)
    return ParseResult.Parsed(
      ParsedTransaction(
        amount = amount,
        merchant = extractMerchant(text, type),
        bank = extractBank(sender),
        type = type,
      )
    )
  }

  /**
   * Sender ids look like `VM-HDFCBK-S`: a rotating operator prefix, a stable bank id and an
   * optional category suffix. Users configure only the stable part, so matching is a
   * case-insensitive substring test. Blank patterns are ignored; they would match everything.
   */
  fun isAllowedSender(sender: String, allowedSenders: Collection<String>): Boolean =
    allowedSenders.any { pattern ->
      val trimmed = pattern.trim()
      trimmed.isNotEmpty() && sender.contains(trimmed, ignoreCase = true)
    }

  /**
   * Returns the direction named first in the message, or null if it is not a transaction.
   *
   * A UPI debit often reads "debited from your account ... credited to MERCHANT", so the earliest
   * keyword wins rather than any credit keyword. "Credit card" and "debit card" are product names,
   * not directions, and are removed first.
   */
  fun detectType(text: String): TransactionType? {
    if (OTP.containsMatchIn(text)) return null
    val scrubbed = CARD_PRODUCT.replace(text, " ")
    val debit = DEBIT_WORDS.find(scrubbed)?.range?.first ?: Int.MAX_VALUE
    val credit = CREDIT_WORDS.find(scrubbed)?.range?.first ?: Int.MAX_VALUE
    return when {
      debit == Int.MAX_VALUE && credit == Int.MAX_VALUE -> null
      debit <= credit -> TransactionType.DEBIT
      else -> TransactionType.CREDIT
    }
  }

  /** First currency-marked amount that is not a balance, limit or due figure. */
  fun extractAmount(text: String): Money? =
    AMOUNT.findAll(text)
      .filterNot { match -> NOT_THE_TRANSACTION.containsMatchIn(leadIn(text, match.range.first)) }
      .firstNotNullOfOrNull { match -> Money.parse(match.groupValues[1]) }
      ?.takeIf { it.isPositive }

  fun extractMerchant(text: String, type: TransactionType): String {
    val pattern = if (type == TransactionType.CREDIT) CREDIT_MERCHANT else DEBIT_MERCHANT
    return pattern
      .findAll(text)
      .map { it.groupValues[1].trim().take(MAX_MERCHANT_LENGTH) }
      .firstOrNull { candidate ->
        candidate.length >= MIN_MERCHANT_LENGTH && !NOT_A_MERCHANT.containsMatchIn(candidate)
      } ?: UNKNOWN_MERCHANT
  }

  /**
   * Bank id from a sender such as `CF-HASTIB-S` (`HASTIB`) or `VM-IDFCFB` (`IDFCFB`). Numeric
   * senders (a phone number) and empty ids give [UNKNOWN_BANK].
   */
  fun extractBank(sender: String): String {
    val parts = sender.trim().split('-').filter { it.isNotBlank() }
    val core =
      when {
        parts.size >= PREFIX_ID_SUFFIX -> parts[parts.size - 2]
        parts.size == 2 -> parts[1]
        else -> parts.firstOrNull()
      }
    val bank = core?.trim()?.uppercase()
    return if (bank.isNullOrEmpty() || bank.all { it.isDigit() || it == '+' }) UNKNOWN_BANK
    else bank
  }

  private fun leadIn(text: String, index: Int): String =
    text.substring(maxOf(0, index - LEAD_IN_CHARS), index)

  companion object {
    const val UNKNOWN_MERCHANT = "Unknown Merchant"
    const val UNKNOWN_BANK = "Unknown Bank"

    /** Parts in a sender like `CF-HASTIB-S`: operator prefix, bank id, category suffix. */
    private const val PREFIX_ID_SUFFIX = 3
    private const val LEAD_IN_CHARS = 28
    private const val MIN_MERCHANT_LENGTH = 2
    private const val MAX_MERCHANT_LENGTH = 60

    private val WHITESPACE = Regex("""\s+""")
    private val OTP =
      Regex(
        """\b(otp|one[- ]time password|verification code|do not share)\b""",
        RegexOption.IGNORE_CASE
      )
    private val CARD_PRODUCT = Regex("""\b(credit|debit|cr|dr)\s+card\b""", RegexOption.IGNORE_CASE)
    private val DEBIT_WORDS =
      Regex(
        """\b(debited|debit|spent|paid|purchase|withdrawn|withdrawal|sent|payment|dr)\b""",
        RegexOption.IGNORE_CASE,
      )
    private val CREDIT_WORDS =
      Regex(
        """\b(credited|credit|received|deposited|refund(?:ed)?|cr)\b""",
        RegexOption.IGNORE_CASE
      )

    // Digits with any grouping, so 1234.56, 1,234.56 and the Indian 1,23,456.00 all parse in full.
    private val AMOUNT =
      Regex("""(?:Rs\.?|INR|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val NOT_THE_TRANSACTION =
      Regex(
        """\b(avl|avail\w*|bal\w*|limit|outstanding|total due|min(?:imum)? due|available)\b[^0-9]*$""",
        RegexOption.IGNORE_CASE,
      )

    private const val MERCHANT_STOP =
      """(?=\s+(?:on|ref|upi|via|avl|bal|using|from|date|txn|if|not|call)\b|[.,;(]|$)"""
    private const val MERCHANT_BODY = """([A-Za-z0-9@_&' -][A-Za-z0-9@._&' -]*?)"""
    private val DEBIT_MERCHANT =
      Regex(
        """(?:\bat|\bto|\bfor|\bVPA|\binfo[:-])\s+$MERCHANT_BODY$MERCHANT_STOP""",
        RegexOption.IGNORE_CASE
      )
    private val CREDIT_MERCHANT =
      Regex(
        """(?:\bfrom|\bby|\bVPA|\binfo[:-])\s+$MERCHANT_BODY$MERCHANT_STOP""",
        RegexOption.IGNORE_CASE
      )
    private val NOT_A_MERCHANT =
      Regex("""^(your|you|a/?c|ac|account|xx|x+\d|card|the|us)\b""", RegexOption.IGNORE_CASE)
  }
}
