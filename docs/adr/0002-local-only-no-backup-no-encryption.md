# ADR 0002: Local-only storage, no backup, no database encryption

Status: accepted

## Context
The database contains the full text of bank messages. The project's promise is that nothing leaves the device. Android's defaults work against that: `allowBackup="true"` uploads app data to the user's Google account, and device-to-device transfer copies it to a new phone.

## Decision
1. The app has **no `INTERNET` permission** and no network or analytics library. `PrivacyInvariantsTest` and a CI step on the merged release manifest enforce both.
2. `allowBackup="false"` plus `dataExtractionRules` (Android 12+) and `fullBackupContent` (older) exclude every domain from cloud backup and device transfer.
3. Users move data with CSV/Excel export, a deliberate and visible action.
4. The database is **not encrypted** (no SQLCipher).

## Why not SQLCipher
- The key would have to live on the device (Keystore-wrapped), so encryption protects against a stolen disk image of a locked phone, which Android file-based encryption already covers, but not against the threats that matter for this app: malware with SMS access, or someone using the unlocked phone.
- It adds a native dependency, build size and a data-loss risk if the key is lost.
- The same SMS bodies are readable by any app holding `READ_SMS`, so encrypting a copy does not change the exposure.

## Consequences
- Users lose nothing on a phone change if they export first; the README and PRIVACY state this.
- If the threat model changes (shared devices, rooted phones), revisit encryption with a Keystore-held key, as the sibling calorie-tracker project does for API keys.
