package com.expenseinterceptor.data.local

import com.expenseinterceptor.domain.model.Expense
import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.model.TransactionType

fun ExpenseEntity.toDomain(): Expense =
  Expense(
    id = id,
    amount = Money.ofMinor(amountMinor),
    merchant = merchant,
    category = category,
    timestamp = timestamp,
    type = TransactionType.fromText(transactionType),
    bank = bank,
    title = title,
    originalSms = originalSms,
    dedupeKey = dedupeKey,
  )

fun Expense.toEntity(): ExpenseEntity =
  ExpenseEntity(
    id = id,
    amountMinor = amount.minor,
    merchant = merchant,
    category = category,
    title = title,
    timestamp = timestamp,
    originalSms = originalSms,
    bank = bank,
    transactionType = type.name,
    dedupeKey = dedupeKey,
  )
