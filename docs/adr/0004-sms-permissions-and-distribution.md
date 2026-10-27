# ADR 0004: Use SMS permissions directly and distribute outside Google Play

Status: accepted

## Context
Reading bank messages needs `RECEIVE_SMS` and `READ_SMS`. Google Play treats both as restricted permissions: an app may request them only if it is the default SMS or assistant handler (or fits a short list of exceptions). A personal expense tracker does not qualify.

## Options
1. **Sideload / GitHub releases** with the permissions as they are.
2. **Notification listener** (`NotificationListenerService`) reading the SMS app's notifications: avoids the SMS permissions but depends on the user's messaging app, misses messages with notifications off, and is fragile across vendors.
3. **SMS Retriever / user consent APIs**: designed for OTPs, not for arbitrary bank messages.

## Decision
Keep the SMS permissions (option 1) and distribute through signed GitHub releases. The code makes the minimum use of them:
- `RECEIVE_SMS` only to be notified; the receiver is protected by `BROADCAST_SMS`, so only the system can invoke it.
- `READ_SMS` only when the user taps "Scan old SMS".
- Only senders on the user's list are parsed; everything else is dropped without being stored or logged.

## Consequences
- Not installable from the Play Store. A future Play version would need a different design (notification listener or manual paste) and a policy declaration.
- Users must grant two sensitive permissions, so [PRIVACY.md](../../PRIVACY.md) and the permission screen explain exactly why.
