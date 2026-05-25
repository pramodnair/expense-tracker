package com.example.novabudget.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import java.security.MessageDigest
import java.util.Calendar

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.messageBody ?: continue
                val sender = sms.originatingAddress ?: "Unknown"
                val timestamp = sms.timestampMillis

                Log.d("SmsReceiver", "Received SMS from $sender: $body")
                
                // Process the SMS
                processSms(context, body, sender, timestamp)
            }
        }
    }

    private fun processSms(context: Context, body: String, sender: String, timestamp: Long) {
        val paymentKeywords = listOf(
            "cc payment", "card pymt", "credit card bill", "inf*credit card",
            "cc pymt", "card payment", "bill pymt", "bill payment", "cc outstanding",
            "credit card", "credit c", "creditcard", "amazon pay credit"
        )
        val isCardPayment = paymentKeywords.any { body.lowercase().contains(it) }
        if (isCardPayment) {
            Log.d("SmsReceiver", "CC payment matched. Ignoring to prevent double-counting.")
            return
        }

        val repo = DefaultDataRepository.getInstance(context)
        val helper = NovaDatabaseHelper(context)

        // 1. Fetch configurations
        val cards = helper.getAllCardConfigs()
        val accounts = helper.getAllAccountConfigs()

        var matchedName = ""
        var matchedLastDigits = ""
        var isMatched = false

        // Check cards
        for (card in cards) {
            if (body.contains(card.lastDigits)) {
                val keywords = card.keywords.split(",").map { it.trim().lowercase() }
                val hasKeyword = keywords.any { keyword -> body.lowercase().contains(keyword) }
                if (hasKeyword) {
                    matchedName = card.cardName
                    matchedLastDigits = card.lastDigits
                    isMatched = true
                    break
                }
            }
        }

        // Check accounts if no card matches
        if (!isMatched) {
            for (acc in accounts) {
                if (body.contains(acc.lastDigits)) {
                    val keywords = acc.keywords.split(",").map { it.trim().lowercase() }
                    val hasKeyword = keywords.any { keyword -> body.lowercase().contains(keyword) }
                    if (hasKeyword) {
                        matchedName = acc.accountName
                        matchedLastDigits = acc.lastDigits
                        isMatched = true
                        break
                    }
                }
            }
        }

        if (!isMatched) {
            Log.d("SmsReceiver", "SMS did not match any card/account configuration digits & keywords.")
            return
        }

        // 2. Parse Amount
        // Matches e.g., INR 355.00, Rs. 15000.00, INR 1,699.15, $45.50
        val amountRegex = Regex("(?i)(?:INR|Rs\\.?|USD|\\$)\\s*([\\d,]+(?:\\.\\d{2})?)")
        val matchResult = amountRegex.find(body)
        if (matchResult == null) {
            Log.d("SmsReceiver", "Failed to parse amount from matched SMS.")
            return
        }

        val amountStr = matchResult.groupValues[1]
        val amount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) {
            Log.d("SmsReceiver", "Parsed amount is zero or negative.")
            return
        }

        // 3. Parse Merchant
        val merchant = parseMerchant(body, matchedName)

        // 4. Generate unique hash
        val syncHash = generateHash(body, sender, timestamp)

        // 5. Create and save transaction
        val tx = Transaction(
            amount = amount,
            accountName = matchedName,
            lastDigits = matchedLastDigits,
            merchant = merchant,
            timestamp = timestamp,
            smsSender = sender,
            smsBody = body,
            isSynced = 0,
            syncHash = syncHash
        )

        val added = repo.addTransaction(tx)
        if (added) {
            Log.d("SmsReceiver", "Successfully logged parsed transaction: $tx")
            
            val notificationHelper = NotificationHelper(context)
            val currencySymbol = repo.getCurrency()

            // Notify transaction success
            notificationHelper.showTransactionLogged(tx, currencySymbol)

            // Check if monthly budget exceeded
            val currentSpent = helper.getCurrentMonthTotalSpent()
            val limit = helper.getBudgetLimit().toDouble()
            if (currentSpent > limit) {
                notificationHelper.showBudgetWarning(currentSpent, limit, currencySymbol)
            }

            // Trigger silent background spouse sync automatically!
            try {
                val syncManager = BluetoothSyncManager(
                    context,
                    repo,
                    object : BluetoothSyncManager.SyncCallback {
                        override fun onProgress(message: String) {}
                        override fun onSuccess(sent: Int, received: Int) {
                            Log.d("SmsReceiver", "Auto-sync on SMS success! Sent: $sent, Rec: $received")
                        }
                        override fun onError(error: String) {
                            Log.e("SmsReceiver", "Auto-sync on SMS error: $error")
                        }
                        override fun onServerStatusChanged(active: Boolean) {}
                    }
                )

                val spouseAddress = syncManager.getLastSyncedSpouseAddress()
                if (spouseAddress != null && syncManager.isBluetoothEnabled()) {
                    val pairedDevices = syncManager.getPairedDevices()
                    val spouseDevice = pairedDevices.firstOrNull { it.address == spouseAddress }
                    if (spouseDevice != null) {
                        Log.d("SmsReceiver", "Auto-syncing spend with spouse at $spouseAddress")
                        syncManager.syncWithSpouseSilent(spouseDevice)
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Failed to trigger background auto-sync", e)
            }
        } else {
            Log.d("SmsReceiver", "Transaction ignored (already exists/duplicate hash).")
        }
    }

    fun parseMerchant(body: String, fallback: String): String {
        // Try credit card style: "... on IKEA INDIA PVT . Avl Limit..."
        val cardMerchantRegex = Regex("(?i)on\\s+([A-Z0-9\\s\\-&]+?)\\s*\\.\\s*(?:Avl|If|on)")
        val cardMatch = cardMerchantRegex.find(body)
        if (cardMatch != null) {
            val merchant = cardMatch.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // Try UPI style: "UPI/P2M/611054017127/BharatPe Merchant" or "UPI/P2M/611054017127/ANY_MERCHANT"
        val upiMerchantRegex = Regex("(?i)(?:UPI/P2M|UPI/[^/]+)/[^/]+/([A-Z0-9\\s\\-&_]+)")
        val upiMatch = upiMerchantRegex.find(body)
        if (upiMatch != null) {
            val merchant = upiMatch.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // Try "at <Merchant>" style
        val atMerchantRegex = Regex("(?i)at\\s+([A-Z0-9\\s\\-&]+?)(?:\\s+on|\\s+\\.|\\s+using|$)")
        val atMatch = atMerchantRegex.find(body)
        if (atMatch != null) {
            val merchant = atMatch.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // Default fallback if no pattern fits
        return fallback
    }

    fun generateHash(body: String, sender: String, timestamp: Long): String {
        val raw = "$sender:$timestamp:$body"
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(raw.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            raw.hashCode().toString()
        }
    }

    companion object {
        fun scanHistoricalSms(context: Context, monthsBack: Int): Int {
            val repo = DefaultDataRepository.getInstance(context)
            val helper = NovaDatabaseHelper(context)
            
            // Calculate cutoff date in milliseconds
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -monthsBack)
            val cutoffMs = calendar.timeInMillis
            
            val uri = android.net.Uri.parse("content://sms/inbox")
            val projection = arrayOf("address", "body", "date")
            val selection = "date >= ?"
            val selectionArgs = arrayOf(cutoffMs.toString())
            
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(
                uri, projection, selection, selectionArgs, "date DESC"
            ) ?: return 0
            
            var importedCount = 0
            
            if (cursor.moveToFirst()) {
                val addrIdx = cursor.getColumnIndexOrThrow("address")
                val bodyIdx = cursor.getColumnIndexOrThrow("body")
                val dateIdx = cursor.getColumnIndexOrThrow("date")
                
                // Fetch active configurations
                val cards = helper.getAllCardConfigs()
                val accounts = helper.getAllAccountConfigs()
                val amountRegex = Regex("(?i)(?:INR|Rs\\.?|USD|\\$)\\s*([\\d,]+(?:\\.\\d{2})?)")
                val receiverInstance = SmsReceiver()
                
                do {
                    val address = cursor.getString(addrIdx) ?: "Unknown"
                    val body = cursor.getString(bodyIdx) ?: continue
                    val timestamp = cursor.getLong(dateIdx)
                    
                    val paymentKeywords = listOf(
                        "cc payment", "card pymt", "credit card bill", "inf*credit card",
                        "cc pymt", "card payment", "bill pymt", "bill payment", "cc outstanding",
                        "credit card", "credit c", "creditcard", "amazon pay credit"
                    )
                    val isCardPayment = paymentKeywords.any { body.lowercase().contains(it) }
                    if (isCardPayment) {
                        Log.d("SmsReceiver", "CC payment matched during scan. Ignoring to prevent double-counting.")
                        continue
                    }
                    
                    // Match against rules
                    var matchedName = ""
                    var matchedLastDigits = ""
                    var isMatched = false

                    // Check cards
                    for (card in cards) {
                        if (body.contains(card.lastDigits)) {
                            val keywords = card.keywords.split(",").map { it.trim().lowercase() }
                            val hasKeyword = keywords.any { keyword -> body.lowercase().contains(keyword) }
                            if (hasKeyword) {
                                matchedName = card.cardName
                                matchedLastDigits = card.lastDigits
                                isMatched = true
                                break
                            }
                        }
                    }

                    // Check accounts
                    if (!isMatched) {
                        for (acc in accounts) {
                            if (body.contains(acc.lastDigits)) {
                                val keywords = acc.keywords.split(",").map { it.trim().lowercase() }
                                val hasKeyword = keywords.any { keyword -> body.lowercase().contains(keyword) }
                                if (hasKeyword) {
                                    matchedName = acc.accountName
                                    matchedLastDigits = acc.lastDigits
                                    isMatched = true
                                    break
                                }
                            }
                        }
                    }

                    if (!isMatched) continue

                    // Parse Amount
                    val matchResult = amountRegex.find(body) ?: continue
                    val amountStr = matchResult.groupValues[1]
                    val amount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
                    if (amount <= 0.0) continue

                    // Parse Merchant
                    val merchant = receiverInstance.parseMerchant(body, matchedName)

                    // Generate unique hash
                    val syncHash = receiverInstance.generateHash(body, address, timestamp)

                    // Create transaction
                    val tx = Transaction(
                        amount = amount,
                        accountName = matchedName,
                        lastDigits = matchedLastDigits,
                        merchant = merchant,
                        timestamp = timestamp,
                        smsSender = address,
                        smsBody = body,
                        isSynced = 0,
                        syncHash = syncHash
                    )

                    // Add to database (automatically checks uniqueness via SQLite constraint!)
                    val added = repo.addTransaction(tx)
                    if (added) {
                        importedCount++
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
            repo.refresh()
            return importedCount
        }
    }
}
