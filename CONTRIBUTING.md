# Contributing

## Setup
JDK 17 and the Android SDK are required. Put the SDK path in `local.properties` (Android Studio does this automatically), then:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Before opening a pull request
Run what CI runs:

```bash
./gradlew spotlessApply                                   # format (ktfmt, Google style)
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest koverVerify
```

## Conventions
- Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `build:`).
- Keep `domain/` free of `android.*` imports. Reach storage, the SMS inbox, notifications and files through the ports in `domain/port` and `domain/repository`.
- **New bank format?** Add a row to `SmsParserTest` that fails first, then adjust `SmsParser`. Use realistic text with the account digits and references replaced.
- Money is `Money` (integer paise). Never use `Double` for amounts; `toDouble()` is for chart coordinates only.
- Never call `Clock`-free time APIs (`System.currentTimeMillis()`, `Calendar.getInstance()`) in code that decides a date; use the injected `Clock`/`DateRanges`.
- New screens get a view model with one state object and a `Channel` of events.
- Database changes need a Room migration, a case in `RoomTest`, and the new exported schema committed in `app/schemas`.
- Do not add a network, analytics or crash-reporting dependency, and do not log message content. `PrivacyInvariantsTest` will fail the build if you do.
- detekt has no baseline: fix findings instead of suppressing them.
