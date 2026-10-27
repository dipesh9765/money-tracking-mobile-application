package com.expenseinterceptor.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.expenseinterceptor.R
import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.model.TransactionType
import com.expenseinterceptor.domain.port.DetectedTransaction
import com.expenseinterceptor.domain.port.ExpenseNotifier
import com.expenseinterceptor.domain.sms.SmsFingerprint
import com.expenseinterceptor.ui.quickadd.QuickAddActivity
import com.expenseinterceptor.ui.quickadd.QuickAddArgs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the "tap to categorize" notification.
 *
 * The notification is private on the lock screen: the amount and merchant are only shown once the
 * phone is unlocked, and a generic title is shown before that.
 */
@Singleton
class AndroidExpenseNotifier @Inject constructor(@ApplicationContext private val context: Context) :
  ExpenseNotifier {

  fun createChannel() {
    val channel =
      NotificationChannel(
          CHANNEL_ID,
          context.getString(R.string.notification_channel_name),
          NotificationManager.IMPORTANCE_HIGH,
        )
        .apply {
          description = context.getString(R.string.notification_channel_description)
          lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  // The POST_NOTIFICATIONS check is in canPostNotifications(), which lint cannot see through.
  @SuppressLint("MissingPermission")
  override fun notifyDetected(detected: DetectedTransaction) {
    if (!canPostNotifications()) return
    val tx = detected.transaction
    // The same SMS always maps to the same id, so a repeated broadcast replaces the notification
    // instead of stacking, and two different SMS in the same second never overwrite each other.
    val id = SmsFingerprint.of(detected.originalSms, detected.timestamp).hashCode()

    val intent =
      Intent(context, QuickAddActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        QuickAddArgs(
            amountMinor = tx.amount.minor,
            merchant = tx.merchant,
            originalSms = detected.originalSms,
            bank = tx.bank,
            type = tx.type,
            timestamp = detected.timestamp,
          )
          .writeTo(this)
      }
    val pending =
      PendingIntent.getActivity(
        context,
        id,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val amount = Money.ofMinor(tx.amount.minor).format()
    val verb = if (tx.type == TransactionType.CREDIT) "received from" else "at"
    val public =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(context.getString(R.string.notification_title))
        .build()
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(context.getString(R.string.notification_title))
        .setContentText("$amount - Tap to categorize")
        .setStyle(
          NotificationCompat.BigTextStyle()
            .bigText("$amount $verb ${tx.merchant}\n\nTap to add a category and save.")
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(public)
        .setAutoCancel(true)
        .setContentIntent(pending)
        .build()
    NotificationManagerCompat.from(context).notify(id, notification)
  }

  private fun canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

  private companion object {
    const val CHANNEL_ID = "expense_alerts"
  }
}
