package com.expenseinterceptor.domain.sms

import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

  private val parser = SmsParser()
  private val allowed = listOf("HASTIB", "IDFCFB", "SBIUPI", "HDFCBK")

  private fun parsed(sender: String, body: String): ParsedTransaction {
    val result = parser.parse(sender, body, allowed)
    assertTrue("expected a transaction but got $result", result is ParseResult.Parsed)
    return (result as ParseResult.Parsed).transaction
  }

  private fun ignoredReason(sender: String, body: String): IgnoreReason {
    val result = parser.parse(sender, body, allowed)
    assertTrue("expected Ignored but got $result", result is ParseResult.Ignored)
    return (result as ParseResult.Ignored).reason
  }

  // ---- amounts -------------------------------------------------------------------------------

  @Test
  fun `parses every common amount format in full`() {
    val cases =
      mapOf(
        "Rs.1234.56 debited from A/c XX1234 at SHOP" to "1234.56",
        "Rs 1,234.56 debited at SHOP" to "1234.56",
        "INR 1234 debited at SHOP" to "1234.00",
        "INR1,234.00 debited at SHOP" to "1234.00",
        "₹1,234 debited at SHOP" to "1234.00",
        "Rs.1,23,456.00 debited at SHOP" to "123456.00",
        "Rs. 99.5 debited at SHOP" to "99.50",
        "Rs.500. debited at SHOP" to "500.00",
      )
    cases.forEach { (body, expected) ->
      assertEquals(body, expected, parsed("VM-HDFCBK-S", body).amount.toPlainString())
    }
  }

  @Test
  fun `balance and limit figures are never taken as the amount`() {
    val tx = parsed("VM-HDFCBK-S", "Avl Bal Rs 5,000.00. Rs 200.00 debited at SHOP on 01-01")
    assertEquals("200.00", tx.amount.toPlainString())
    val tx2 = parsed("VM-HDFCBK-S", "Rs 750.00 spent on card at CAFE. Available limit Rs 40,000.00")
    assertEquals("750.00", tx2.amount.toPlainString())
  }

  @Test
  fun `message with only a balance figure has no amount`() {
    assertEquals(
      IgnoreReason.AMOUNT_NOT_FOUND,
      ignoredReason("VM-HDFCBK-S", "Debit alert. Avl Bal Rs 5,000.00"),
    )
  }

  // ---- direction -----------------------------------------------------------------------------

  @Test
  fun `upi debit that mentions credited to the payee is a debit`() {
    val tx =
      parsed(
        "VM-IDFCFB-S",
        "Rs 500.00 debited from A/c XX1234 on 01-01-26. Credited to SWIGGY via UPI Ref 123456",
      )
    assertEquals(TransactionType.DEBIT, tx.type)
    assertEquals("500.00", tx.amount.toPlainString())
  }

  @Test
  fun `credit messages are credits`() {
    assertEquals(
      TransactionType.CREDIT,
      parsed("CF-HASTIB-S", "Rs 2,000.00 credited to your A/c XX1234 from RAHUL on 01-01").type,
    )
    assertEquals(
      TransactionType.CREDIT,
      parsed("CF-HASTIB-S", "Rs.1500 refund received from AMAZON").type,
    )
  }

  @Test
  fun `credit card as a product name does not make a debit a credit`() {
    val tx = parsed("VM-HDFCBK-S", "Rs 999.00 spent on HDFC Credit Card XX4321 at ZOMATO on 02-01")
    assertEquals(TransactionType.DEBIT, tx.type)
    assertEquals("ZOMATO", tx.merchant)
  }

  @Test
  fun `cr and dr markers are understood`() {
    assertEquals(TransactionType.CREDIT, parsed("VM-HDFCBK-S", "A/c XX1 Rs.300.00 Cr by JOHN").type)
    assertEquals(TransactionType.DEBIT, parsed("VM-HDFCBK-S", "A/c XX1 Rs.300.00 Dr to JOHN").type)
  }

  // ---- what is not a transaction -------------------------------------------------------------

  @Test
  fun `otp and promotional messages are ignored`() {
    assertEquals(
      IgnoreReason.NOT_A_TRANSACTION,
      ignoredReason("VM-HDFCBK-S", "Your OTP for Rs 500 payment is 123456. Do not share it"),
    )
    assertEquals(
      IgnoreReason.NOT_A_TRANSACTION,
      ignoredReason("VM-HDFCBK-S", "Get a pre-approved loan up to Rs 5,00,000 today!"),
    )
  }

  @Test
  fun `messages from senders that are not allowed are ignored`() {
    assertEquals(
      IgnoreReason.SENDER_NOT_ALLOWED,
      ignoredReason("AD-RANDOM", "Rs 500 debited at SHOP"),
    )
  }

  @Test
  fun `blank sender patterns never match everything`() {
    assertFalse(parser.isAllowedSender("AD-ANYTHING", listOf("", "   ")))
    assertFalse(parser.isAllowedSender("AD-ANYTHING", emptyList()))
  }

  @Test
  fun `sender matching is case insensitive and ignores the rotating prefix`() {
    assertTrue(parser.isAllowedSender("jm-hastib-s", listOf("HASTIB")))
    assertTrue(parser.isAllowedSender("VM-IDFCFB", listOf(" idfcfb ")))
  }

  // ---- merchant and bank ---------------------------------------------------------------------

  @Test
  fun `merchant is extracted for common phrasings`() {
    val cases =
      mapOf(
        "Rs 100.00 debited at AMAZON PAY on 01-01" to "AMAZON PAY",
        "Rs 100.00 paid to rahul@okhdfc Ref 123" to "rahul@okhdfc",
        "Rs 100.00 sent to SWIGGY. UPI Ref 1" to "SWIGGY",
        "Rs 100.00 spent for Uber trip" to "Uber trip",
      )
    cases.forEach { (body, expected) ->
      assertEquals(body, expected, parsed("VM-HDFCBK-S", body).merchant)
    }
  }

  @Test
  fun `credit merchant is the sender of the money`() {
    assertEquals(
      "RAHUL KUMAR",
      parsed("CF-HASTIB-S", "Rs 2000.00 credited to A/c XX1234 from RAHUL KUMAR on 01-01").merchant,
    )
  }

  @Test
  fun `account references are not mistaken for a merchant`() {
    assertEquals(
      SmsParser.UNKNOWN_MERCHANT,
      parsed("CF-HASTIB-S", "Rs 100.00 debited to your account XX1234.").merchant,
    )
  }

  @Test
  fun `bank is the stable part of the sender id`() {
    assertEquals("HASTIB", parser.extractBank("CF-HASTIB-S"))
    assertEquals("HASTIB", parser.extractBank("JM-HASTIB-S"))
    assertEquals("IDFCFB", parser.extractBank("VM-IDFCFB"))
    assertEquals("HDFCBK", parser.extractBank("HDFCBK"))
    assertEquals("YES BANK", parser.extractBank("Yes Bank"))
    assertEquals(SmsParser.UNKNOWN_BANK, parser.extractBank("+919876543210"))
    assertEquals(SmsParser.UNKNOWN_BANK, parser.extractBank(""))
  }

  @Test
  fun `parsed result carries all fields`() {
    val tx =
      parsed(
        "CF-HASTIB-S",
        "Rs.1,234.50 debited from A/c XX9 at BIG BAZAAR on 03-01. Avl Bal Rs 9,999.00"
      )
    assertEquals(Money.ofMinor(123_450), tx.amount)
    assertEquals("BIG BAZAAR", tx.merchant)
    assertEquals("HASTIB", tx.bank)
    assertEquals(TransactionType.DEBIT, tx.type)
  }
}
