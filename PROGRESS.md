# Progress log

Branch: `overhaul/v1` (nothing pushed). Baseline: `assembleDebug` OK (AGP 9.0.1, Gradle 9.2.1, Kotlin 2.2.10), **zero tests**, no CI, no lint/format config, minify off, Room schema not exported.

## Audit (what an interviewer would find)

### Correctness bugs (each gets a failing test first)
| # | Finding | Evidence |
|---|---------|----------|
| 1 | `AMOUNT_REGEX` truncates plain amounts: `Rs.1234.56` parses as `123` (first alternative `\d{1,3}` wins). The README advertises this exact format. Indian lakh grouping (`1,23,456`) is also mis-parsed. | `SmsParserConfig.kt` |
| 2 | A UPI debit SMS usually says "debited ... credited to MERCHANT"; `detectTransactionType` treats any "credited" as CREDIT, so most UPI debits are recorded as income. | `detectTransactionType` |
| 3 | Non-transaction SMS (OTP, "Avl Bal Rs 5,000", offers) are turned into expense notifications because any `Rs <n>` is accepted and the first amount (often the balance) is used. | `extractAmount` |
| 4 | Re-scanning the inbox, or importing the same file twice, inserts duplicates (autogenerate id + REPLACE, no natural key). | `ScanViewModel.saveDetected`, `ExpenseDao` |
| 5 | CSV/Excel export drops `bank` and `transactionType`; import hardcodes `DEBIT`. Export then import turns every credit into a debit. | `CsvExporter`, `CsvImporter` |
| 6 | CSV import reads line by line, but the exporter quotes embedded newlines, so a multi-line field corrupts the file. | `CsvImporter.parseCsv` |
| 7 | `SimpleDateFormat` instances shared in `object`s (not thread safe); timestamps use the default zone implicitly. | importers |
| 8 | Notification id `BASE + currentTimeMillis().toInt() % 1000` can be negative / collide, so two SMS in a second overwrite each other. | `NotificationHelper` |
| 9 | Scan saves rows with category "Uncategorized", which is not in the category enum, so the edit dialog shows "Other" and the dashboard shows a phantom category. | `SmsScanner.toExpenseEntity` |
| 10 | Month ranges are computed in three separate copies, with `Calendar` and the default zone, and the scan range uses an inclusive end while the list uses an exclusive one. | `ExpenseRepository`, `MainViewModel`, `ScanViewModel` |

### Security / privacy (the app's selling point is "private")
- `allowBackup="true"` copies the expense database, including raw bank SMS bodies, to Google cloud backup. That contradicts the README.
- SMS text, amounts and merchants are written to logcat (`Log.d`) in the receiver, in release builds too.
- Notification content (amount + merchant) is shown on the lock screen.
- Exported CSV/Excel cells are not neutralised: the merchant comes from an arbitrary SMS sender, so `=HYPERLINK(...)` in an SMS becomes a formula in the user's spreadsheet (CSV injection).
- `XSSFWorkbook` opens an untrusted file picked by the user with no size cap.
- Money stored as `Double`.
- Release build: no minification, no shrinking.

### Architecture
- `MainViewModel` 723 lines does list, edit, dashboard maths, export, import and Android `Context` work. `MainScreen` is 912 lines. `DashboardScreen` 537.
- Business logic (`SmsParserConfig`, `SmsScanner`, `NotificationHelper`, `CsvImporter` ...) is in `object` singletons that touch `Context` and `Log` directly, so none of it can be unit tested or faked.
- Bank sender list is a Kotlin constant: users must edit source and rebuild to use another bank. README admits it.
- `ExpenseRepository` is a concrete class with no interface; a manual `@Provides` duplicates `@Inject`.
- Stringly-typed `transactionType` ("CREDIT"/"DEBIT") and category.
- `exportSchema = false`, so migrations cannot be tested. Room 2.7.0 and Compose BOM 2024.09 are out of date.
- README says "MVVM with Hilt" and "No Internet" but nothing enforces it (no CI, no tests).

## Plan (small commits, build and tests green at every step)

- **A. Build and CI.** Pin versions, add Spotless (ktfmt), detekt, Android Lint, Kover with a gate, R8 minify, Room schema export, test dependencies, CI and a tag-triggered release workflow.
- **B. Domain extraction.** Pure `SmsParser` (fixes 1-3), `Money` as integer paise, `TransactionType` enum, domain `Expense`, repository interface, `DateRanges` with an injected `Clock`/`ZoneId` (fix 10), use cases for recording SMS transactions with a dedupe key (fix 4), dashboard maths moved out of the ViewModel. Split `MainViewModel` into Transactions / Dashboard / Transfer (import-export) ViewModels. Replace singletons by injected interfaces (`SmsInbox`, `ExpenseNotifier`).
- **C. Security and privacy.** `allowBackup=false` plus data extraction rules, no content in logs, redacted in-memory debug log, private lock-screen notification, spreadsheet formula guard, bounded import, Room migration 2→3 (integer paise, unique dedupe key, indexes) with a migration test.
- **D. Feature completion.** In-app editable sender list (no source edits), lossless CSV/Excel round trip including bank and type, safe multi-line CSV parsing, dedupe on import and rescan.
- **E. Tests.** Table-driven parser tests on realistic SMS formats, Money, date ranges, use cases, ViewModels (Turbine), exporter/importer round trips, Room DAO + migration (Robolectric), receiver test, Hilt smoke test.
- **F. Docs.** README rewrite with Mermaid architecture and honest limits, ARCHITECTURE.md, ADRs (integer money, no SQLCipher, SMS permission and Play policy, dedupe key), SECURITY, PRIVACY, CONTRIBUTING, CHANGELOG, LICENSE; resume bullets.

## Status
- [x] Audit
- [ ] A Build and CI
- [ ] B Domain extraction
- [ ] C Security and privacy
- [ ] D Feature completion
- [ ] E Tests
- [ ] F Docs
