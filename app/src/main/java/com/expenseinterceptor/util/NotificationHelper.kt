package com.expenseinterceptor.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.expenseinterceptor.R
import com.expenseinterceptor.ui.quickadd.QuickAddActivity
import java.text.NumberFormat
import java.util.Locale

/**
 * Helper object for creating and displaying expense notifications.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "expense_alerts"
    private const val NOTIFICATION_ID_BASE = 1000

    /**
     * Intent extras keys for passing data to QuickAddActivity.
     */
    object Extras {
        const val AMOUNT = "extra_amount"
        const val MERCHANT = "extra_merchant"
        const val ORIGINAL_SMS = "extra_original_sms"
        const val BANK = "extra_bank"
        const val TRANSACTION_TYPE = "extra_transaction_type"
    }

    /**
     * Creates the notification channel for expense alerts.
     * Must be called before showing any notifications (typically in Application.onCreate).
     */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            enableLights(true)
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Shows a high-priority notification for a detected expense.
     * 
     * @param context Application context
     * @param amount The transaction amount
     * @param merchant The merchant/description
     * @param originalSms The original SMS body for reference
     */
    fun showExpenseNotification(
        context: Context,
        amount: Double,
        merchant: String,
        bank: String,
        transactionType: String,
        originalSms: String
    ) {
        // Format amount with Indian Rupee symbol
        val formattedAmount = formatCurrency(amount)

        // Create intent to open QuickAddActivity
        val intent = Intent(context, QuickAddActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(Extras.AMOUNT, amount)
            putExtra(Extras.MERCHANT, merchant)
            putExtra(Extras.ORIGINAL_SMS, originalSms)
            putExtra(Extras.BANK, bank)
            putExtra(Extras.TRANSACTION_TYPE, transactionType)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText("$formattedAmount - Tap to categorize")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$formattedAmount at $merchant\n\nTap to add category and save."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        // Show notification with unique ID
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            NOTIFICATION_ID_BASE + System.currentTimeMillis().toInt() % 1000,
            notification
        )
    }

    /**
     * Formats a double value as Indian Rupee currency.
     */
    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        return format.format(amount)
    }
}

