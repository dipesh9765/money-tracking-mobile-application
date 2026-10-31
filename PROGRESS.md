# Progress log

Branch: `overhaul/v1` (nothing pushed), tag `v1.1.0`.

Baseline at the start: `assembleDebug` OK (AGP 9.0.1, Gradle 9.2.1, Kotlin 2.2.10), **zero tests**, no CI, no lint or format config, minification off, Room schema not exported, 5,350 lines of main Kotlin.

## What the audit found (and what happened to each item)

### Correctness bugs. Evidence is a real run of the original code, then a regression test.

| # | Finding | Evidence from the original code | Fix |
|---|---------|----------------------------------|-----|
| 1 | Plain amounts truncated: `Rs.1234.56` parsed as **123.0**; lakh grouping `Rs 1,23,456.00` parsed as **1.0** | characterization run of `SmsParserConfig` | New amount regex + `Money.parse`; `SmsParserTest` |
| 2 | UPI debit that says "credited to SWIGGY" classified **CREDIT** | same run | earliest direction keyword wins; "credit card" ignored |
| 3 | Balance taken as the amount: "Avl Bal Rs 5,000. Rs 200 debited" gave **5000.0**; an OTP message gave 500.0 | same run | balance/limit lead-in filter; OTP filter |
| 4 | Bank for `CF-HASTIB-S` recorded as **`S`** | same run | bank = stable middle of the sender id |
| 5 | Rescan or re-import duplicates every row | code reading (autogenerate id + REPLACE) | unique `dedupeKey` + content check; `RoomTest`, `SmsUseCasesTest`, `ExpenseTransferTest` |
| 6 | Export dropped bank and direction; import forced DEBIT | code reading | 8-column format, 6-column files still import; round-trip tests |
| 7 | Multi-line CSV fields broke import; Excel rows after a gap skipped | code reading (`readLine`, `physicalNumberOfRows`) | RFC 4180 reader; `lastRowNum`; tests |
| 8 | "Total spent" summed credits (SQL `SUM(amount)` over all rows) | code reading | `ExpenseSummary` separates spent and received |
| 9 | Long SMS parsed per PDU part | code reading | parts joined per sender; receiver test with two real PDUs |
| 10 | Notification id `currentTimeMillis().toInt() % 1000` could collide or go negative | code reading | id derived from the message fingerprint; tests for replace and no-overwrite |
| 11 | Second notification while the dialog was open kept the first transaction (`singleInstance`, no `onNewIntent`) | code reading | `onNewIntent` handling; view model test |
| 12 | Month ranges computed three times with `Calendar` and the default zone, one with an inclusive end | code reading | `DateRanges(clock, zone)`; DST, leap-year and zone tests |

### Found while writing the new tests
- QuickAdd's amount filter accepted 13-digit numbers (`\d{0,12}\.?\d{0,2}` also matches 13 digits with no dot). Fixed with `(\.\d{0,2})?`.
- After "Save new" the scan screen cleared its own "Saved N" message by rescanning. Fixed.
- The first receiver rewrite crashed at runtime (Hilt injection skipped). Replaced by an entry point and covered by a test that drives the real receiver.
- The scan preview listed SMS from senders the user never configured (personal conversations). They are now dropped before reaching the UI.

### Security and privacy
- `allowBackup=true` uploaded raw bank SMS to cloud backup → disabled, plus extraction rules.
- SMS text, amounts and merchants in logcat → removed; test forbids `Log.d/v/i`, `println`, `printStackTrace`.
- Lock-screen notification showed amount and merchant → private visibility with a generic public version.
- CSV formula injection from SMS merchants → guard and restore.
- Unbounded import → size, row and field limits.
- `Double` money → integer paise, migration 2→3.
- Release build not minified → R8 on, Apache POI kept for reflection safety.
- Dead debug screen that stored SMS previews → deleted.

## Delivered

| Step | Commit(s) | Result |
|------|-----------|--------|
| Audit | `docs: audit and overhaul plan` | This document |
| A Build and CI | `build: add spotless, detekt, lint, kover...`, `style: apply ktfmt` | ktfmt, detekt, Lint (abortOnError), Kover, R8, schema export, CI + release workflows |
| B Domain and architecture | `feat(domain)...`, `refactor: layered architecture...` | ports, use cases, `Money`, `DateRanges`, 723-line view model split into three (largest 144 lines); static `object` utilities (parser config, scanner, notifier, exporters) replaced by injected classes |
| C Security and privacy | in `refactor`, `test: ...`, `build: keep Apache POI...` | see above |
| D Features | `refactor`, `test` | editable sender list, lossless round trip, dedupe, new/already-saved scan flags |
| E Tests | `test: ...` | 143 tests, 79.4% of non-UI lines, 75% gate |
| F Docs | `docs: ...` | README, ARCHITECTURE, 4 ADRs, SECURITY, PRIVACY, CONTRIBUTING, CHANGELOG, LICENSE |

## Verified
`spotlessCheck detekt lintDebug testDebugUnitTest koverVerify assembleRelease` all exit 0 with **no detekt baseline**. A mutation spot check (disabling the formula guard and reversing the direction order) makes 14 tests fail. The merged release manifest requests `RECEIVE_SMS`, `READ_SMS` and `POST_NOTIFICATIONS` only.

## Not verified / open items
- **No device or emulator run.** The receiver, PDF writer, permission prompts and the minified release APK (6.9 MB with POI kept) were exercised through Robolectric and builds only.
- No screenshots in the README.
- The license is MIT; the original README said "For personal use only". Change `LICENSE` if you prefer that.
- GitHub secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) are needed for signed releases.
- Default sender ids are common Indian banks; the original author's own bank ids (HASTIB, HASTI, YESBNK, IDFCFB, SBIUPI) are partly kept (IDFCFB, YESBNK, SBIUPI). Add the others in the app.
- AGP 9 is used with the legacy opt-out flags already in `gradle.properties` (`android.newDsl=false`, `android.builtInKotlin=false`); they are deprecated and will need migration at AGP 10.

## Status
- [x] Audit
- [x] A Build and CI
- [x] B Domain extraction
- [x] C Security and privacy
- [x] D Feature completion
- [x] E Tests
- [x] F Docs
