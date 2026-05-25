package com.example.novabudget.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

class SubscriptionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("SubAlarmReceiver", "Received broadcast action: $action")
        
        val dbHelper = NovaDatabaseHelper(context)
        
        if (Intent.ACTION_BOOT_COMPLETED == action) {
            // Restore all alarms on phone reboot
            val scheduler = SubscriptionAlarmScheduler(context)
            scheduler.scheduleAllAlarms(dbHelper)
            Log.d("SubAlarmReceiver", "Re-scheduled all active subscription alerts on boot.")
        } else if ("com.example.novabudget.ACTION_RENEWAL_ALERT" == action) {
            val subId = intent.getLongExtra("sub_id", -1L)
            val subName = intent.getStringExtra("sub_name") ?: "Subscription"
            val subAmount = intent.getDoubleExtra("sub_amount", 0.0)
            
            if (subId != -1L) {
                // Read active currency symbol from DB
                val currencyCode = dbHelper.getCurrency()
                val currencySymbol = if (currencyCode == "USD") "$" else "₹" // simple mapping helper
                
                // Show notification alert 2 days before renewal
                val notificationHelper = NotificationHelper(context)
                notificationHelper.showSubscriptionRenewalAlert(subName, subAmount, currencySymbol)
                Log.d("SubAlarmReceiver", "Fired renewal warning alert for $subName ($currencySymbol $subAmount)")
                
                // Auto-advance the next renewal date inside the SQLite database to the next billing cycle
                val allSubs = dbHelper.getAllSubscriptions()
                val activeSub = allSubs.firstOrNull { it.id == subId }
                if (activeSub != null) {
                    val nextRenewal = calculateNextCycleRenewal(activeSub.nextRenewalDate, activeSub.billingCycle)
                    val updatedSub = activeSub.copy(nextRenewalDate = nextRenewal)
                    dbHelper.updateSubscription(updatedSub)
                    Log.d("SubAlarmReceiver", "Advanced $subName renewal date to $nextRenewal")
                    
                    // Re-schedule the alarm for the next cycle
                    val scheduler = SubscriptionAlarmScheduler(context)
                    scheduler.scheduleAlarm(updatedSub)
                }
            }
        }
    }

    private fun calculateNextCycleRenewal(currentRenewal: Long, cycle: String): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = currentRenewal
            if (cycle.lowercase() == "yearly") {
                add(Calendar.YEAR, 1)
            } else {
                add(Calendar.MONTH, 1)
            }
        }
        return cal.timeInMillis
    }
}
