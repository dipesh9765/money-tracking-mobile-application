package com.expenseinterceptor.domain.usecase

import com.expenseinterceptor.domain.model.Expense
import com.expenseinterceptor.domain.model.Money

/** Totals for a list of transactions. */
data class ExpenseSummary(val spent: Money, val received: Money, val count: Int) {
  /** Money out minus money in; negative when more came in than went out. */
  val net: Money
    get() = spent - received

  companion object {
    fun of(expenses: List<Expense>): ExpenseSummary {
      var spent = Money.ZERO
      var received = Money.ZERO
      expenses.forEach { if (it.isCredit) received += it.amount else spent += it.amount }
      return ExpenseSummary(spent, received, expenses.size)
    }
  }
}
