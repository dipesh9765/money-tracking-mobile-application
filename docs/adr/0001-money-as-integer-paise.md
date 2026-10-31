# ADR 0001: Store and compute money as integer paise

Status: accepted

## Context
Amounts were stored in Room as `REAL` (a `Double`) and summed with `sumOf`. Binary floating point cannot represent most decimal fractions: ten transactions of ₹0.10 sum to `0.9999999999999999`, and a month of totals, percentages and exports inherits that drift. Excel and CSV round trips made it worse by printing `Double.toString()`.

## Decision
- Persist `amountMinor INTEGER` (paise). Domain code uses a `Money` value class over `Long` with exact `+`, `-`, comparison and formatting.
- Text in is parsed with `BigDecimal` (`Money.parse`), rounding half-up at two decimals and rejecting negatives, exponents and anything above ₹10^11, so a hostile SMS or file cannot overflow a sum.
- `Money.toDouble()` exists only for chart coordinates and is documented as display-only.
- Migration 2→3 converts with `CAST(ROUND(amount * 100) AS INTEGER)`; the test feeds it `0.1 + 0.2` and expects 30 paise.

## Consequences
- Totals, shares and exports are exact and deterministic.
- One currency (INR) is assumed; supporting more would need a currency field and a per-currency scale.
- `Long` overflow is guarded by the input cap and `Math.addExact`.
