package com.example.novabudget.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface DataRepository {
    val transactions: Flow<List<Transaction>>
    val cardConfigs: Flow<List<CardConfig>>
    val accountConfigs: Flow<List<AccountConfig>>
    val currentMonthSpent: Flow<Double>
    val budgetLimit: Flow<Float>
    val currencySymbol: Flow<String>
    
    val monthlyIncome: Flow<Double>
    val incomeDay: Flow<Int>
    val startingBalance: Flow<Double>
    
    // Reactive historical tracking
    val selectedMonth: Flow<String>
    val distinctMonths: Flow<List<String>>

    val subscriptions: Flow<List<Subscription>>
    val suggestedSubscriptions: Flow<List<Subscription>>

    val isSecurityEnabled: Flow<Boolean>
    val masterPasscode: Flow<String>
    val decoyPasscode: Flow<String>
    val decoyStartingBalance: Flow<Double>
    val decoyMonthlyIncome: Flow<Double>
    val decoyBudgetLimit: Flow<Float>
    val isStealthModeActive: Flow<Boolean>

    fun refresh()
    fun setSelectedMonth(yearMonth: String)
    fun addTransaction(tx: Transaction): Boolean
    fun deleteTransaction(id: Long)
    fun clearAllTransactions()
    
    fun getBudgetLimit(): Float
    fun setBudgetLimit(limit: Float)
    fun getCurrency(): String
    fun setCurrency(curr: String)
    fun isSyncServerActive(): Boolean
    fun setSyncServerActive(active: Boolean)
    
    fun getStartingBalance(): Double
    fun setStartingBalance(bal: Double)
    fun getMonthlyIncome(): Double
    fun setMonthlyIncome(income: Double)
    fun getIncomeDay(): Int
    fun setIncomeDay(day: Int)
    
    fun addCardConfig(name: String, lastDigits: String, keywords: String, billingCycleDay: Int = 0): Boolean
    fun deleteCardConfig(id: Long)
    fun updateCardConfig(id: Long, name: String, lastDigits: String, keywords: String, billingCycleDay: Int = 0): Boolean
    
    fun addAccountConfig(name: String, lastDigits: String, keywords: String): Boolean
    fun deleteAccountConfig(id: Long)
    fun updateAccountConfig(id: Long, name: String, lastDigits: String, keywords: String): Boolean

    fun addSubscription(sub: Subscription): Boolean
    fun deleteSubscription(id: Long)
    fun updateSubscription(sub: Subscription): Boolean
    fun detectSubscriptions(): List<Subscription>

    fun isSecurityEnabled(): Boolean
    fun setSecurityEnabled(enabled: Boolean)
    fun getMasterPasscode(): String
    fun setMasterPasscode(code: String)
    fun getDecoyPasscode(): String
    fun setDecoyPasscode(code: String)
    fun getDecoyStartingBalance(): Double
    fun setDecoyStartingBalance(bal: Double)
    fun getDecoyMonthlyIncome(): Double
    fun setDecoyMonthlyIncome(income: Double)
    fun getDecoyBudgetLimit(): Float
    fun setDecoyBudgetLimit(limit: Float)
    fun setStealthModeActive(active: Boolean)
}

class DefaultDataRepository(private val context: Context) : DataRepository {
    private val dbHelper = NovaDatabaseHelper(context)

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    override val transactions: Flow<List<Transaction>> = _transactions.asStateFlow()

    private val _cardConfigs = MutableStateFlow<List<CardConfig>>(emptyList())
    override val cardConfigs: Flow<List<CardConfig>> = _cardConfigs.asStateFlow()

    private val _accountConfigs = MutableStateFlow<List<AccountConfig>>(emptyList())
    override val accountConfigs: Flow<List<AccountConfig>> = _accountConfigs.asStateFlow()

    private val _currentMonthSpent = MutableStateFlow(0.0)
    override val currentMonthSpent: Flow<Double> = _currentMonthSpent.asStateFlow()

