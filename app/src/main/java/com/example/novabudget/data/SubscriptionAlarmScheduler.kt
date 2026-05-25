package com.example.novabudget.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

class SubscriptionAlarmScheduler(private val context: Context) {

    fun scheduleAlarm(sub: Subscription) {
        if (sub.isReminderEnabled == 0) {
            cancelAlarm(sub)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SubscriptionAlarmReceiver::class.java).apply {
            action = "com.example.novabudget.ACTION_RENEWAL_ALERT"
            putExtra("sub_id", sub.id)
            putExtra("sub_name", sub.name)
            putExtra("sub_amount", sub.amount)
            putExtra("sub_renewal", sub.nextRenewalDate)
        }

        // Use a unique request code based on the subscription's ID
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sub.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Calculate reminder time: 2 days before nextRenewalDate at 9:00 AM
        val reminderTimeMs = calculateReminderTime(sub.nextRenewalDate)

        if (reminderTimeMs > System.currentTimeMillis()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderTimeMs,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    reminderTimeMs,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Scheduled renewal alarm for ${sub.name} (id: ${sub.id}) at timestamp $reminderTimeMs")
        } else {
            // If 2 days before is already in the past, schedule it immediately or for today if the renewal is in the future
            if (sub.nextRenewalDate > System.currentTimeMillis()) {
                val immediateCal = Calendar.getInstance().apply {
                    add(Calendar.MINUTE, 1) // 1 minute from now
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        immediateCal.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        immediateCal.timeInMillis,
                        pendingIntent
                    )
                }
                Log.d("AlarmScheduler", "Renewal is close. Scheduled immediate alarm for ${sub.name} (id: ${sub.id}) in 1 minute")
            } else {
                Log.d("AlarmScheduler", "Renewal date for ${sub.name} is in the past, skipping alarm scheduling.")
            }
        }
    }

    fun cancelAlarm(sub: Subscription) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SubscriptionAlarmReceiver::class.java).apply {
            action = "com.example.novabudget.ACTION_RENEWAL_ALERT"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sub.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("AlarmScheduler", "Cancelled alarm for ${sub.name} (id: ${sub.id})")
        }
    }

    fun scheduleAllAlarms(dbHelper: NovaDatabaseHelper) {
        val subscriptions = dbHelper.getAllSubscriptions()
        for (sub in subscriptions) {
            if (sub.isReminderEnabled == 1) {
                scheduleAlarm(sub)
            }
        }
    }

    private fun calculateReminderTime(renewalTimestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = renewalTimestamp
            add(Calendar.DAY_OF_YEAR, -2) // 2 days before
            set(Calendar.HOUR_OF_DAY, 9)   // at 9:00 AM
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
