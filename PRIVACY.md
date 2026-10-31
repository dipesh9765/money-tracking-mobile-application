# Privacy

Expense Interceptor does not collect, transmit or share any data. It has no account, no server, no analytics and no network permission.

## What the app reads
| Data | When | Why |
| :-- | :-- | :-- |
| Incoming SMS from the senders **you** list | When a message arrives | To detect a bank transaction and offer to categorise it |
| Past SMS from those senders | Only when you tap "Scan old SMS" | To backfill earlier transactions. Messages from other senders are discarded immediately and never shown |

## What the app stores
On the device only, in the app's private database:
amount, merchant, category, optional note, bank, direction, timestamp and the text of the bank SMS you saved.

## What leaves the device
Nothing, unless you choose **Share / Export**. Export writes a file to the app cache, hands it to the app you pick in the share sheet, and deletes it after 24 hours. What the receiving app does with it is up to you.

## Backups
Android backup and device-to-device transfer are disabled. To change phones, export your data and import it on the new one.

## Deleting your data
"Delete all" in the menu removes every expense. Uninstalling the app removes the database and settings.

## Permissions
`RECEIVE_SMS`, `READ_SMS` and `POST_NOTIFICATIONS`. See the [README](README.md#permissions) and [ADR 0004](docs/adr/0004-sms-permissions-and-distribution.md).