    private val _budgetLimit = MutableStateFlow(50000.0f)
    override val budgetLimit: Flow<Float> = _budgetLimit.asStateFlow()

    private val _currencySymbol = MutableStateFlow("INR")
    override val currencySymbol: Flow<String> = _currencySymbol.asStateFlow()

    private val _selectedMonth = MutableStateFlow("")
    override val selectedMonth: Flow<String> = _selectedMonth.asStateFlow()

    private val _distinctMonths = MutableStateFlow<List<String>>(emptyList())
    override val distinctMonths: Flow<List<String>> = _distinctMonths.asStateFlow()

    private val _startingBalance = MutableStateFlow(50000.0)
    override val startingBalance: Flow<Double> = _startingBalance.asStateFlow()

    private val _monthlyIncome = MutableStateFlow(100000.0)
    override val monthlyIncome: Flow<Double> = _monthlyIncome.asStateFlow()

    private val _incomeDay = MutableStateFlow(30)
    override val incomeDay: Flow<Int> = _incomeDay.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    override val subscriptions: Flow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _suggestedSubscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    override val suggestedSubscriptions: Flow<List<Subscription>> = _suggestedSubscriptions.asStateFlow()

    private val _isSecurityEnabled = MutableStateFlow(false)
    override val isSecurityEnabled: Flow<Boolean> = _isSecurityEnabled.asStateFlow()

    private val _masterPasscode = MutableStateFlow("")
    override val masterPasscode: Flow<String> = _masterPasscode.asStateFlow()

    private val _decoyPasscode = MutableStateFlow("")
    override val decoyPasscode: Flow<String> = _decoyPasscode.asStateFlow()

    private val _decoyStartingBalance = MutableStateFlow(75000.0)
    override val decoyStartingBalance: Flow<Double> = _decoyStartingBalance.asStateFlow()

    private val _decoyMonthlyIncome = MutableStateFlow(90000.0)
    override val decoyMonthlyIncome: Flow<Double> = _decoyMonthlyIncome.asStateFlow()

    private val _decoyBudgetLimit = MutableStateFlow(25000.0f)
    override val decoyBudgetLimit: Flow<Float> = _decoyBudgetLimit.asStateFlow()

    private val _isStealthModeActive = MutableStateFlow(false)
    override val isStealthModeActive: Flow<Boolean> = _isStealthModeActive.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: DefaultDataRepository? = null

