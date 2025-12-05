# Money Tracking Mobile Application

A personal Android app that intercepts bank SMS messages, extracts transaction amounts, and prompts you to categorize expenses immediately.

## Features

- **SMS Interception**: Automatically detects bank transaction SMS messages
- **Quick Categorization**: Tap the notification to categorize expenses in a transparent overlay
- **Expense Tracking**: View all expenses for the current month
- **CSV Export**: Export expenses to CSV and share via any app

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Database**: Room Database
- **Architecture**: MVVM with Hilt (Dependency Injection)

## Setup

### 1. Configure SMS Sender IDs

Edit `app/src/main/java/com/expenseinterceptor/config/SmsParserConfig.kt` to add your bank's sender IDs:

```kotlin
val ALLOWED_SENDER_IDS = listOf(
    "VM-MAHABK",  // Maharashtra Bank
    "AD-HDFCBK",  // HDFC Bank
    // Add your bank's sender ID here
)
```

### 2. Customize Amount Regex (if needed)

The default regex handles most Indian bank SMS formats:
- Rs.1234.56
- Rs 1,234.56
- INR 1234
- ₹1,234.00

If your bank uses a different format, update `AMOUNT_REGEX` in `SmsParserConfig.kt`.

## Building the APK

### Using Android Studio

1. Open the project in Android Studio
2. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**
3. Find the APK at `app/build/outputs/apk/debug/app-debug.apk`

### Using Command Line

```bash
# Windows
gradlew.bat assembleDebug

# Linux/Mac
./gradlew assembleDebug
```

## Installing on Your Phone

1. **Enable Unknown Sources**: Go to Settings > Security > Install unknown apps
2. **Transfer APK**: Copy `app-debug.apk` to your phone
3. **Install**: Tap the APK file to install
4. **Grant Permissions**: When prompted, grant SMS and notification permissions

## Permissions Required

| Permission | Purpose |
|------------|---------|
| `RECEIVE_SMS` | Detect incoming bank transaction messages |
| `READ_SMS` | Read SMS content to extract amount |
| `POST_NOTIFICATIONS` | Show notification when expense is detected |

## Privacy

- **100% Offline**: All data stays on your device
- **No Internet**: The app doesn't require internet access
- **No Analytics**: No tracking or telemetry
- **Local Storage**: Expenses stored in local Room database

## Categories

Default expense categories:
- Food
- Travel
- Bills
- Shopping
- Entertainment
- Health
- Other

## Project Structure

```
app/src/main/java/com/expenseinterceptor/
├── config/
│   └── SmsParserConfig.kt      # SMS filtering and regex patterns
├── data/
│   ├── local/
│   │   ├── ExpenseEntity.kt    # Room entity
│   │   ├── ExpenseDao.kt       # Database queries
│   │   └── ExpenseDatabase.kt  # Room database
│   └── repository/
│       └── ExpenseRepository.kt
├── di/
│   └── AppModule.kt            # Hilt dependency injection
├── receiver/
│   └── SmsReceiver.kt          # BroadcastReceiver for SMS
├── ui/
│   ├── main/
│   │   ├── MainActivity.kt
│   │   ├── MainScreen.kt
│   │   └── MainViewModel.kt
│   ├── quickadd/
│   │   ├── QuickAddActivity.kt
│   │   ├── QuickAddScreen.kt
│   │   └── QuickAddViewModel.kt
│   └── theme/
│       └── Theme.kt, Color.kt, Type.kt
├── util/
│   ├── CsvExporter.kt          # CSV export functionality
│   └── NotificationHelper.kt   # Notification creation
└── ExpenseInterceptorApp.kt    # Application class
```

## License

For personal use only.
