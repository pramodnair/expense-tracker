package com.example.novabudget.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.novabudget.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "nova_budget_channel"
        const val CHANNEL_NAME = "NovaBudget Alerts"
        const val TX_NOTIFICATION_ID = 1001
        const val BUDGET_WARNING_ID = 1002
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Notifications for transaction alerts and budget limits"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showTransactionLogged(tx: Transaction, currencySymbol: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val formattedAmount = String.format("%.2f", tx.amount)
        val contentText = "Logged $currencySymbol $formattedAmount spent on ${tx.accountName} (${tx.lastDigits}) at ${tx.merchant}"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar) // Standard Android system icon
            .setContentTitle("New Expense Logged")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            // Need permission check for Android 13+ but let's let caller handle or catch SecurityException
            manager.notify(TX_NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Permission not granted yet, ignore or print
            e.printStackTrace()
        }
    }

    fun showBudgetWarning(currentSpent: Double, limit: Double, currencySymbol: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val formattedSpent = String.format("%.2f", currentSpent)
        val formattedLimit = String.format("%.2f", limit)
        val contentText = "Warning! Total monthly spend of $currencySymbol $formattedSpent exceeds your configured budget of $currencySymbol $formattedLimit!"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Standard warning alert icon
            .setContentTitle("🚨 Budget Limit Exceeded!")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX) // Max priority for warnings
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(BUDGET_WARNING_ID, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showSyncNotification(sentCount: Int, receivedCount: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (receivedCount > 0) {
            "Auto-synced! Merged $receivedCount new transactions from your spouse."
        } else {
            "Auto-synced with spouse! Database is fully up to date."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("🔄 Spouse Sync Complete")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(1003, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showSubscriptionRenewalAlert(name: String, amount: Double, currencySymbol: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val formattedAmount = String.format("%.2f", amount)
        val contentText = "Your subscription to $name ($currencySymbol $formattedAmount) is renewing in 2 days!"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ Subscription Renewal Alert")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(name.hashCode(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
