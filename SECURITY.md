# Security

## Reporting a vulnerability
Please open a private security advisory on GitHub (Security → Advisories → Report a vulnerability) rather than a public issue. Include the Android version, what you did and what you saw. You will get an acknowledgement within a week.

## Threat model

The app handles bank SMS text and spending history. What it defends against, and what it deliberately does not:

| Threat | Mitigation | Residual risk |
| :-- | :-- | :-- |
| Data leaves the phone | No `INTERNET` permission, no network or analytics libraries (enforced by test and CI) | A build from modified sources could add one; verify the release manifest |
| Cloud backup / device transfer copies the database | `allowBackup=false` and extraction rules that exclude all domains | Export files you share are as safe as where you send them |
| Another app spoofs an SMS broadcast | Receiver requires `BROADCAST_SMS`, which only the system holds | A malicious SMS can still *contain* hostile text (see next row) |
| Hostile SMS text, e.g. merchant `=HYPERLINK(...)` | CSV cells starting with `= + - @ tab CR` get a guard quote; Excel cells are strings, never formulas; text fields are length-limited on import | Open untrusted CSV in a viewer, not a macro-enabled workbook |
| Malicious import file | 5 MB limit, 50,000 rows, 2,000 characters per field, per-row validation, no formula evaluation; POI's zip-bomb checks stay enabled | A crafted OOXML file could still exhaust memory on a very small device |
| Notification content on the lock screen | Channel and notification visibility are private; the public version shows only a generic title | Notification history apps could still capture text after unlock |
| Leaking through logs | No `Log.d/v/i`, `println` or `printStackTrace` in the app (enforced by test); warnings never include message content | Crash reports from the OS are outside the app's control |
| Reading the database on a rooted or unlocked phone | None: the database is not encrypted ([ADR 0002](docs/adr/0002-local-only-no-backup-no-encryption.md)) | Accepted for this threat model |
| Shared exports lingering on disk | Files are written to the app cache only and deleted after 24 hours; the FileProvider exposes `exports/` only | The recipient app keeps its own copy |
| Exported components | Only the launcher activity is exported; `QuickAddActivity`, the FileProvider and PendingIntents are not (immutable flag) | |

## Hardening in the build
- R8 minification and resource shrinking are on for release builds (Apache POI is kept whole because it loads schemas reflectively).
- Release signing keys are read from CI secrets or `~/.gradle/gradle.properties`, never from the repository; `*.jks` and `*.keystore` are git-ignored.
- Dependencies are pinned in `gradle/libs.versions.toml`.

## History note
The original repository exposed sender ids and parsing rules only; it never contained credentials. Nothing needs rotating.
