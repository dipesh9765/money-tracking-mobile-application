package com.expenseinterceptor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an expense transaction.
 */
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Transaction amount in INR */
    val amount: Double,

    /** Merchant or description of the expense */
    val merchant: String,

    /** Category (e.g., Food, Travel, Bills) */
    val category: String,

    /** Optional user-provided title */
    val title: String? = null,

    /** Timestamp when the expense was recorded (milliseconds since epoch) */
    val timestamp: Long = System.currentTimeMillis(),

    /** Original SMS body for reference */
    val originalSms: String? = null,

    /** Source bank inferred from sender/body */
    val bank: String = "Unknown Bank",

    /** Transaction direction: CREDIT or DEBIT */
    val transactionType: String = "DEBIT"
)