        fun getInstance(context: Context): DefaultDataRepository {
            return INSTANCE ?: synchronized(this) {
                val active = INSTANCE ?: DefaultDataRepository(context.applicationContext)
                INSTANCE = active
                active
            }
        }
    }

    init {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        _selectedMonth.value = sdf.format(Date())
        refresh()
    }

    private fun getTransactionBudgetMonth(tx: Transaction, cardConfigs: List<CardConfig>): String {
        val matchingCard = cardConfigs.firstOrNull { it.lastDigits == tx.lastDigits || it.cardName == tx.accountName }
        val cutoffDay = matchingCard?.billingCycleDay ?: 0
        
        if (cutoffDay > 0) {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = tx.timestamp
            val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
            
            if (day > cutoffDay) {
                cal.add(java.util.Calendar.MONTH, 1) // Shift to next month's salary cycle!
            }
            
            val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
            return sdf.format(cal.time)
        }
        
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        return sdf.format(Date(tx.timestamp))
    }

    private val stealthManualTransactions = mutableListOf<Transaction>()

    private fun generateDecoyTransactions(selectedMonth: String): List<Transaction> {
        val list = mutableListOf<Transaction>()
        
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val targetMonthDate = try {
            sdf.parse(selectedMonth) ?: Date()
        } catch (e: Exception) {
            Date()
        }
        
        val cal = java.util.Calendar.getInstance()
        cal.time = targetMonthDate
        
        // Mock 1: Swiggy
        cal.set(java.util.Calendar.DAY_OF_MONTH, 5)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 13)
        cal.set(java.util.Calendar.MINUTE, 15)
        list.add(
            Transaction(
                id = 9001,
                amount = 250.0,
                accountName = "Savings A/c",
                lastDigits = "9999",
                merchant = "Swiggy",
                timestamp = cal.timeInMillis,
                smsSender = "AXISBANK",
                smsBody = "INR 250.00 debited from XX9999 on Swiggy",
                isSynced = 1,
                syncHash = "stealth_hash_1"
            )
        )
        
        // Mock 2: Uber
        cal.set(java.util.Calendar.DAY_OF_MONTH, 12)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 18)
        cal.set(java.util.Calendar.MINUTE, 45)
        list.add(
            Transaction(
                id = 9002,
                amount = 380.0,
                accountName = "Savings A/c",
                lastDigits = "9999",
                merchant = "Uber",
                timestamp = cal.timeInMillis,
                smsSender = "AXISBANK",
                smsBody = "INR 380.00 debited from XX9999 on Uber",
                isSynced = 1,
                syncHash = "stealth_hash_2"
            )
        )
        
        // Mock 3: Netflix
        cal.set(java.util.Calendar.DAY_OF_MONTH, 15)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 9)
        cal.set(java.util.Calendar.MINUTE, 0)
        list.add(
            Transaction(
                id = 9003,
                amount = 199.0,
                accountName = "Classic Credit Card",
                lastDigits = "8888",
                merchant = "Netflix",
                timestamp = cal.timeInMillis,
                smsSender = "ICICIBANK",
                smsBody = "INR 199.00 spent on XX8888 on Netflix",
                isSynced = 1,
                syncHash = "stealth_hash_3"
            )
        )

        // Mock 4: Reliance Digital
        cal.set(java.util.Calendar.DAY_OF_MONTH, 20)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 15)
        cal.set(java.util.Calendar.MINUTE, 30)
        list.add(
            Transaction(
                id = 9004,
                amount = 2499.0,
                accountName = "Classic Credit Card",
                lastDigits = "8888",
                merchant = "Reliance Digital",
                timestamp = cal.timeInMillis,
                smsSender = "ICICIBANK",
                smsBody = "INR 2499.00 spent on XX8888 on Reliance Digital",
                isSynced = 1,
                syncHash = "stealth_hash_4"
            )
        )
        
        // Filter in-memory manual spends during stealth mode session
        val filteredManual = stealthManualTransactions.filter { 
            sdf.format(Date(it.timestamp)) == selectedMonth
        }
        list.addAll(filteredManual)
        
        return list.sortedByDescending { it.timestamp }
    }

    override fun refresh() {
        // Load active security states
        _isSecurityEnabled.value = dbHelper.isSecurityEnabled()
        _masterPasscode.value = dbHelper.getMasterPasscode()
        _decoyPasscode.value = dbHelper.getDecoyPasscode()
        _decoyStartingBalance.value = dbHelper.getDecoyStartingBalance()
        _decoyMonthlyIncome.value = dbHelper.getDecoyMonthlyIncome()
        _decoyBudgetLimit.value = dbHelper.getDecoyBudgetLimit()

        val currentSelected = _selectedMonth.value
        val isStealth = _isStealthModeActive.value

        if (isStealth) {
            _cardConfigs.value = listOf(
                CardConfig(1, "Classic Credit Card", "8888", "spent,charged", 0)
            )
            _accountConfigs.value = listOf(
                AccountConfig(1, "Savings A/c", "9999", "debited")
            )
            val fakeTx = generateDecoyTransactions(currentSelected)
            _transactions.value = fakeTx
            _currentMonthSpent.value = fakeTx.sumOf { it.amount }
            
            _budgetLimit.value = dbHelper.getDecoyBudgetLimit()
            _currencySymbol.value = dbHelper.getCurrency()
            _startingBalance.value = dbHelper.getDecoyStartingBalance()
            _monthlyIncome.value = dbHelper.getDecoyMonthlyIncome()
            _incomeDay.value = dbHelper.getIncomeDay()

            _subscriptions.value = listOf(
                Subscription(1, "Netflix", 199.0, "Monthly", System.currentTimeMillis() + 5 * 24 * 3600 * 1000L, 1, 0)
            )
            _suggestedSubscriptions.value = emptyList()

            val monthsSet = mutableSetOf<String>()
            val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
            monthsSet.add(sdf.format(Date()))
            _distinctMonths.value = monthsSet.sortedDescending()
        } else {
            val allTx = dbHelper.getAllTransactions()
            val cards = dbHelper.getAllCardConfigs()
            
            _cardConfigs.value = cards
            _accountConfigs.value = dbHelper.getAllAccountConfigs()
            _transactions.value = allTx.filter { getTransactionBudgetMonth(it, cards) == currentSelected }
            _currentMonthSpent.value = _transactions.value.sumOf { it.amount }
            _budgetLimit.value = dbHelper.getBudgetLimit()
            _currencySymbol.value = dbHelper.getCurrency()
            _startingBalance.value = dbHelper.getStartingBalance()
            _monthlyIncome.value = dbHelper.getMonthlyIncome()
            _incomeDay.value = dbHelper.getIncomeDay()
            _subscriptions.value = dbHelper.getAllSubscriptions()
            _suggestedSubscriptions.value = dbHelper.detectSubscriptions()

            val monthsSet = mutableSetOf<String>()
            val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
            monthsSet.add(sdf.format(Date()))
            for (tx in allTx) {
                monthsSet.add(getTransactionBudgetMonth(tx, cards))
            }
            _distinctMonths.value = monthsSet.sortedDescending()
        }
    }

    override fun setSelectedMonth(yearMonth: String) {
        _selectedMonth.value = yearMonth
        refresh()
    }

    override fun addTransaction(tx: Transaction): Boolean {
        if (_isStealthModeActive.value) {
            val fakeTx = tx.copy(id = (10000 + stealthManualTransactions.size).toLong(), isSynced = 1)
            stealthManualTransactions.add(fakeTx)
            refresh()
            return true
        }
        val success = dbHelper.addTransaction(tx)
        if (success) {
            refresh()
        }
        return success
    }

    override fun deleteTransaction(id: Long) {
        if (_isStealthModeActive.value) {
            stealthManualTransactions.removeAll { it.id == id }
            refresh()
            return
        }
        dbHelper.deleteTransaction(id)
        refresh()
    }

    override fun clearAllTransactions() {
        dbHelper.clearAllTransactions()
        refresh()
    }

    override fun getBudgetLimit(): Float = dbHelper.getBudgetLimit()

    override fun setBudgetLimit(limit: Float) {
        dbHelper.setBudgetLimit(limit)
        refresh()
    }

    override fun getCurrency(): String = dbHelper.getCurrency()

    override fun setCurrency(curr: String) {
        dbHelper.setCurrency(curr)
        refresh()
    }

    override fun isSyncServerActive(): Boolean = dbHelper.isSyncServerActive()

    override fun setSyncServerActive(active: Boolean) {
        dbHelper.setSyncServerActive(active)
    }

    override fun getStartingBalance(): Double = dbHelper.getStartingBalance()

    override fun setStartingBalance(bal: Double) {
        dbHelper.setStartingBalance(bal)
        refresh()
    }

    override fun getMonthlyIncome(): Double = dbHelper.getMonthlyIncome()

    override fun setMonthlyIncome(income: Double) {
        dbHelper.setMonthlyIncome(income)
        refresh()
    }

    override fun getIncomeDay(): Int = dbHelper.getIncomeDay()

    override fun setIncomeDay(day: Int) {
        dbHelper.setIncomeDay(day)
        refresh()
    }

    override fun addCardConfig(name: String, lastDigits: String, keywords: String, billingCycleDay: Int): Boolean {
        val success = dbHelper.addCardConfig(name, lastDigits, keywords, billingCycleDay)
        if (success) {
            refresh()
        }
        return success
    }

    override fun deleteCardConfig(id: Long) {
        dbHelper.deleteCardConfig(id)
        refresh()
    }

    override fun updateCardConfig(id: Long, name: String, lastDigits: String, keywords: String, billingCycleDay: Int): Boolean {
        val success = dbHelper.updateCardConfig(id, name, lastDigits, keywords, billingCycleDay)
        if (success) {
            refresh()
        }
        return success
    }

    override fun addAccountConfig(name: String, lastDigits: String, keywords: String): Boolean {
        val success = dbHelper.addAccountConfig(name, lastDigits, keywords)
        if (success) {
            refresh()
        }
        return success
    }

    override fun deleteAccountConfig(id: Long) {
        dbHelper.deleteAccountConfig(id)
        refresh()
    }

    override fun updateAccountConfig(id: Long, name: String, lastDigits: String, keywords: String): Boolean {
        val success = dbHelper.updateAccountConfig(id, name, lastDigits, keywords)
        if (success) {
            refresh()
        }
        return success
    }

    override fun addSubscription(sub: Subscription): Boolean {
        val success = dbHelper.addSubscription(sub)
        if (success) {
            val allSubs = dbHelper.getAllSubscriptions()
            val insertedSub = allSubs.firstOrNull { it.name == sub.name && it.amount == sub.amount }
            if (insertedSub != null) {
                SubscriptionAlarmScheduler(context).scheduleAlarm(insertedSub)
            }
            refresh()
        }
        return success
    }

    override fun deleteSubscription(id: Long) {
        val sub = dbHelper.getAllSubscriptions().firstOrNull { it.id == id }
        if (sub != null) {
            SubscriptionAlarmScheduler(context).cancelAlarm(sub)
        }
        dbHelper.deleteSubscription(id)
        refresh()
    }

    override fun updateSubscription(sub: Subscription): Boolean {
        val success = dbHelper.updateSubscription(sub)
        if (success) {
            val scheduler = SubscriptionAlarmScheduler(context)
            if (sub.isReminderEnabled == 1) {
                scheduler.scheduleAlarm(sub)
            } else {
                scheduler.cancelAlarm(sub)
            }
            refresh()
        }
        return success
    }

    override fun detectSubscriptions(): List<Subscription> {
        return dbHelper.detectSubscriptions()
    }

    override fun isSecurityEnabled(): Boolean = dbHelper.isSecurityEnabled()

    override fun setSecurityEnabled(enabled: Boolean) {
        dbHelper.setSecurityEnabled(enabled)
        refresh()
    }

    override fun getMasterPasscode(): String = dbHelper.getMasterPasscode()

    override fun setMasterPasscode(code: String) {
        dbHelper.setMasterPasscode(code)
        refresh()
    }

    override fun getDecoyPasscode(): String = dbHelper.getDecoyPasscode()

    override fun setDecoyPasscode(code: String) {
        dbHelper.setDecoyPasscode(code)
        refresh()
    }

    override fun getDecoyStartingBalance(): Double = dbHelper.getDecoyStartingBalance()

    override fun setDecoyStartingBalance(bal: Double) {
        dbHelper.setDecoyStartingBalance(bal)
        refresh()
    }

    override fun getDecoyMonthlyIncome(): Double = dbHelper.getDecoyMonthlyIncome()

    override fun setDecoyMonthlyIncome(income: Double) {
        dbHelper.setDecoyMonthlyIncome(income)
        refresh()
    }

    override fun getDecoyBudgetLimit(): Float = dbHelper.getDecoyBudgetLimit()

    override fun setDecoyBudgetLimit(limit: Float) {
        dbHelper.setDecoyBudgetLimit(limit)
        refresh()
    }

    override fun setStealthModeActive(active: Boolean) {
        _isStealthModeActive.value = active
        if (!active) {
            // Clear manual spends added during stealth mode when exiting
            stealthManualTransactions.clear()
        }
        refresh()
    }
}
