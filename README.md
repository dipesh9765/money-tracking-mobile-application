# Expense Interceptor

**Intercept bank SMS. Categorize instantly. Never forget a transaction.**

A privacy-first Android app that watches for bank transaction SMS, extracts amounts and merchants, and prompts you to categorize expenses on the spot. No cloud, no account, no internet required—everything stays on your device.

---

## Features

- **SMS Interception** — Real-time detection of bank transaction messages from configured sender patterns
- **Quick Categorization** — Tap the notification to open a transparent overlay and assign a category in seconds
- **Transactions View** — Scrollable list with date range filtering, swipe-to-delete (with undo), and tap-to-edit category, bank, and transaction type
- **Dashboard** — Bar, Line, and Pie charts powered by Vico. Filter by category, bank, or DEBIT/CREDIT. Time scales: daily, weekly, monthly
- **Scan Old SMS** — Backfill past transactions by scanning your inbox for a chosen date range
- **Export** — Share as CSV, Excel (.xlsx), PDF, or formatted text
- **Import** — Bring in expenses from CSV or Excel files

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Database | Room |
| Architecture | MVVM with Hilt |
| Excel | Apache POI |
| Charts | Vico |

---

## Getting Started

### 1. Configure SMS Sender Patterns

Edit `app/src/main/java/com/expenseinterceptor/config/SmsParserConfig.kt` and add your bank's sender patterns. The app uses **partial matching**—only the consistent suffix is needed:

```kotlin
val ALLOWED_SENDER_PATTERNS = listOf(
    "HASTIB",    // Hasti Bank (CF-HASTIB-S, JM-HASTI-S)
    "IDFCFB",    // IDFC Bank (CP-IDFCFB-S, VM-IDFCFB-S)
    "MAHABK",    // Maharashtra Bank
    "HDFCBK",    // HDFC Bank
    "SBIUPI",    // SBI UPI
    // Add your bank's pattern here
)
```

### 2. Customize Parsing (Optional)

The default `AMOUNT_REGEX` handles formats like `Rs.1234.56`, `INR 1,234`, `₹1,234.00`.  
`MERCHANT_REGEX` extracts "at/to/for MERCHANT" patterns.  
`detectTransactionType()` treats "credited"/"cr" as CREDIT, otherwise DEBIT.  
Adjust these in `SmsParserConfig.kt` if your bank uses different wording.

---

## Building the APK

**Android Studio:** Build > Build Bundle(s) / APK(s) > Build APK(s)  
Output: `app/build/outputs/apk/debug/app-debug.apk`

**Command line:**

```bash
# Windows
gradlew.bat assembleDebug

# Linux/Mac
./gradlew assembleDebug
```

---

## Installing on Your Phone

1. Enable **Install unknown apps** for your file manager or browser
2. Copy `app-debug.apk` to your phone and install
3. Grant **SMS** and **Notification** permissions when prompted

---

## Permissions

| Permission | Purpose |
|------------|---------|
| `RECEIVE_SMS` | Detect incoming bank transaction messages |
| `READ_SMS` | Read SMS content and scan old messages |
| `POST_NOTIFICATIONS` | Show notification when an expense is detected |

---

## Privacy

- **100% Offline** — All data stays on your device
- **No Internet** — The app does not require network access
- **No Analytics** — No tracking or telemetry
- **Local Storage** — Expenses stored in a local Room database

---

## Categories

Default expense categories: Food, Travel, Bills, Shopping, Entertainment, Health, Other

---

## Project Structure

```
app/src/main/java/com/expenseinterceptor/
├── config/
│   └── SmsParserConfig.kt       # Sender patterns, amount/merchant regex
├── data/
│   ├── local/
│   │   ├── ExpenseEntity.kt     # Room entity (amount, merchant, category, bank, DEBIT/CREDIT)
│   │   ├── ExpenseDao.kt
│   │   └── ExpenseDatabase.kt
│   └── repository/
│       └── ExpenseRepository.kt
├── di/
│   └── AppModule.kt
├── receiver/
│   └── SmsReceiver.kt           # BroadcastReceiver for SMS
├── ui/
│   ├── main/
│   │   ├── MainActivity.kt
│   │   ├── MainScreen.kt        # Transactions + Dashboard (bottom nav)
│   │   ├── MainViewModel.kt
│   │   └── DashboardScreen.kt   # Charts, filters, category breakdown
│   ├── quickadd/
│   │   ├── QuickAddActivity.kt
│   │   ├── QuickAddScreen.kt
│   │   └── QuickAddViewModel.kt
│   ├── scan/
│   │   ├── ScanSmsScreen.kt      # Scan old SMS by date range
│   │   └── ScanViewModel.kt
│   ├── debug/
│   │   └── DebugLogScreen.kt    # SMS debug logs (optional)
│   └── theme/
│       └── Theme.kt, Color.kt, Type.kt
├── util/
│   ├── CsvExporter.kt
│   ├── CsvImporter.kt
│   ├── ExcelExporter.kt
│   ├── ExcelImporter.kt
│   ├── PdfExporter.kt
│   ├── SmsScanner.kt            # Scan inbox for past transactions
│   ├── SmsDebugLogger.kt
│   └── NotificationHelper.kt
└── ExpenseInterceptorApp.kt
```

---

## License

For personal use only.