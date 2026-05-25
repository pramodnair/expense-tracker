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

    override fun refresh() {
        val currentSelected = _selectedMonth.value
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

        val monthsSet = mutableSetOf<String>()
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        monthsSet.add(sdf.format(Date()))
        for (tx in allTx) {
            monthsSet.add(getTransactionBudgetMonth(tx, cards))
        }
        _distinctMonths.value = monthsSet.sortedDescending()
    }

    override fun setSelectedMonth(yearMonth: String) {
        _selectedMonth.value = yearMonth
        refresh()
    }

    override fun addTransaction(tx: Transaction): Boolean {
        val success = dbHelper.addTransaction(tx)
        if (success) {
            refresh()
        }
        return success
    }

    override fun deleteTransaction(id: Long) {
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
}
