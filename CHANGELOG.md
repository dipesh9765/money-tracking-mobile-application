# Changelog

## 1.1.0

### Added
- Bank sender ids are editable inside the app (overflow menu → Bank senders); previously users had to edit a Kotlin file and rebuild.
- "Scan old SMS" shows which detected messages are new and which are already saved, and saving skips duplicates.
- Credits and debits are tracked separately: the summary shows spent and received, and the dashboard net total subtracts credits.
- CSV and Excel exports include bank and direction; older six-column files still import.
- Import reports duplicates and per-row errors; files over 5 MB or 50,000 rows are refused.
- 143 tests (there were none), Kover coverage gate (75%, measured 79.4% of non-UI lines), detekt with no baseline, Android Lint, ktfmt, R8 release builds and a CI workflow.
- Room schema export and tested migrations.
- SECURITY, PRIVACY, ARCHITECTURE, CONTRIBUTING, ADRs and an MIT license.

### Changed
- Amounts are stored and summed as integer paise instead of `Double` (database v3, migrated automatically).
- Architecture: domain layer with ports and use cases, Hilt bindings, and the 723-line `MainViewModel` split into Transactions, Dashboard and Transfer view models.
- One `DateRanges` class replaces three hand-written month calculations; weeks now start on Monday.
- Notifications are private on the lock screen and no longer overwrite each other.
- Android backup and device transfer are disabled, and cleartext traffic is off.
- Toolchain: Compose BOM, lifecycle, Room and AndroidX updated; Kotlin 2.2, AGP 9.0.

### Fixed
- `Rs.1234.56` was read as 123 and lakh-grouped amounts (`1,23,456.00`) as 1.
- UPI debits that say "credited to MERCHANT" were recorded as credits.
- Balances and limits ("Avl Bal Rs 5,000") could be taken as the transaction amount; OTP messages produced expense notifications.
- The bank name for `CF-HASTIB-S` was recorded as `S`.
- Long bank SMS split into several parts were parsed piece by piece.
- Re-scanning the inbox or re-importing a file duplicated every row.
- Export then import turned every credit into a debit and dropped the bank.
- Multi-line fields broke CSV import; spreadsheet rows after a gap were skipped.
- Tapping a second notification while the quick-add dialog was open kept showing the first transaction.
- "Total spent" included credits.
- CSV export could carry spreadsheet formulas from SMS text (formula injection).
- SMS text, amounts and merchants were written to logcat.
- A rotated screen could reset the quick-add form.

### Removed
- The unreachable SMS debug screen and its in-memory log of message previews.
- Unused strings and colours.
