package com.expenseinterceptor.config

/**
 * Configuration object for SMS parsing.
 * 
 * Modify the ALLOWED_SENDER_PATTERNS list to match your bank's sender IDs.
 * Modify the regex patterns to match your bank's SMS format.
 */
object SmsParserConfig {

    /**
     * List of sender ID patterns to match (case-insensitive).
     * 
     * Bank sender IDs typically have format: PREFIX-BANKID
     * where PREFIX varies (CF-, JM-, VM-, CP-, AD-, etc.)
     * but BANKID remains consistent for each bank.
     * 
     * Add ONLY the consistent suffix part here (without the varying prefix).
     * 
     * Examples:
     * - "HASTIB" or "HASTI" (Hasti Bank: CF-HASTIB-S, JM-HASTI-S)
     * - "IDFCFB" (IDFC Bank: CP-IDFCFB-S, VM-IDFCFB-S)
     * - "MAHABK" (Maharashtra Bank: VM-MAHABK, AD-MAHABK)
     * - "HDFCBK" (HDFC Bank)
     * - "SBIINB" or "SBIPSG" (SBI)
     * - "ICICIB" (ICICI Bank)
     * - "AXISBK" (Axis Bank)
     * - "KOTAKB" (Kotak Bank)
     */
    val ALLOWED_SENDER_PATTERNS = listOf(
        "HASTIB",    
        "HASTI",    
        "IDFCFB",   
        "YESBNK",
        "Yes Bank",
        "SBIUPI"
    )

    /**
     * Regex pattern to extract transaction amount from SMS.
     * 
     * This pattern matches common Indian bank SMS formats:
     * - Rs.1234.56
     * - Rs 1,234.56
     * - INR 1234
     * - ₹1,234.00
     * 
     * Captures the numeric amount (with optional commas and decimals).
     */
    val AMOUNT_REGEX = Regex(
        """(?:Rs\.?|INR|₹)\s*(\d{1,3}(?:,\d{3})*(?:\.\d{2})?|\d+(?:\.\d{2})?)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Regex pattern to extract merchant/description from SMS.
     * 
     * Common patterns:
     * - "at MERCHANT_NAME"
     * - "to MERCHANT_NAME"
     * - "for MERCHANT_NAME"
     * - "VPA merchant@upi"
     */
    val MERCHANT_REGEX = Regex(
        """(?:at|to|for|VPA)\s+([A-Za-z0-9@._\s]+?)(?:\s+on|\s+Ref|\s+UPI|$)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Checks if the sender ID matches any of the allowed sender patterns.
     * 
     * This uses partial matching - the sender ID just needs to contain
     * the pattern anywhere in the string.
     * 
     * Examples:
     * - "CF-HASTIB-S" matches pattern "HASTIB" ✓
     * - "JM-HASTI-S" matches pattern "HASTI" ✓
     * - "VM-IDFCFB-S" matches pattern "IDFCFB" ✓
     * - "RANDOM-SPAM" does not match any pattern ✗
     */
    fun isAllowedSender(senderId: String): Boolean {
        return ALLOWED_SENDER_PATTERNS.any { pattern ->
            senderId.contains(pattern, ignoreCase = true)
        }
    }

    /**
     * Detects transaction direction.
     * If SMS contains "credited"/"cr" we treat as CREDIT, otherwise DEBIT.
     */
    fun detectTransactionType(smsBody: String): String {
        val lowerBody = smsBody.lowercase()
        val creditRegex = Regex("""\b(credited|cr)\b""", RegexOption.IGNORE_CASE)
        return if (creditRegex.containsMatchIn(lowerBody)) {
            "CREDIT"
        } else {
            "DEBIT"
        }
    }

    /**
     * Extracts the transaction amount from the SMS body.
     * Returns null if no amount is found.
     */
    fun extractAmount(smsBody: String): Double? {
        val match = AMOUNT_REGEX.find(smsBody)
        return match?.groupValues?.get(1)?.let { amountStr ->
            // Remove commas and parse as double
            amountStr.replace(",", "").toDoubleOrNull()
        }
    }

    /**
     * Extracts the merchant/description from the SMS body.
     * Returns a default value if not found.
     */
    fun extractMerchant(smsBody: String): String {
        val match = MERCHANT_REGEX.find(smsBody)
        return match?.groupValues?.get(1)?.trim() ?: "Unknown Merchant"
    }

    /**
     * Tries to extract bank name from sender (e.g., VM-HDFCBK) or falls back to a parsed token.
     */
    fun extractBank(senderId: String?, smsBody: String): String {
        val cleanedSender = senderId
            ?.substringAfterLast('-')
            ?.takeIf { it.isNotBlank() }
            ?.trim()
            ?.uppercase()

        if (!cleanedSender.isNullOrBlank()) {
            return cleanedSender
        }

        val bankFromBody = Regex("""\b([A-Z]{3,})\b""")
            .find(smsBody.uppercase())
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }

        return bankFromBody ?: "Unknown Bank"
    }
}

