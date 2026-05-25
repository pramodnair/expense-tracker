package com.example.novabudget.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Transaction(
    val id: Long = 0,
    val amount: Double,
    val accountName: String,
    val lastDigits: String,
    val merchant: String,
    val timestamp: Long,
    val smsSender: String,
    val smsBody: String,
    val isSynced: Int = 0,
    val syncHash: String
)

@Serializable
data class CardConfig(
    val id: Long = 0,
    @SerialName("name")
    val cardName: String,
    val lastDigits: String,
    val keywords: String,
    val billingCycleDay: Int = 0
)

@Serializable
data class AccountConfig(
    val id: Long = 0,
    @SerialName("name")
    val accountName: String,
    val lastDigits: String,
    val keywords: String
)

@Serializable
data class Subscription(
    val id: Long = 0,
    val name: String,
    val amount: Double,
    val billingCycle: String, // "Monthly" or "Yearly"
    val nextRenewalDate: Long, // timestamp
    val isReminderEnabled: Int = 1,
    val isAutoDetected: Int = 0
)


