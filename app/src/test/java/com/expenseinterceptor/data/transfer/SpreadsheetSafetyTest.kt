package com.expenseinterceptor.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Test

class SpreadsheetSafetyTest {

  @Test
  fun `dangerous leading characters get a guard`() {
    listOf("=cmd", "+1", "-2", "@x", "\tTAB", "\rCR").forEach {
      assertEquals("'$it", SpreadsheetSafety.neutralize(it))
    }
  }

  @Test
  fun `ordinary text is untouched`() {
    listOf("", "SWIGGY", "5 stars", "a=b", "x-y").forEach {
      assertEquals(it, SpreadsheetSafety.neutralize(it))
    }
  }

  @Test
  fun `restore undoes neutralize and nothing else`() {
    listOf("=cmd", "+1", "-2", "@x", "plain", "'quoted", "'", "").forEach {
      assertEquals(it, SpreadsheetSafety.restore(SpreadsheetSafety.neutralize(it)))
    }
    assertEquals("'text", SpreadsheetSafety.restore("'text"))
  }
}
